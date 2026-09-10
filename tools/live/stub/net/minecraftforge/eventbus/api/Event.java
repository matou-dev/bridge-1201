package net.minecraftforge.eventbus.api;

/**
 * D1 compile stub: shape-only eventbus 6.0.x API (never obfuscated). Never
 * runs (compile classpath only). Presence is pinned by tools/run-live.sh
 * (D3) -- drift fails loudly.
 *
 * <p>Spawn shape (hub decisions/SPAWN.md): the past-cap veto cancels the
 * join through {@code setCanceled} (the join event is {@code @Cancelable}
 * on 47.2.0 -- measured via javap on the pinned universal, never
 * assumed). Pinned by tools/run-live.sh (library pin) -- drift fails
 * loudly.
 */
public class Event {
    public void setCanceled(boolean canceled) {
    }
}
