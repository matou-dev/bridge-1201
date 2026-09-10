package net.minecraft.client.renderer.entity;

import net.minecraft.world.entity.Entity;

/**
 * Custom-entity compile stub: shape-only 1.20.1 (Mojmap) vanilla client
 * API used by {@code forge/} sources. Never runs (compile classpath
 * only — see {@code EntityRenderer}). The factory the renderer event
 * takes (measured via client.txt: Mojmap {@code create(Context)} —
 * same factory shape as the 1165 {@code IRenderFactory}). Only the SAM
 * forge reads is stubbed. Client link unproven until the live tranche.
 */
public interface EntityRendererProvider<T extends Entity> {
    EntityRenderer<T> create(Context context);

    public static final class Context {
    }
}
