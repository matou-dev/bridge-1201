package net.minecraftforge.event.entity.living;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.EntityEvent;

/**
 * Loot compile stub: shape-only Forge 1.20.1-47.2.0 API (universal jar,
 * never obfuscated). Never runs (compile classpath only). Kills arrive
 * through {@code getEntity} as a {@code LivingEntity} (measured via
 * javap against the pinned 47.2.0 universal — the 1.12/1.16.5
 * {@code getEntityLiving} shape does not port). Only the members forge
 * reads are stubbed. Pinned by tools/run-live.sh — drift fails loudly.
 */
public class LivingEvent extends EntityEvent {
    public LivingEvent(LivingEntity entity) {
        super(entity);
    }

    public LivingEntity getEntity() {
        return null;
    }
}
