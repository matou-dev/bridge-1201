package net.minecraftforge.client.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * Custom-entity compile stub: shape-only Forge 1.20.1-47.2.0 API
 * (universal jar, never obfuscated). Never runs (compile classpath
 * only). The generic beast reuses the vanilla pig renderer through this
 * mod-bus event (measured via javap against the pinned 47.2.0
 * universal: public no-arg event, single call
 * {@code registerEntityRenderer(EntityType, EntityRendererProvider)} —
 * the 1.16.5 {@code RenderingRegistry} path does not exist on 1.20.1).
 * Only the member forge reads is stubbed. Pinned by tools/run-live.sh —
 * drift fails loudly. Client link unproven until the live tranche (the
 * server Reobf leaves these refs alone; what the client runtime wants
 * them named is measured at live time, never assumed here).
 */
public class EntityRenderersEvent
        extends net.minecraftforge.eventbus.api.Event {
    public static final class RegisterRenderers
            extends EntityRenderersEvent {
        public <T extends Entity> void registerEntityRenderer(
                EntityType<? extends T> type,
                EntityRendererProvider<T> provider) {
        }
    }
}
