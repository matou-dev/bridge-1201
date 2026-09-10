package net.minecraft.world.entity;

import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.Level;

/**
 * Spawn compile stub: shape-only 1.20.1 (Mojmap) vanilla API used by
 * {@code forge/} sources. Never runs (compile classpath only). Only a
 * type token: the T1 pig type goes through this (measured via
 * server.txt: {@code PIG} is a public static field -- ctors take it, so
 * no narrow-map row for construction; the field itself is pinned).
 * Non-final by design (registry ref, Items.DIAMOND precedent). Pinned by
 * tools/run-live.sh (narrow map) -- drift fails loudly.
 *
 * <p>Custom entity tranche: the one generic beast builds through the
 * {@code Builder} ({@code of}/{@code sized}/{@code clientTrackingRange}/
 * {@code build}, measured via server.txt + joined.tsrg v2 + javap on the
 * pinned 47.2.0 bytes — the 1.7.10/1.12 {@code EntityRegistry} call does
 * not exist on 1.20.1). The factory SAM stays Mojmap-named
 * ({@code create} — LambdaMetafactory ignores the name on a
 * single-abstract-method interface, 1165-proven live, never a narrow
 * row). Pinned by tools/run-live.sh (narrow map) — drift fails loudly.
 */
public class EntityType<T extends Entity> {
    public static EntityType<Pig> PIG;

    public interface EntityFactory<F extends Entity> {
        F create(EntityType<F> type, Level level);
    }

    public static final class Builder<B extends Entity> {
        public static <N extends Entity> Builder<N> of(
                EntityFactory<N> factory, MobCategory category) {
            return null;
        }

        public Builder<B> sized(float width, float height) {
            return null;
        }

        public Builder<B> clientTrackingRange(int range) {
            return null;
        }

        public EntityType<B> build(String id) {
            return null;
        }
    }
}
