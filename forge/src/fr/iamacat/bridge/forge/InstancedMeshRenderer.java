package fr.iamacat.bridge.forge;

import fr.iamacat.bridge.model.BeastModel;
import fr.iamacat.spi.render.GlBackend;
import fr.iamacat.spi.render.InstanceFormat;
import fr.iamacat.spi.model.MatouModel;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
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
 * The static mesh is baked from the shipped {@code my_beast.geo.json}
 * (hub decisions/MATOU_MODEL.md) — never a hardcoded box again.
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

    private static final String VERTEX_SHADER =
            "#version 330 core\n"
            + "layout(location = 0) in vec3 a_pos;\n"
            + "layout(location = 1) in vec2 a_uv;\n"
            + "layout(location = 2) in vec3 a_normal;\n"
            + "layout(location = 3) in vec3 i_pos;\n"
            + "layout(location = 4) in vec3 i_rot_scale;\n"
            + "layout(location = 5) in vec4 i_color;\n"
            + "layout(location = 6) in vec2 i_light;\n"
            + "uniform mat4 u_projection;\n"
            + "uniform mat4 u_view;\n"
            + "out vec4 v_color;\n"
            + "out vec3 v_normal;\n"
            + "void main() {\n"
            + "    float yaw = i_rot_scale.x;\n"
            + "    float scale = i_rot_scale.z;\n"
            + "    float cy = cos(yaw);\n"
            + "    float sy = sin(yaw);\n"
            + "    mat3 rotY = mat3(cy, 0.0, sy, 0.0, 1.0, 0.0, -sy, 0.0, cy);\n"
            + "    vec3 localPos = rotY * (a_pos * scale);\n"
            + "    vec3 worldRelPos = localPos + i_pos;\n"
            + "    gl_Position = u_projection * u_view * vec4(worldRelPos, 1.0);\n"
            + "    v_color = i_color;\n"
            + "    v_normal = rotY * a_normal;\n"
            + "}\n";

    private static final String FRAGMENT_SHADER =
            "#version 330 core\n"
            + "in vec4 v_color;\n"
            + "in vec3 v_normal;\n"
            + "out vec4 fragColor;\n"
            + "void main() {\n"
            + "    vec3 lightDir = normalize(vec3(0.2, 1.0, -0.7));\n"
            + "    float diff = max(dot(v_normal, lightDir), 0.0) * 0.4 + 0.6;\n"
            + "    vec4 col = v_color;\n"
            + "    col.rgb *= diff;\n"
            + "    fragColor = col;\n"
            + "}\n";

    private final GlBackend backend;
    private boolean initialized;
    private boolean drawLogged;
    private int program;
    private int vao;
    private int meshVbo;
    private int instanceVbo;
    private int vertexCount;
    private int uProjLoc;
    private int uViewLoc;
    private final FloatBuffer viewMatrixBuffer;
    private final FloatBuffer projMatrixBuffer;
    private FloatBuffer instanceBuffer;

    private InstancedMeshRenderer() {
        this.backend = new Lwjgl3Backend();
        this.viewMatrixBuffer = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        this.projMatrixBuffer = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        this.instanceBuffer = ByteBuffer.allocateDirect(512 * InstanceFormat.STRIDE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
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

        vao = backend.genVertexArrays();
        backend.bindVertexArray(vao);

        meshVbo = backend.genBuffers();
        backend.bindBuffer(GlBackend.GL_ARRAY_BUFFER, meshVbo);
        // Model tranche: the static mesh is the SPI bake of the shipped
        // beast geometry, never a hardcoded box. A missing or broken
        // model refuses here, loudly, before the first frame.
        float[] mesh = BeastModel.cached().mesh();
        vertexCount = mesh.length / MatouModel.VERTEX_STRIDE;
        FloatBuffer meshData = ByteBuffer.allocateDirect(mesh.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        meshData.put(mesh);
        meshData.flip();
        backend.bufferData(GlBackend.GL_ARRAY_BUFFER, meshData, GlBackend.GL_STATIC_DRAW);

        int meshStride = MatouModel.VERTEX_STRIDE * 4;
        backend.enableVertexAttribArray(0);
        backend.vertexAttribPointer(0, 3, GlBackend.GL_FLOAT, false, meshStride, 0);
        backend.enableVertexAttribArray(1);
        backend.vertexAttribPointer(1, 2, GlBackend.GL_FLOAT, false, meshStride, 3 * 4);
        backend.enableVertexAttribArray(2);
        backend.vertexAttribPointer(2, 3, GlBackend.GL_FLOAT, false, meshStride, 5 * 4);

        instanceVbo = backend.genBuffers();
        backend.bindBuffer(GlBackend.GL_ARRAY_BUFFER, instanceVbo);

        int instStride = InstanceFormat.STRIDE_BYTES;
        backend.enableVertexAttribArray(3);
        backend.vertexAttribPointer(3, 3, GlBackend.GL_FLOAT, false, instStride, 0);
        backend.vertexAttribDivisor(3, 1);

        backend.enableVertexAttribArray(4);
        backend.vertexAttribPointer(4, 3, GlBackend.GL_FLOAT, false, instStride, 12);
        backend.vertexAttribDivisor(4, 1);

        backend.enableVertexAttribArray(5);
        backend.vertexAttribPointer(5, 4, GlBackend.GL_FLOAT, false, instStride, 24);
        backend.vertexAttribDivisor(5, 1);

        backend.enableVertexAttribArray(6);
        backend.vertexAttribPointer(6, 2, GlBackend.GL_FLOAT, false, instStride, 40);
        backend.vertexAttribDivisor(6, 1);

        backend.bindVertexArray(0);
        backend.bindBuffer(GlBackend.GL_ARRAY_BUFFER, 0);
        initialized = true;
        System.out.println("[MatouRenderer] ready mesh=" + vertexCount
                + " verts stride=" + MatouModel.VERTEX_STRIDE
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

        int count = 0;
        instanceBuffer.clear();
        for (MatouEntity beast : found) {
            // Owner discipline (hub decisions/LOOT.md): inherited
            // vanilla members go through the declaring Entity type,
            // never through the beast — Reobf maps the exact bytecode
            // owner, so a MatouEntity-owned ref passes through silently
            // and dies linking live (caught offline at E0 by reobfing
            // the staged jar and grepping the pool, never recalled).
            Entity e = beast;
            if (e != null && e.isAlive()) {
                if (instanceBuffer.remaining() < InstanceFormat.FLOATS_PER_INSTANCE) {
                    FloatBuffer expanded = ByteBuffer.allocateDirect(instanceBuffer.capacity() * 2 * 4)
                            .order(ByteOrder.nativeOrder()).asFloatBuffer();
                    instanceBuffer.flip();
                    expanded.put(instanceBuffer);
                    instanceBuffer = expanded;
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
                count++;
            }
        }

        if (count == 0) {
            return;
        }
        instanceBuffer.flip();

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

        backend.bindBuffer(GlBackend.GL_ARRAY_BUFFER, instanceVbo);
        backend.bufferData(GlBackend.GL_ARRAY_BUFFER, instanceBuffer, GlBackend.GL_STREAM_DRAW);

        GL11C.glEnable(GL11C.GL_DEPTH_TEST);
        GL11C.glDepthMask(true);
        GL11C.glEnable(GL11C.GL_CULL_FACE);
        GL11C.glCullFace(GL11C.GL_BACK);

        backend.useProgram(program);
        backend.uniformMatrix4fv(uProjLoc, false, projMatrixBuffer);
        backend.uniformMatrix4fv(uViewLoc, false, viewMatrixBuffer);

        backend.bindVertexArray(vao);
        // Draw-proof discipline (hub decisions/MATOU_MODEL.md, visual
        // tranche): pre-existing GL errors belong to the shared context
        // (MC's own state may carry some) — drain them so only this draw
        // is judged, then refuse loudly if GL rejects it. A rejected draw
        // that still logged "drew" would be a silent pass.
        while (GL11C.glGetError() != GL11C.GL_NO_ERROR) {
        }
        backend.drawArraysInstanced(GlBackend.GL_TRIANGLES, 0, vertexCount, count);
        int glErr = GL11C.glGetError();
        if (glErr != GL11C.GL_NO_ERROR) {
            throw new IllegalStateException("E_GL_DRAW:failed <" + glErr
                    + "> (instanced beast draw rejected — see hub decisions/GL_INSTANCING_ADAPTER.md)");
        }
        if (!drawLogged) {
            drawLogged = true;
            System.out.println("[MatouRenderer] drew instances=" + count
                    + " mesh=" + vertexCount + " verts");
        }

        backend.bindVertexArray(0);
        backend.useProgram(0);
        backend.bindBuffer(GlBackend.GL_ARRAY_BUFFER, 0);
    }
}
