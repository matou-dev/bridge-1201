package net.minecraftforge.event.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

/**
 * Custom-entity compile stub: shape-only Forge 1.20.1-47.2.0 API
 * (universal jar, never obfuscated). Never runs (compile classpath
 * only). The beast's attribute map registers here (measured via javap
 * against the pinned 47.2.0 universal: mod-bus event — the real class
 * also implements {@code IModBusEvent} — single call
 * {@code put(EntityType, AttributeSupplier)}). Without this the vanilla
 * {@code LivingEntity} ctor NPEs on the first landing (fresh types carry
 * no attribute map — measured live on 1165, never assumed). Only the
 * member forge reads is stubbed. Pinned by tools/run-live.sh — drift
 * fails loudly.
 */
public class EntityAttributeCreationEvent
        extends net.minecraftforge.eventbus.api.Event {
    public void put(EntityType<? extends LivingEntity> type,
            AttributeSupplier map) {
    }
}
