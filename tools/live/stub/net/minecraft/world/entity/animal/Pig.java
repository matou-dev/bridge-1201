package net.minecraft.world.entity.animal;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Loot-proof compile stub (DEV ONLY): shape-only 1.20.1 (Mojmap) vanilla
 * API used by the autoplay companion (tools/autoplay/, never shipped).
 * Never runs (compile classpath only). The T1 victim is a vanilla pig
 * until custom-entity registration lands (hub decisions/LOOT.md): the
 * companion spawns one through this ctor (measured via server.txt:
 * public {@code (EntityType,Level)} — ctors are never obfuscated, so no
 * narrow-map row). A {@code LivingEntity} for the simulated kill post.
 */
public class Pig extends LivingEntity {
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Pig(EntityType<? extends Pig> type, Level level) {
    }
}
