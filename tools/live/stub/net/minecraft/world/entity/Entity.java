package net.minecraft.world.entity;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Loot compile stub: shape-only 1.20.1 (Mojmap) vanilla API used by
 * {@code forge/} sources. Never runs (compile classpath only). Declaring
 * type of {@code level()}/{@code getX/getY/getZ} (forge reads inherited
 * vanilla members through this type — hub decisions/LOOT.md owner
 * discipline). Mojmap names measured, not recalled, against the pinned
 * 47.2.0 bytes (server.txt + joined.tsrg v2 + javap): {@code level} is a
 * public {@code ()Level} method (the 1.16.5 public-field shape does not
 * port), {@code getX/getY/getZ} are public {@code ()D} methods (the 1.12
 * {@code posX} field shape does not port). Pinned by tools/run-live.sh
 * (narrow map) — drift fails loudly.
 *
 * <p>Companion shape (loot proof, DEV ONLY): the autoplay companion
 * positions its pig victim through {@code setPos} and removes it through
 * {@code discard} (the 1.12 {@code setDead} shape does not port — measured
 * via server.txt + joined.tsrg v2 + javap, same practice). Pinned by the
 * AUTOPLAY derive in hub tools/run-client.sh (want.txt) — drift fails
 * loudly.
 *
 * <p>Renderer shape (hub decisions/MATOU_MODEL.md + GL_INSTANCING_ADAPTER.md):
 * frame interpolation rides the {@code xo/yo/zo} olds (public doubles —
 * the 1.12 {@code prevPos} shape does not port) with the current
 * {@code getX/getY/getZ}, yaw/pitch ride the current
 * {@code getYRot/getXRot} (public {@code ()F} methods). Measured via
 * server.txt + joined.tsrg v2 + javap on the pinned 47.2.0 inner server
 * jar (obf {@code J/K/L}, {@code M/N} for the {@code yRotO/xRotO} olds,
 * {@code dy/dA} for the getters — same practice). Pinned by
 * tools/run-live.sh (narrow map) — drift fails loudly.
 */
public class Entity {
    public double xo;
    public double yo;
    public double zo;
    public float yRotO;
    public float xRotO;

    public float getYRot() {
        return 0;
    }

    public float getXRot() {
        return 0;
    }

    public Level level() {
        return null;
    }

    public double getX() {
        return 0;
    }

    public double getY() {
        return 0;
    }

    public double getZ() {
        return 0;
    }

    public void setPos(double x, double y, double z) {
    }

    public void discard() {
    }

    /**
     * Spawn shape (hub decisions/SPAWN.md, T1 vanilla host): the census
     * id goes through {@code getId}, the living check through
     * {@code isAlive}, landings position through {@code moveTo} (the 1.12
     * {@code getEntityId} / {@code isDead} / {@code setPositionAndRotation}
     * shapes do not port -- measured via server.txt + joined.tsrg v2 +
     * javap, same practice). Pinned by tools/run-live.sh (narrow map).
     */
    public int getId() {
        return 0;
    }

    public boolean isAlive() {
        return false;
    }

    public void moveTo(double x, double y, double z, float yaw,
            float pitch) {
    }

    /**
     * Combat compile stub: 47.2.0 {@code Entity} declares the attacker
     * eye/look surface the bridge combat hook reads through this
     * declaring type (owner discipline, hub decisions/LOOT.md) —
     * {@code getEyePosition} is the no-arg {@code ()Vec3} overload and
     * {@code getLookAngle} the no-arg {@code ()Vec3} look, both measured
     * via server.txt + joined.tsrg v2 + javap against the pinned 47.2.0
     * bytes. The autoplay combat leg reads the standing eye through
     * {@code getEyeHeight} (same measure). Never runs.
     */
    public Vec3 getEyePosition() {
        return null;
    }

    public Vec3 getLookAngle() {
        return null;
    }

    public float getEyeHeight() {
        return 0.0f;
    }
}
