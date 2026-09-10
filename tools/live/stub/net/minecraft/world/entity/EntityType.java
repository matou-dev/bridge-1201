package net.minecraft.world.entity;

import net.minecraft.world.entity.animal.Pig;

/**
 * Spawn compile stub: shape-only 1.20.1 (Mojmap) vanilla API used by
 * {@code forge/} sources. Never runs (compile classpath only). Only a
 * type token: the T1 pig type goes through this (measured via
 * server.txt: {@code PIG} is a public static field -- ctors take it, so
 * no narrow-map row for construction; the field itself is pinned).
 * Non-final by design (registry ref, Items.DIAMOND precedent). Pinned by
 * tools/run-live.sh (narrow map) -- drift fails loudly.
 */
public class EntityType<T extends Entity> {
    public static EntityType<Pig> PIG;
}
