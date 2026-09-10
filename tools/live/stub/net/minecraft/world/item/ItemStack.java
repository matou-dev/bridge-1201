package net.minecraft.world.item;

import net.minecraft.world.level.ItemLike;

/**
 * Loot compile stub: shape-only 1.20.1 (Mojmap) vanilla API used by
 * {@code forge/} sources. Never runs (compile classpath only). Ctor
 * measured via javap against the pinned 47.2.0 bytes (public
 * {@code (ItemLike,int)} — ctors are never obfuscated, so no
 * narrow-map row). The forge side wraps the diamond carrier in this.
 *
 * <p>Companion shape (loot proof, DEV ONLY): the autoplay companion reads
 * the carrier content through {@code getItem} (measured via server.txt +
 * joined.tsrg v2 + javap, same practice). Pinned by the AUTOPLAY derive
 * in hub tools/run-client.sh (want.txt) — drift fails loudly.
 */
public class ItemStack {
    public ItemStack(ItemLike item, int size) {
    }

    public Item getItem() {
        return null;
    }
}
