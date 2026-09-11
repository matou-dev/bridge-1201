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
 *
 * <p>Combat-companion compile stub: the autoplay combat leg polls the
 * struck beast health through this declaring type (owner discipline,
 * hub decisions/LOOT.md) — {@code getHealth} is the no-arg {@code ()F}
 * getter, measured via server.txt + joined.tsrg v2 + javap against the
 * pinned 47.2.0 bytes (the same-descriptor max-health sibling is NOT
 * this, hence the SRG anchor in the autoplay derive). Never runs.
 */
public class LivingEntity extends Entity {
    public AttributeInstance getAttribute(Attribute attribute) {
        return null;
    }

    public void setHealth(float health) {
    }

    public float getHealth() {
        return 0.0f;
    }

    public float getMaxHealth() {
        return 0;
    }
}
