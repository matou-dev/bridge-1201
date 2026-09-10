package net.minecraft.world.entity;

import net.minecraft.world.level.Level;

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
 */
public class Entity {
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
}
