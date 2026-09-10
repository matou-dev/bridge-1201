package net.minecraft.world.entity.animal;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
 */
public class Pig extends LivingEntity {
    public Pig(EntityType<? extends Pig> type, Level level) {
    }
}
