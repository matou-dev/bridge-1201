package net.minecraftforge.event.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Spawn compile stub: shape-only Forge 1.20.1-47.2.0 API (universal jar,
 * never obfuscated). Never runs (compile classpath only). The join signal
 * (the 1.12/1.16.5 {@code EntityJoinWorldEvent} name does not exist on
 * 1.20.1): the entity lives on the {@code EntityEvent} base behind
 * {@code getEntity}, the level on this subclass behind {@code getLevel}
 * (measured via javap against the pinned 47.2.0 universal). The event is
 * {@code @Cancelable} (same measurement) -- the past-cap veto cancels
 * here. Only the members forge reads are stubbed. Pinned by
 * tools/run-live.sh -- drift fails loudly.
 */
public class EntityJoinLevelEvent extends EntityEvent {
    public EntityJoinLevelEvent(Entity entity, Level level) {
        super(entity);
    }

    public Level getLevel() {
        return null;
    }
}
