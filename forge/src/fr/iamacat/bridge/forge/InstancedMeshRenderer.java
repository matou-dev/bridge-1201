package fr.iamacat.bridge.forge;

import fr.iamacat.bridge.ForgeSnapshot;
import fr.iamacat.bridge.model.BeastAnimation;
import fr.iamacat.bridge.model.BeastModel;
import fr.iamacat.bridge.model.BeastTexture;
import fr.iamacat.bridge.render.RenderJob;
import fr.iamacat.bridge.render.RenderSeal;
import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.render.GlBackend;
import fr.iamacat.spi.render.InstanceFormat;
import fr.iamacat.spi.render.InstanceBucket.Rec;
import fr.iamacat.spi.render.ViewProjection;
import fr.iamacat.spi.model.MatouAnimation;
import fr.iamacat.spi.model.MatouModel;
import fr.iamacat.spi.model.Molang;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.opengl.GL11C;

/**
 * Client-only instanced mesh renderer hooked into RenderLevelStageEvent.
 * Renders all visible MatouEntity instances via OpenGL 3.1+ instancing primitives (Lwjgl3Backend).
 * The static mesh is the SPI skinned bake of the shipped
 * {@code my_beast.geo.json} (hub decisions/MATOU_ANIMATION.md) — bind
 * positions plus the file-order bone index per vertex, never a hardcoded
 * box again. Per-instance bone deltas ride a 4xN RGBA32F bone texture
 * on unit 1 (GPU skinning over the static VBO — the CPU oracle never
 * uploads, one walk phase per mob, never a shared uniform).
 *
 * <p>1.20.1 shapes (all measured on the pinned 47.2.0 bytes, never
 * recalled): the frame event is {@code RenderLevelStageEvent} (the
 * 1.16.5 {@code RenderWorldLastEvent} does not exist here) gated on
 * {@code Stage.AFTER_ENTITIES}, partial ticks ride
 * {@code getPartialTick} (no trailing s), the view entity comes from
 * {@code Minecraft.getCameraEntity} (Entity-typed — no Camera reads),
 * iteration rides the already-mapped
 * {@code EntityGetter.getEntitiesOfClass} over a camera-centred box
 * (no ClientLevel member is ever named, so no client-level row joins
 * the narrow map), interpolation rides {@code xo + (getX - xo) * pt}
 * with the {@code yRotO/xRotO} olds (the 1.12 {@code prevPos} shape
 * does not port), and both shader matrices ride the event directly —
 * view from {@code getPoseStack().last().pose()}, projection from
 * {@code getProjectionMatrix} (both org.joml, uploaded via
 * {@code Matrix4f.get} — the 1.16.5 Mojang-math {@code write} shape
 * does not port to JOML).
 */
@OnlyIn(Dist.CLIENT)
public final class InstancedMeshRenderer {
    private static final InstancedMeshRenderer INSTANCE = new InstancedMeshRenderer();

    /**
     * Skinned stride (SPI {@code bakeSkinnedMesh} layout: pos3, uv2,
     * normal3, bone1 — bind positions, bone1 is the file-order bone
     * index as float). The stride-8 {@code bakeMesh} stays the gate
     * oracle layout, never the upload.
     */
    private static final int SKINNED_STRIDE = 9;

    /**
     * Bone ceiling for the per-instance path — frozen on the holder
     * ({@code BeastAnimation.MAX_BONES}, generic-palette tranche, hub
     * decisions/MATOU_ANIMATION.md): 1..MAX_BONES bones ride the bone
     * texture (4 RGBA32F texels per bone, fetched by file-order
     * index), so the 16-attribute guarantee holds for any admitted
     * beast — only static (4) + instance (4) slots ride attributes
     * now. Past the ceiling refuses loudly at {@code initGl}, never a
     * silent clamp.
     */
    private static final int MAX_BONES = BeastAnimation.MAX_BONES;

