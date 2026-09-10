package net.minecraft.server.level;

import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
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
 *
 * <p>Companion shape (loot proof, DEV ONLY): the autoplay companion reads
 * the joined player through {@code players} (the 1.12 {@code
 * playerEntities} field shape does not port — measured via server.txt +
 * joined.tsrg v2 + javap, same practice). Pinned by the AUTOPLAY derive
 * in hub tools/run-client.sh (want.txt) — drift fails loudly.
 */
public class ServerLevel extends Level {
    public boolean addFreshEntity(Entity entity) {
        return false;
    }

    public List<Player> players() {
        return null;
    }
}
