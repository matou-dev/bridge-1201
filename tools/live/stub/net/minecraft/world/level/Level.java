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
 *
 * <p>Companion shape (loot proof, DEV ONLY): the autoplay companion reads
 * through {@code getBlockState}, places through {@code setBlock}, clears
 * through {@code removeBlock} (the 1.12 {@code setBlockToAir} shape does
 * not port — measured via server.txt + joined.tsrg v2 + javap, same
 * practice). {@code EntityGetter} is direct here, transitive in vanilla
 * ({@code Level} implements {@code LevelAccessor} extends {@code
 * CommonLevelAccessor} extends {@code EntityGetter} — measured via javap
 * on the pinned SRG jar): the companion calls
 * {@code getEntitiesOfClass} through the declaring interface type (owner
 * discipline — Reobf maps the exact bytecode owner). Pinned by the
 * AUTOPLAY derive in hub tools/run-client.sh (want.txt) — drift fails
 * loudly.
 */
public class Level implements LevelAccessor, EntityGetter {
    public static final ResourceKey<Level> OVERWORLD = null;

    public ResourceKey<Level> dimension() {
        return null;
    }

    /**
     * Loot shape (hub decisions/LOOT.md): server-side gate. Mojmap name
     * measured against the pinned 47.2.0 bytes (public {@code ()Z}
     * method — the 1.16.5 {@code isRemote} field shape does not port).
     * Pinned by tools/run-live.sh (narrow map) — drift fails loudly.
     */
    public boolean isClientSide() {
        return false;
    }

    public boolean setBlock(BlockPos pos, BlockState state, int flags) {
        return false;
    }

    public BlockState getBlockState(BlockPos pos) {
        return null;
    }

    public boolean removeBlock(BlockPos pos, boolean isMoving) {
        return false;
    }
}