    private static final String VERTEX_SHADER =
            "#version 330 core\n"
            + "layout(location = 0) in vec3 a_pos;\n"
            + "layout(location = 1) in vec2 a_uv;\n"
            + "layout(location = 2) in vec3 a_normal;\n"
            + "layout(location = 3) in float a_bone;\n"
            + "layout(location = 4) in vec3 i_pos;\n"
            + "layout(location = 5) in vec3 i_rot_scale;\n"
            + "layout(location = 6) in vec4 i_color;\n"
            + "layout(location = 7) in vec2 i_light;\n"
            + "uniform mat4 u_projection;\n"
            + "uniform mat4 u_view;\n"
            + "uniform sampler2D u_bones;\n"
            + "out vec4 v_color;\n"
            + "out vec3 v_normal;\n"
            + "out vec2 v_uv;\n"
            + "void main() {\n"
            + "    int bi = int(a_bone + 0.5);\n"
            + "    mat4 skin = mat4(texelFetch(u_bones, ivec2(0, bi), 0), texelFetch(u_bones, ivec2(1, bi), 0), texelFetch(u_bones, ivec2(2, bi), 0), texelFetch(u_bones, ivec2(3, bi), 0));\n"
            + "    vec4 posed = skin * vec4(a_pos, 1.0);\n"
            + "    float yaw = i_rot_scale.x;\n"
            + "    float scale = i_rot_scale.z;\n"
            + "    float cy = cos(yaw);\n"
            + "    float sy = sin(yaw);\n"
            + "    mat3 rotY = mat3(cy, 0.0, sy, 0.0, 1.0, 0.0, -sy, 0.0, cy);\n"
            + "    vec3 localPos = rotY * (posed.xyz * scale);\n"
            + "    vec3 worldRelPos = localPos + i_pos;\n"
            + "    gl_Position = u_projection * u_view * vec4(worldRelPos, 1.0);\n"
            + "    v_color = i_color;\n"
            + "    v_normal = normalize(rotY * (mat3(skin) * a_normal));\n"
            + "    v_uv = a_uv;\n"
            + "}\n";

    private static final String FRAGMENT_SHADER =
            "#version 330 core\n"
            + "in vec4 v_color;\n"
            + "in vec3 v_normal;\n"
            + "in vec2 v_uv;\n"
            + "uniform sampler2D u_tex;\n"
            + "out vec4 fragColor;\n"
            + "void main() {\n"
            + "    vec3 lightDir = normalize(vec3(0.2, 1.0, -0.7));\n"
            + "    float diff = max(dot(v_normal, lightDir), 0.0) * 0.4 + 0.6;\n"
            + "    vec4 col = texture(u_tex, v_uv) * v_color;\n"
            + "    col.rgb *= diff;\n"
            + "    fragColor = col;\n"
            + "}\n";

    private final GlBackend backend;
    private boolean initialized;
    private boolean drawLogged;
    private int skippedNan;
    private int program;
    private int vao;
    private int meshVbo;
    private int instanceVbo;
    private int vertexCount;
    private int uProjLoc;
    private int uViewLoc;
    private int uTexLoc;
    private int uBonesLoc;
    private int texture;
    private int boneTexture;
    private final FloatBuffer viewMatrixBuffer;
    private final FloatBuffer projMatrixBuffer;
    private FloatBuffer instanceBuffer;
    /**
     * Staging for the bone-texture upload (N bones x 16 column floats
     * per beast — {@code BeastAnimation.packPaletteInto}, transpose
     * once at pack). Byte-backed: the texture upload reads bytes while
     * the pack writes floats through the same storage.
     */
    private ByteBuffer boneBytes;
    private FloatBuffer boneBuffer;
    /** Shared-texture bucket key: the V2 shader samples the beast texture
     * and multiplies the tint (hub decisions/MATOU_MODEL.md) — one mesh
     * and one texture today, per-mob textures plug their own keys here
     * (named follow-up, never a silent second texture). */
    private static final String TEXTURE_TINT = "tint";
    private static final RenderJob RENDER_JOB = new RenderJob();
    /** Client frame sequence carried by the render snapshot (the job
     * ignores the tick — no addressed randomness on this path — but a
     * snapshot refuses a negative one, so the frames number it). */
    private long frame;

