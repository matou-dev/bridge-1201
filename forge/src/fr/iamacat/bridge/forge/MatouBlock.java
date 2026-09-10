package fr.iamacat.bridge.forge;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Registration landing (see hub decisions/REGISTRATION.md): the one
 * generic Forge block every content block registers as. Hardness lands
 * via {@code BlockBehaviour.Properties} strength — never hardcoded per
 * content, never a subclass per content. Opacity has no measured
 * Properties-level slot on 1.20.1 (the live tranche confirms against
 * the provisioned 47.2.0 jars): translucent specs refuse loudly at
 * registration time, never default. Only this package may touch
 * {@code net.minecraft} / {@code net.minecraftforge}.
 */
public final class MatouBlock extends Block {
    /**
     * Args are pre-validated by the registering mod (E_REG_* owns the
     * refusals); the constructor only lands them.
     */
    public MatouBlock(float hardness) {
        super(BlockBehaviour.Properties.of().strength(hardness));
    }
}
