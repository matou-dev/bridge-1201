package net.minecraftforge.event.entity.living;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Combat compile stub: 47.2.0 {@code LivingHurtEvent} carries the hurt
 * entity on the {@code LivingEvent} base behind {@code getEntity}, the
 * damage source and amount behind getters, the amount behind a setter
 * (all measured via javap against the pinned 47.2.0 universal — ctor
 * {@code (LivingEntity, DamageSource, float)}, Forge classes are never
 * obfuscated, presence is the pin). The bridge combat hook resolves the
 * struck bone through it; the autoplay combat leg drives it through the
 * genuine attack path, never by post. Never runs.
 */
public class LivingHurtEvent extends LivingEvent {
    public LivingHurtEvent(LivingEntity entity, DamageSource source,
            float amount) {
        super(entity);
    }

    public DamageSource getSource() {
        return null;
    }

    public float getAmount() {
        return 0.0f;
    }

    public void setAmount(float amount) {
    }
}
