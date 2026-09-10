package net.minecraft.world.entity.animal;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

/**
 * Spawn compile stub: shape-only 1.20.1 (Mojmap) vanilla API used by
 * {@code forge/} sources. Never runs (compile classpath only). The T1
 * victim/host is a vanilla pig until custom-entity registration lands
 * (hub decisions/SPAWN.md): the bridge lands one through this ctor
 * (measured via server.txt: public {@code (EntityType,Level)} -- ctors
 * are never obfuscated, so no narrow-map row; the 1.12 no-arg shape does
 * not port, the type arrives through {@code EntityType.PIG}). A
 * {@code LivingEntity} for the hp seam and the kill post.
 *
 * <p>Custom entity tranche: the generic beast reuses the vanilla pig
 * attribute map wholesale ({@code createAttributes().build()} —
 * pig-identical, never hand-copied values — measured via server.txt +
 * joined.tsrg v2 + javap on the pinned 47.2.0 bytes). Pinned by
 * tools/run-live.sh (narrow map) — drift fails loudly.
 */
public class Pig extends LivingEntity {
    public Pig(EntityType<? extends Pig> type, Level level) {
    }

    public static AttributeSupplier.Builder createAttributes() {
        return null;
    }
}
