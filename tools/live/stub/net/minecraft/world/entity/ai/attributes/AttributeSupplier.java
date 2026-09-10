package net.minecraft.world.entity.ai.attributes;

/**
 * Custom-entity compile stub: shape-only 1.20.1 (Mojmap) vanilla API
 * used by {@code forge/} sources. Never runs (compile classpath only).
 * Fresh entity types carry no attribute map (the vanilla
 * {@code LivingEntity} ctor NPEs without one — measured live on 1165),
 * so the beast reuses the vanilla pig map wholesale
 * ({@code Pig.createAttributes().build()} — pig-identical, never
 * hand-copied values). Only the finishing {@code build} call is
 * Mojmap-named here (measured via server.txt + joined.tsrg v2 + javap).
 * Pinned by tools/run-live.sh (narrow map) — drift fails loudly.
 */
public class AttributeSupplier {
    public static final class Builder {
        public AttributeSupplier build() {
            return null;
        }
    }
}
