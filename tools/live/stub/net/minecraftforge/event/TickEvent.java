package net.minecraftforge.event;

import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.LogicalSide;

/**
 * D1 compile stub: shape-only Forge 1.20.1-47.2.0 API (universal jar, never
 * obfuscated). Never runs (compile classpath only). Members {@code side},
 * {@code phase}, {@code Phase.END} and {@code LevelTickEvent.level} are
 * pinned by tools/run-live.sh (D3) — drift fails loudly.
 */
public class TickEvent extends Event {
    public final LogicalSide side = null;
    public final Phase phase = null;

    public enum Phase {
        START, END
    }

    public static class LevelTickEvent extends TickEvent {
        public final Level level = null;
    }
}
