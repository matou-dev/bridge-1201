package net.minecraft.world.entity.item;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Loot compile stub: shape-only 1.20.1 (Mojmap) vanilla API used by
 * {@code forge/} sources. Never runs (compile classpath only). Ctor
 * measured via javap against the pinned 47.2.0 bytes (public
 * {@code (Level,double,double,double,ItemStack)} — ctors are never
 * obfuscated, so no narrow-map row). The forge side lands one carrier
 * per due drop through this type.
 *
 * <p>Companion shape (loot proof, DEV ONLY): the autoplay companion polls
 * carriers through {@code getItem} (measured via server.txt +
 * joined.tsrg v2 + javap, same practice). Pinned by the AUTOPLAY derive
 * in hub tools/run-client.sh (want.txt) — drift fails loudly.
 */
public class ItemEntity extends Entity {
    public ItemEntity(Level level, double x, double y, double z,
            ItemStack stack) {
    }

    public ItemStack getItem() {
        return null;
    }
}
