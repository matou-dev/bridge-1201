package net.minecraft.world.level.block.state;

/**
 * Registration compile stub: shape-only 1.20.1 (Mojmap) vanilla API used
 * by {@code forge/} sources. Never runs (compile classpath only). Only
 * the members {@code forge/} references ({@code of}, {@code strength}) —
 * the live tranche pins them against the provisioned 47.2.0 jars, drift
 * fails loudly there, never silently here.
 */
import net.minecraft.world.level.block.Block;

public class BlockBehaviour {
    public static class Properties {
        public static Properties of() {
            return null;
        }

        public Properties strength(float destroyTime) {
            return this;
        }
    }

    /**
     * Loot shape (hub decisions/LOOT.md): declaring type of
     * {@code getBlock} (forge reads the ore match through this type —
     * owner discipline; measured via javap against the pinned 47.2.0
     * bytes, the 1.16.5 {@code AbstractBlockState} shape does not
     * port). Pinned by tools/run-live.sh (narrow map) — drift fails
     * loudly.
     */
    public static class BlockStateBase {
        public Block getBlock() {
            return null;
        }
    }
}
