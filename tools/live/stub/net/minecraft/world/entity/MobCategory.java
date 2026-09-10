package net.minecraft.world.entity;

/**
 * Custom-entity compile stub: shape-only 1.20.1 (Mojmap) vanilla API
 * used by {@code forge/} sources. Never runs (compile classpath only).
 * The generic beast builds through {@code EntityType.Builder} under this
 * category (measured via {@code javap -c} on the pinned 47.2.0 SRG game
 * jar: vanilla PIG chains {@code MobCategory.CREATURE}, pig-identical).
 * The constant ships with the runtime name (joined.tsrg v2 maps obf
 * {@code b} straight to {@code CREATURE} — same passthrough as the 1165
 * {@code EntityClassification} constants, never a narrow row).
 */
public class MobCategory {
    public static MobCategory CREATURE;
}
