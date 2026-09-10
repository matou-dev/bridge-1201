package net.minecraftforge.registries;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * D1 compile stub: shape-only Forge 1.20.1-47.2.0 API (universal jar, never
 * obfuscated). Never runs (compile classpath only). Member {@code BLOCKS}
 * is pinned by tools/run-live.sh (D3) — drift fails loudly.
 *
 * <p>Companion shape (loot proof, DEV ONLY): the autoplay companion
 * resolves the ore, the diamond and the pig type through {@code BLOCKS},
 * {@code ITEMS} and {@code ENTITY_TYPES} (measured via javap against the
 * pinned 47.2.0 universal — no vanilla field touched, the Mojmap autoplay
 * derive pins methods only). Pinned by tools/autoplay/universal-pin.txt
 * (hub tools/run-client.sh, AUTOPLAY=1) — drift fails loudly.
 */
public class ForgeRegistries {
    public static final IForgeRegistry<Block> BLOCKS = null;
    public static final IForgeRegistry<Item> ITEMS = null;
    public static final IForgeRegistry<EntityType<?>> ENTITY_TYPES = null;
}
