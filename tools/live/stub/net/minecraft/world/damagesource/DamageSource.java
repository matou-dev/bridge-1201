package net.minecraft.world.damagesource;

import net.minecraft.world.entity.Entity;

/**
 * Loot compile stub: shape-only 1.20.1 (Mojmap) vanilla API used by
 * {@code forge/} sources. Never runs (compile classpath only). Only a
 * type: the {@code LivingDropsEvent} ctor takes one (measured via javap
 * against the pinned 47.2.0 universal), the forge side never reads it.
 *
 * <p>Combat tranche: the bridge combat hook resolves the true attacker
 * through {@code getEntity} (the no-arg {@code ()Entity} getter —
 * {@code getDirectEntity} is the projectile, not the author, hence the
 * Mojmap-name anchor in the narrow map; measured via server.txt +
 * joined.tsrg v2 + javap against the pinned 47.2.0 bytes). Never runs.
 */
public class DamageSource {
    public Entity getEntity() {
        return null;
    }
}
