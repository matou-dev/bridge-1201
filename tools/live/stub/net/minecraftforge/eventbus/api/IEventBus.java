package net.minecraftforge.eventbus.api;

import java.util.function.Consumer;

/**
 * D1 compile stub: shape-only eventbus 6.0.x API (never obfuscated). Never
 * runs (compile classpath only). Member {@code register} is pinned by
 * tools/run-live.sh (D3) — drift fails loudly. {@code addListener} is the
 * setup surface (see hub decisions/REGISTRATION.md) — pinned by the live
 * tranche, never widened here beyond what {@code forge/} references.
 */
public interface IEventBus {
    void register(Object obj);

    <T extends Event> void addListener(Consumer<T> handler);
}