    private InstancedMeshRenderer() {
        this.backend = new Lwjgl3Backend();
        this.viewMatrixBuffer = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        this.projMatrixBuffer = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        this.instanceBuffer = ByteBuffer.allocateDirect(512 * InstanceFormat.STRIDE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
        this.boneBytes = ByteBuffer.allocateDirect(512 * MAX_BONES * 16 * 4).order(ByteOrder.nativeOrder());
        this.boneBuffer = this.boneBytes.asFloatBuffer();
    }

    public static InstancedMeshRenderer getInstance() {
        return INSTANCE;
    }

    public static void initClient() {
        MinecraftForge.EVENT_BUS.register(INSTANCE);
    }

    private void initGl() {
        int vs = backend.createShader(GlBackend.GL_VERTEX_SHADER);
        backend.shaderSource(vs, VERTEX_SHADER);
        backend.compileShader(vs);
        if (!backend.getShaderCompileStatus(vs)) {
            throw new IllegalStateException("E_GL_SHADER:compile " + backend.getShaderInfoLog(vs));
        }

        int fs = backend.createShader(GlBackend.GL_FRAGMENT_SHADER);
        backend.shaderSource(fs, FRAGMENT_SHADER);
        backend.compileShader(fs);
        if (!backend.getShaderCompileStatus(fs)) {
            throw new IllegalStateException("E_GL_SHADER:compile " + backend.getShaderInfoLog(fs));
        }

        program = backend.createProgram();
        backend.attachShader(program, vs);
        backend.attachShader(program, fs);
        backend.linkProgram(program);
        if (!backend.getProgramLinkStatus(program)) {
            throw new IllegalStateException("E_GL_PROGRAM:link " + backend.getProgramInfoLog(program));
        }

        uProjLoc = backend.getUniformLocation(program, "u_projection");
        uViewLoc = backend.getUniformLocation(program, "u_view");
        uTexLoc = backend.getUniformLocation(program, "u_tex");
        uBonesLoc = backend.getUniformLocation(program, "u_bones");

        vao = backend.genVertexArrays();
        backend.bindVertexArray(vao);

        meshVbo = backend.genBuffers();
        backend.bindBuffer(GlBackend.GL_ARRAY_BUFFER, meshVbo);
        // Animation tranche: the static mesh is the SPI skinned bake of
        // the shipped beast geometry (bind positions + file-order bone
        // index), never a rebaked pose. A missing or broken model, a
        // missing animation file, or a beast past the palette ceiling
        // refuses here, loudly, before the first frame.
        MatouModel beastModel = BeastModel.cached().model();
        BeastAnimation.paletteBonesOrThrow(beastModel.bones.size());
        BeastAnimation.cached();
        float[] mesh = beastModel.bakeSkinnedMesh();
        vertexCount = mesh.length / SKINNED_STRIDE;
        FloatBuffer meshData = ByteBuffer.allocateDirect(mesh.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        meshData.put(mesh);
        meshData.flip();
        backend.bufferData(GlBackend.GL_ARRAY_BUFFER, meshData, GlBackend.GL_STATIC_DRAW);

        int meshStride = SKINNED_STRIDE * 4;
        backend.enableVertexAttribArray(0);
        backend.vertexAttribPointer(0, 3, GlBackend.GL_FLOAT, false, meshStride, 0);
        backend.enableVertexAttribArray(1);
        backend.vertexAttribPointer(1, 2, GlBackend.GL_FLOAT, false, meshStride, 3 * 4);
        backend.enableVertexAttribArray(2);
        backend.vertexAttribPointer(2, 3, GlBackend.GL_FLOAT, false, meshStride, 5 * 4);
        backend.enableVertexAttribArray(3);
        backend.vertexAttribPointer(3, 1, GlBackend.GL_FLOAT, false, meshStride, 8 * 4);

        instanceVbo = backend.genBuffers();
        backend.bindBuffer(GlBackend.GL_ARRAY_BUFFER, instanceVbo);

        int instStride = InstanceFormat.STRIDE_BYTES;
        backend.enableVertexAttribArray(4);
        backend.vertexAttribPointer(4, 3, GlBackend.GL_FLOAT, false, instStride, 0);
        backend.vertexAttribDivisor(4, 1);

        backend.enableVertexAttribArray(5);
        backend.vertexAttribPointer(5, 3, GlBackend.GL_FLOAT, false, instStride, 12);
        backend.vertexAttribDivisor(5, 1);

        backend.enableVertexAttribArray(6);
        backend.vertexAttribPointer(6, 4, GlBackend.GL_FLOAT, false, instStride, 24);
        backend.vertexAttribDivisor(6, 1);

        backend.enableVertexAttribArray(7);
        backend.vertexAttribPointer(7, 2, GlBackend.GL_FLOAT, false, instStride, 40);
        backend.vertexAttribDivisor(7, 1);

        // Generic-palette tranche (hub decisions/MATOU_ANIMATION.md):
        // bone deltas ride a 4xN RGBA32F texture on unit 1 (NEAREST +
        // CLAMP_TO_EDGE — NPOT-safe; texelFetch reads exact texels, no
        // filtering, no mipmaps). Storage lands per bucket below
        // (STREAM shape, like the VBO uploads) — unit 0 keeps the
        // beast albedo throughout.
        boneTexture = backend.genTextures();
        backend.activeTexture(GlBackend.GL_TEXTURE1);
        backend.bindTexture(GlBackend.GL_TEXTURE_2D, boneTexture);
        backend.texParameteri(GlBackend.GL_TEXTURE_2D,
                GlBackend.GL_TEXTURE_MIN_FILTER, GlBackend.GL_NEAREST);
        backend.texParameteri(GlBackend.GL_TEXTURE_2D,
                GlBackend.GL_TEXTURE_MAG_FILTER, GlBackend.GL_NEAREST);
        backend.texParameteri(GlBackend.GL_TEXTURE_2D,
                GlBackend.GL_TEXTURE_WRAP_S, GlBackend.GL_CLAMP_TO_EDGE);
        backend.texParameteri(GlBackend.GL_TEXTURE_2D,
                GlBackend.GL_TEXTURE_WRAP_T, GlBackend.GL_CLAMP_TO_EDGE);
        backend.activeTexture(GlBackend.GL_TEXTURE0);

        // Texture tranche (hub decisions/MATOU_MODEL.md, V2): the beast
        // texture uploads once, NEAREST + CLAMP_TO_EDGE (MC pixels, no
        // bleed, no mipmaps — NPOT-safe), on unit 0. A missing or broken
        // texture refuses here, loudly, before the first frame — an
        // untextured tint fallback would be a silent pass.
        BeastTexture beastTex = BeastTexture.cached();
        texture = backend.genTextures();
        backend.bindTexture(GlBackend.GL_TEXTURE_2D, texture);
        backend.texImage2D(GlBackend.GL_TEXTURE_2D, 0, GlBackend.GL_RGBA,
                beastTex.width(), beastTex.height(), 0,
                GlBackend.GL_RGBA, GlBackend.GL_UNSIGNED_BYTE,
                beastTex.uploadBuffer());
        backend.texParameteri(GlBackend.GL_TEXTURE_2D,
                GlBackend.GL_TEXTURE_MIN_FILTER, GlBackend.GL_NEAREST);
        backend.texParameteri(GlBackend.GL_TEXTURE_2D,
                GlBackend.GL_TEXTURE_MAG_FILTER, GlBackend.GL_NEAREST);
        backend.texParameteri(GlBackend.GL_TEXTURE_2D,
                GlBackend.GL_TEXTURE_WRAP_S, GlBackend.GL_CLAMP_TO_EDGE);
        backend.texParameteri(GlBackend.GL_TEXTURE_2D,
                GlBackend.GL_TEXTURE_WRAP_T, GlBackend.GL_CLAMP_TO_EDGE);

        backend.bindVertexArray(0);
        backend.bindBuffer(GlBackend.GL_ARRAY_BUFFER, 0);
        initialized = true;
        System.out.println("[MatouRenderer] ready mesh=" + vertexCount
                + " verts stride=" + SKINNED_STRIDE
                + " texture=" + beastTex.width() + "x" + beastTex.height()
                + " program=" + program);
    }

    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        render(event);
    }

    public void render(RenderLevelStageEvent event) {
        float partialTicks = event.getPartialTick();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return;
        }
        Entity view = mc.getCameraEntity();
        if (view == null) {
            return;
        }

        // Owner discipline (hub decisions/LOOT.md): iteration rides the
        // already-mapped EntityGetter.getEntitiesOfClass (the same row
        // the server reconcile polls — no ClientLevel member is named,
        // so the narrow map gains no client-level row for this). The
        // box is camera-centred (64 blocks — past it the instances are
        // fog-culled by vanilla anyway).
        double eyeX = view.xo + (view.getX() - view.xo) * partialTicks;
        double eyeY = view.yo + (view.getY() - view.yo) * partialTicks;
        double eyeZ = view.zo + (view.getZ() - view.zo) * partialTicks;
        EntityGetter getter = (EntityGetter) mc.level;
        java.util.List<MatouEntity> found = getter.getEntitiesOfClass(
                MatouEntity.class,
                new AABB(eyeX - 64.0, eyeY - 64.0, eyeZ - 64.0,
                        eyeX + 64.0, eyeY + 64.0, eyeZ + 64.0));

        if (!initialized) {
            initGl();
        }

        // Render-plan tranche (hub decisions/GPU_INSTANCING.md): every
        // drawn beast rides a sealed instance record through the pure
        // plan — frustum-culled records never reach the upload, never
        // silently. Model keys are mob-addressed (one bucket per mob
        // today over the single shared mesh — per-mob meshes plug into
        // the same keys), radii bound the sealed hitboxes (the cull
        // never clips a limb the hit-tester still serves).
        List<Rec> recs = new ArrayList<Rec>();
        List<MatouEntity> beasts = new ArrayList<MatouEntity>();
        for (MatouEntity beast : found) {
            // Owner discipline (hub decisions/LOOT.md): inherited
            // vanilla members go through the declaring Entity type,
            // never through the beast — Reobf maps the exact bytecode
            // owner, so a MatouEntity-owned ref passes through silently
            // and dies linking live (caught offline at E0 by reobfing
            // the staged jar and grepping the pool, never recalled).
            Entity e = beast;
            if (e != null && e.isAlive()) {
                double entX = e.xo + (e.getX() - e.xo) * partialTicks;
                double entY = e.yo + (e.getY() - e.yo) * partialTicks;
                double entZ = e.zo + (e.getZ() - e.zo) * partialTicks;
                float radius = RenderSeal.boundRadius(beast.hitBoxes(),
                        e.getX(), e.getY(), e.getZ());
                recs.add(new Rec(beast.mobOrFirst(), TEXTURE_TINT,
                        entX, entY, entZ, radius, e.getYRot()));
                beasts.add(beast);
            }
        }

        if (recs.isEmpty()) {
            return;
        }

        // View/projection ride the event (the exact matrices vanilla
        // renders the world with this frame). Both are org.joml, whose
        // get(FloatBuffer) is an absolute 16-float write at the current
        // position that never moves it (probed on the provisioned joml
        // 1.10.5 bytes — clear() then get() leaves pos=0 lim=16, so no
        // flip(): flip() would set limit=0 and upload nothing).
        viewMatrixBuffer.clear();
        event.getPoseStack().last().pose().get(viewMatrixBuffer);
        projMatrixBuffer.clear();
        event.getProjectionMatrix().get(projMatrixBuffer);
        // Same GL era as the derived path (hub
        // decisions/GPU_INSTANCING.md): driver matrices arrive
        // column-major, transposed and multiplied once, purely, into
        // the row-major product the plan consumes.
        float[] vp = ViewProjection.vpRowMajor(colMajor(viewMatrixBuffer),
                colMajor(projMatrixBuffer));
        // Loading-transient guard (measured live 2026-09-12: during the
        // client-world join AFTER_ENTITIES fires with a non-finite
        // projection from the event — vanilla draws garbage those frames
        // too; same signature as the 1165 join transient). A
        // non-projection cannot be culled against, so the frame is
        // skipped BEFORE the seal (never planned, never uploaded) —
        // loudly on first sight and every 600th, and refusing with the
        // seal's own code past 3600 consecutive bad frames (a full
        // minute: no join lasts that long, so persistence is a wiring
        // bug, never a transient).
        if (!finite16(vp)) {
            skippedNan++;
            if (skippedNan == 1 || skippedNan % 600 == 0) {
                System.out.println("[MatouRenderer] skipped non-finite"
                        + " view-projection (run " + skippedNan
                        + " consecutive frames — join transient,"
                        + " draw skipped, never planned)");
            }
            if (skippedNan > 3600) {
                throw new IllegalStateException(
                        "E_RENDER_FRUSTUM:degenerate <projection non-finite "
                        + skippedNan + " consecutive frames>"
                        + " (not a projection)");
            }
            return;
        }
        skippedNan = 0;
        Map<MatouId, Object> states = RenderSeal.seal(
                RenderJob.vocabulary(),
                new double[] {eyeX, eyeY, eyeZ}, vp, recs);
        Map<String, List<Integer>> buckets = RENDER_JOB.decide(
                ForgeSnapshot.snapshot(frame++, states));

        if (buckets.isEmpty()) {
            return;
        }
        int total = 0;
        for (List<Integer> bucket : buckets.values()) {
            total += bucket.size();
        }

        GL11C.glEnable(GL11C.GL_DEPTH_TEST);
        GL11C.glDepthMask(true);
        GL11C.glEnable(GL11C.GL_CULL_FACE);
        GL11C.glCullFace(GL11C.GL_BACK);

        backend.useProgram(program);
        backend.uniformMatrix4fv(uProjLoc, false, projMatrixBuffer);
        backend.uniformMatrix4fv(uViewLoc, false, viewMatrixBuffer);
        backend.uniform1i(uTexLoc, 0);
        backend.uniform1i(uBonesLoc, 1);

        backend.bindVertexArray(vao);
        // One instanced draw per planned bucket (the GPU execution
        // order InstanceBucket.plan proves): both buffers are repacked
        // per bucket, so a culled bucket costs nothing and a visible one
        // binds once. The texture binds per bucket beside the repack —
        // one shared texture today, per-mob textures plug the same call.
        // Bone deltas ride the palette texture per bucket (GPU skinning
        // over the static VBO — one walk phase per mob, never a shared
        // uniform).
        MatouModel skinModel = BeastModel.cached().model();
        int bones = skinModel.bones.size();
        for (Map.Entry<String, List<Integer>> bucket
                : buckets.entrySet()) {
            instanceBuffer.clear();
            boneBuffer.clear();
            for (Integer index : bucket.getValue()) {
                MatouEntity beast = beasts.get(index.intValue());
                Entity e = beast;
                if (instanceBuffer.remaining() < InstanceFormat.FLOATS_PER_INSTANCE) {
                    FloatBuffer expanded = ByteBuffer.allocateDirect(instanceBuffer.capacity() * 2 * 4)
                            .order(ByteOrder.nativeOrder()).asFloatBuffer();
                    instanceBuffer.flip();
                    expanded.put(instanceBuffer);
                    instanceBuffer = expanded;
                }
                if (boneBuffer.remaining() < bones * 16) {
                    int pos = boneBuffer.position();
                    ByteBuffer grown = ByteBuffer.allocateDirect(
                            boneBytes.capacity() * 2).order(ByteOrder.nativeOrder());
                    boneBytes.position(0);
                    boneBytes.limit(pos * 4);
                    grown.put(boneBytes);
                    boneBytes = grown;
                    boneBuffer = boneBytes.asFloatBuffer();
                    boneBuffer.position(pos);
                }
                double entX = e.xo + (e.getX() - e.xo) * partialTicks;
                double entY = e.yo + (e.getY() - e.yo) * partialTicks;
                double entZ = e.zo + (e.getZ() - e.zo) * partialTicks;

                float yaw = (float) Math.toRadians(-e.getYRot());
                float pitch = (float) Math.toRadians(e.getXRot());
                InstanceFormat.pack(instanceBuffer,
                        entX - eyeX, entY - eyeY, entZ - eyeZ,
                        yaw, pitch, 1.0f,
                        1.0f, 0.7f, 0.7f, 1.0f,
                        0.0f, 0.0f);
                // Animation clock (hub decisions/MATOU_ANIMATION.md):
                // the entity age through the declaring stub type (owner
                // discipline — never the beast), one sealed-clip pose
                // per mob per bucket. 1.20.1 names the age field
                // tickCount (Mojmap — the 1.16.5 ticksExisted name does
                // not port) and the distance clock walkDist/walkDistO
                // (the 1.12 distanceWalkedModified pair does not port —
                // the renderer interpolates prev-to-cur over
                // partialTicks, same shape as the xo interpolation
                // beside it).
                double t = e.tickCount / 20.0;
                double dist = BeastAnimation.interpDistMoved(
                        e.walkDistO, e.walkDist, partialTicks);
                Molang.Ctx ctx = BeastAnimation.animCtx(t, dist);
                MatouAnimation.AnimPose pose = BeastAnimation.poseFor(
                        beast.mobOrFirst(), t, ctx);
                Map<String, float[]> deltas = skinModel.poseDeltaMatrices(pose);
                for (int bi = 0; bi < bones; bi++) {
                    BeastAnimation.packPaletteInto(boneBuffer,
                            deltas.get(skinModel.bones.get(bi).name));
                }
            }
            instanceBuffer.flip();
            boneBuffer.flip();
            backend.activeTexture(GlBackend.GL_TEXTURE1);
            backend.bindTexture(GlBackend.GL_TEXTURE_2D, boneTexture);
            boneBytes.position(0);
            boneBytes.limit(boneBuffer.limit() * 4);
            backend.texImage2D(GlBackend.GL_TEXTURE_2D, 0, GlBackend.GL_RGBA32F,
                    4, bones, 0, GlBackend.GL_RGBA, GlBackend.GL_FLOAT, boneBytes);
            backend.activeTexture(GlBackend.GL_TEXTURE0);
            backend.bindTexture(GlBackend.GL_TEXTURE_2D, texture);
            backend.bindBuffer(GlBackend.GL_ARRAY_BUFFER, instanceVbo);
            backend.bufferData(GlBackend.GL_ARRAY_BUFFER, instanceBuffer, GlBackend.GL_STREAM_DRAW);
            // Draw-proof discipline (hub decisions/MATOU_MODEL.md, visual
            // tranche): pre-existing GL errors belong to the shared context
            // (MC's own state may carry some) — drain them so only this draw
            // is judged, then refuse loudly if GL rejects it. A rejected draw
            // that still logged "drew" would be a silent pass.
            while (GL11C.glGetError() != GL11C.GL_NO_ERROR) {
            }
            backend.drawArraysInstanced(GlBackend.GL_TRIANGLES, 0, vertexCount, bucket.getValue().size());
            int glErr = GL11C.glGetError();
            if (glErr != GL11C.GL_NO_ERROR) {
                throw new IllegalStateException("E_GL_DRAW:failed <" + glErr
                        + "> (instanced beast draw rejected — see hub decisions/GL_INSTANCING_ADAPTER.md)");
            }
        }
        if (!drawLogged) {
            drawLogged = true;
            System.out.println("[MatouRenderer] drew instances=" + total
                    + " mesh=" + vertexCount + " verts"
                    + " buckets=" + buckets.size());
        }

        backend.bindVertexArray(0);
        backend.useProgram(0);
        backend.bindBuffer(GlBackend.GL_ARRAY_BUFFER, 0);
    }

    /**
     * Reads back 16 column-major floats without moving the buffer
     * position (the uniform upload below reads the same buffer
     * afterwards — an absolute get disturbs nothing).
     */
    private static float[] colMajor(FloatBuffer buf) {
        float[] m = new float[16];
        for (int i = 0; i < 16; i++) {
            m[i] = buf.get(i);
        }
        return m;
    }

    /**
     * Every lane finite (NaN or infinite poisons the product — the
     * loading-transient signature above). Pure lane scan, no
     * allocation, disturb nothing.
     */
    private static boolean finite16(float[] m) {
        for (int i = 0; i < 16; i++) {
            if (!Float.isFinite(m[i])) {
                return false;
            }
        }
        return true;
    }
}
