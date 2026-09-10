package net.minecraft.server.level;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Loot compile stub: shape-only 1.20.1 (Mojmap) vanilla API used by
 * {@code forge/} sources. Never runs (compile classpath only). The
 * carrier sink is the public {@code addFreshEntity} (measured via javap
 * against the pinned 47.2.0 bytes — the 1.12 {@code World.spawnEntity}
 * / 1.16.5 {@code ServerWorld.addEntity} shapes do not port). Mojmap
 * name measured, not recalled (server.txt + joined.tsrg v2 + javap:
 * public {@code (Entity)Z} method). Pinned by tools/run-live.sh
 * (narrow map) — drift fails loudly.
 */
public class ServerLevel extends Level {
    public boolean addFreshEntity(Entity entity) {
        return false;
    }
}
