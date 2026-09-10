package net.minecraftforge.event.entity.living;

import java.util.Collection;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * Loot compile stub: shape-only Forge 1.20.1-47.2.0 API (universal jar,
 * never obfuscated). Never runs (compile classpath only). Only a
 * signal type: the forge side records every dim-0 kill through the
 * inherited {@code getEntity} (T1 any-kill-pays — hub
 * decisions/LOOT.md). Ctor measured via javap against the pinned 47.2.0
 * universal (5 args with the source — the 1.12 {@code specialDropValue}
 * shape is gone). Pinned by tools/run-live.sh — drift fails loudly.
 */
public class LivingDropsEvent extends LivingEvent {
    public LivingDropsEvent(LivingEntity entity, DamageSource source,
            Collection<ItemEntity> drops, int lootingLevel,
            boolean recentlyHit) {
        super(entity);
    }
}
