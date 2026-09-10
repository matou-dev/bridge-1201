package net.minecraftforge.api.distmarker;

/**
 * Custom-entity compile stub: shape-only Forge 1.20.1-47.2.0 API
 * (mergetool-api jar on the installer legacy classpath, never
 * obfuscated). Never runs (compile classpath only). Measured with javap
 * against the provisioned mergetool jar (enum with {@code CLIENT} +
 * {@code DEDICATED_SERVER} — no bare {@code SERVER} on this version,
 * same split as the 1165 forgespi finding). Only the dist side the
 * renderer subscriber names is pinned — drift fails loudly.
 */
public enum Dist {
    CLIENT, DEDICATED_SERVER
}
