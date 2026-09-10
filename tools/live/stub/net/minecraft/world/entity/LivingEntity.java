package net.minecraft.world.entity;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

/**
 * Loot compile stub: shape-only 1.20.1 (Mojmap) vanilla API used by
 * {@code forge/} sources. Never runs (compile classpath only). Only a
 * type: {@code LivingEvent.getEntity} returns this (measured via javap
 * against the pinned 47.2.0 universal -- the 1.12
 * {@code getEntityLiving} shape does not port).
 *
 * <p>Spawn shape (hub decisions/SPAWN.md, hp tranche): the content hp
 * lands through {@code getAttribute} plus {@code setHealth} /
 * {@code getMaxHealth} (measured via server.txt + joined.tsrg v2 +
 * javap, same practice). Pinned by tools/run-live.sh (narrow map) --
 * drift fails loudly.
 */
public class LivingEntity extends Entity {
    public AttributeInstance getAttribute(Attribute attribute) {
        return null;
    }

    public void setHealth(float health) {
    }

    public float getMaxHealth() {
        return 0;
    }
}
