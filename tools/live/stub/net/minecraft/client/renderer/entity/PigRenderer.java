package net.minecraft.client.renderer.entity;

import net.minecraft.world.entity.animal.Pig;

/**
 * Custom-entity compile stub: shape-only 1.20.1 (Mojmap) vanilla client
 * API used by {@code forge/} sources. Never runs (compile classpath
 * only — see {@code EntityRenderer}). The generic beast reuses this
 * renderer verbatim until the custom-renderer tranche (measured via
 * javap against the pinned 47.2.0 SRG client jar: single
 * {@code (Context)} ctor — same single-arg pattern as the 1165 pig
 * renderer). Only the ctor forge reads is stubbed. Client link unproven
 * until the live tranche.
 */
public class PigRenderer extends EntityRenderer<Pig> {
    public PigRenderer(EntityRendererProvider.Context context) {
    }
}
