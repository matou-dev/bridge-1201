package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.state.BlockState;

/**
 * D1 compile stub: shape-only 1.20.1 (Mojmap) vanilla API used by
 * {@code forge/} sources. Never runs (compile classpath only). Members
 * {@code dimension}, {@code setBlock} and field {@code OVERWORLD} are
 * reobfuscated to SRG at D3 time (see tools/run-live.sh narrow map) and
 * pinned to the provisioned 1.20.1-47.2.0 jars — drift fails loudly.
 */
public class Level {
    public static final ResourceKey<Level> OVERWORLD = null;

    public ResourceKey<Level> dimension() {
        return null;
    }

    public boolean setBlock(BlockPos pos, BlockState state, int flags) {
        return false;
    }
}
