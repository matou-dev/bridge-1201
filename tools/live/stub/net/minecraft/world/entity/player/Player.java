package net.minecraft.world.entity.player;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Loot compile stub: shape-only 1.20.1 (Mojmap) vanilla API used by
 * {@code forge/} sources. Never runs (compile classpath only). Only a
 * type: the {@code BreakEvent} ctor takes one (measured via javap
 * against the pinned 47.2.0 universal), the forge side never reads it
 * (T1 any-break-pays — hub decisions/LOOT.md).
 *
 * <p>Combat tranche: the autoplay combat leg drives the genuine vanilla
 * attack path through {@code attack} (declared on {@code Player},
 * measured via server.txt + joined.tsrg v2 + javap against the pinned
 * 47.2.0 bytes — not the same-named {@code ServerPlayer} row). The
 * hierarchy is vanilla truth ({@code Player} is a {@code LivingEntity},
 * hence an {@code Entity}): the companion teleports through the
 * declaring {@code Entity} and strikes through {@code Player} (owner
 * discipline, hub decisions/LOOT.md). Never runs.
 */
public class Player extends LivingEntity {
    public void attack(Entity target) {
    }
}
