package net.minecraft.world.level.block;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * D1 compile stub: shape-only 1.20.1 (Mojmap) vanilla API used by
 * {@code forge/} sources. Never runs (compile classpath only). Member
 * {@code defaultBlockState} is reobfuscated to SRG at D3 time (see
 * tools/run-live.sh narrow map) and pinned — drift fails loudly. The
 * {@code Properties} constructor is the registration surface (see hub
 * decisions/REGISTRATION.md) — pinned by the live tranche, never
 * widened here beyond what {@code forge/} references.
 */
public class Block {
    public Block(BlockBehaviour.Properties properties) {
    }

    public BlockState defaultBlockState() {
        return null;
    }
}
