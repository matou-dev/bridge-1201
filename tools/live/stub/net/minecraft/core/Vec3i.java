package net.minecraft.core;

/**
 * Loot compile stub: shape-only 1.20.1 (Mojmap) vanilla API used by
 * {@code forge/} sources. Never runs (compile classpath only). Declaring
 * type of {@code getX/getY/getZ} (forge reads inherited vanilla members
 * through this type — hub decisions/LOOT.md owner discipline). Mojmap
 * names measured, not recalled, against the pinned 47.2.0 bytes
 * (server.txt + joined.tsrg v2 + javap): {@code getX/getY/getZ} are
 * public {@code ()I} methods. Pinned by tools/run-live.sh (narrow map) —
 * drift fails loudly.
 */
public class Vec3i {
    public int getX() {
        return 0;
    }

    public int getY() {
        return 0;
    }

    public int getZ() {
        return 0;
    }
}
