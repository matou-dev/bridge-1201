package fr.iamacat.bridge.forge;

import fr.iamacat.bridge.CellSink;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * D1 live sink: pure decision cells to 1.20.1 level edits. Plane
 * {@code "x,z"} cells land at the wire y/block, volume
 * {@code "x,y,z:ns:block"} cells (V3 structures) at their own y with a
 * block resolved by name (cached, refused loudly when unknown).
 * Server side only; the caller owns threading (FML server tick).
 * Only this package may touch MC/Forge.
 */
public final class WorldCellSink implements CellSink {
    /**
     * Wire policy: y lives in 0..255 (a conservative subset of the 1.20.1
     * overworld span -64..319 — never out of bounds by construction).
     */
    static final int MAX_Y = 255;

    private final Level level;
    private final int y;
    private final Block block;
    private final Map<String, Block> resolved =
            new HashMap<String, Block>();

    public WorldCellSink(Level level, int y, Block block) {
        if (level == null) {
            throw new NullPointerException("E_FORGE_WORLD:null");
        }
        if (block == null) {
            throw new NullPointerException("E_FORGE_BLOCK:null");
        }
        checkY(y);
        this.level = level;
        this.y = y;
        this.block = block;
    }

    /** Single owner of the wire y rule (also used at wire bind). */
    static void checkY(int y) {
        if (y < 0 || y > MAX_Y) {
            throw new IllegalArgumentException(
                    "E_FORGE_Y:range <" + y + "> (want 0.." + MAX_Y + ")");
        }
    }

    public void setCell(int x, int z) {
        // Flag 3: update neighbours + send to clients (dedicated server:
        // the world file is the verdict, neighbours stay consistent).
        level.setBlock(new BlockPos(x, y, z),
                block.defaultBlockState(), 3);
    }

    @Override
    public void setBlock(int x, int y, int z, String blockName) {
        if (blockName == null) {
            throw new NullPointerException("E_FORGE_BLOCK:null");
        }
        checkY(y);
        Block at = resolved.get(blockName);
        if (at == null) {
            at = ForgeRegistries.BLOCKS.getValue(
                    new ResourceLocation(blockName));
            if (at == null) {
                throw new IllegalArgumentException(
                        "E_FORGE_BLOCK:unknown <" + blockName + ">");
            }
            resolved.put(blockName, at);
        }
        level.setBlock(new BlockPos(x, y, z), at.defaultBlockState(), 3);
    }
}
