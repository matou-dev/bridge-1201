package net.minecraft.world.level;

import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * Loot-proof compile stub (DEV ONLY): shape-only 1.20.1 (Mojmap) vanilla
 * API used by the autoplay companion (tools/autoplay/, never shipped).
 * Never runs (compile classpath only). Declaring type of the 2-arg
 * {@code getEntitiesOfClass} (measured via server.txt + joined.tsrg v2 +
 * javap, same practice — a {@code default} interface method, the 1.12
 * {@code loadedEntityList} field shape does not port): the companion
 * calls it through this type (owner discipline — Reobf maps the exact
 * bytecode owner), and {@code Level} honestly implements it here as in
 * vanilla ({@code Level} implements {@code LevelAccessor} extends {@code
 * CommonLevelAccessor} extends {@code EntityGetter} — measured via javap
 * on the pinned SRG jar). Pinned by the AUTOPLAY derive in hub
 * tools/run-client.sh (want.txt) — drift fails loudly.
 */
public interface EntityGetter {
    // Default in vanilla (measured via javap on the pinned SRG jar:
    // m_45976_ is a default interface method) — the stub keeps the
    // default shape so Level subtypes inherit it like the runtime.
    default <T extends Entity> List<T> getEntitiesOfClass(
            Class<? extends T> cls, AABB box) {
        return null;
    }
}
