package net.minecraftforge.event.level;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.Event;

/**
 * Loot compile stub: shape-only Forge 1.20.1-47.2.0 API (universal jar,
 * never obfuscated). Never runs (compile classpath only). The break
 * signal lives in the {@code level} package on 1.20.1 (measured via
 * javap against the pinned 47.2.0 universal — the 1.12/1.16.5
 * {@code world} package shape does not port) and carries the level
 * behind {@code getLevel} as a {@code LevelAccessor} (the 1.16.5
 * {@code IWorld} shape does not port either). Only the members forge
 * reads are stubbed. Pinned by tools/run-live.sh — drift fails loudly.
 */
public class BlockEvent extends Event {
    public BlockEvent(LevelAccessor level, BlockPos pos, BlockState state) {
    }

    public LevelAccessor getLevel() {
        return null;
    }

    public BlockPos getPos() {
        return null;
    }

    public BlockState getState() {
        return null;
    }

    public static class BreakEvent extends BlockEvent {
        public BreakEvent(Level level, BlockPos pos, BlockState state,
                Player player) {
            super(level, pos, state);
        }

        public Player getPlayer() {
            return null;
        }

        public int getExpToDrop() {
            return 0;
        }

        public void setExpToDrop(int exp) {
        }
    }
}
