package net.minecraft.world.entity;

import net.minecraft.world.level.Level;

/**
 * Loot-proof compile stub (DEV ONLY): shape-only 1.20.1 (Mojmap) vanilla
 * API used by the autoplay companion (tools/autoplay/, never shipped).
 * Never runs (compile classpath only). Only a type token: the companion
 * spawns its T1 victim through {@code EntityType} + this ctor (measured
 * via server.txt: public {@code (EntityType,Level)} — ctors are never
 * obfuscated, so no narrow-map row; the 1.12 no-arg shape does not port).
 * Positioning/removal go through the declaring {@code Entity} type (owner
 * discipline — hub decisions/LOOT.md).
 */
public class EntityType<T extends Entity> {
}
