package net.minecraft.world.phys;

/**
 * Loot-proof compile stub (DEV ONLY): shape-only 1.20.1 (Mojmap) vanilla
 * API used by the autoplay companion (tools/autoplay/, never shipped).
 * Never runs (compile classpath only). Only a volume token: the companion
 * polls carriers through {@code EntityGetter.getEntitiesOfClass} over one
 * box per loot spot (measured via server.txt: public 6-double ctor —
 * ctors are never obfuscated, so no narrow-map row).
 */
public class AABB {
    public AABB(double x1, double y1, double z1, double x2, double y2,
            double z2) {
    }
}
