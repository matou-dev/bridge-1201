package net.minecraftforge.registries;

import net.minecraft.resources.ResourceLocation;

/**
 * D1 compile stub: shape-only Forge 1.20.1-47.2.0 API (universal jar, never
 * obfuscated). Never runs (compile classpath only). Member
 * {@code getValue} is pinned by tools/run-live.sh (D3) — drift fails
 * loudly. {@code containsKey} is the only presence probe: {@code getValue}
 * returns the registry default (air for blocks) for unknown names, never
 * null — a null check would cry DUP on a correct config and resolve typos
 * to air silently (found live on 1165, same registry semantics here,
 * verified live).
 */
public interface IForgeRegistry<V> {
    V getValue(ResourceLocation name);

    boolean containsKey(ResourceLocation name);
}
