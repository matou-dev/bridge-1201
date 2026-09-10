package net.minecraftforge.event.entity;

import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.Event;

/**
 * Loot compile stub: shape-only Forge 1.20.1-47.2.0 API (universal jar,
 * never obfuscated). Never runs (compile classpath only). Only a
 * supertype: {@code LivingEvent} extends this (measured via javap
 * against the pinned 47.2.0 universal).
 *
 * <p>Spawn shape (hub decisions/SPAWN.md): the join signal resolves the
 * joined entity through {@code getEntity} on this base (measured via
 * javap against the pinned 47.2.0 universal -- the 1.12 public-field
 * shape does not port). Pinned by tools/run-live.sh -- drift fails
 * loudly.
 */
public class EntityEvent extends Event {
    public EntityEvent(Entity entity) {
    }

    public Entity getEntity() {
        return null;
    }
}
