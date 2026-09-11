package net.minecraft.client.multiplayer;

/**
 * Shape-only compile stub for the 1.20.1 client level.
 * Never runs (compile classpath only). Pure type token: the renderer's
 * {@code Minecraft.level} field must carry the true runtime type
 * (ClientLevel — a Level-typed fieldref would die linking, the JVM
 * matches field descriptors exactly), while iteration rides the
 * already-mapped {@code EntityGetter.getEntitiesOfClass} interface, so
 * no ClientLevel member is ever named and the narrow map gains no
 * client-level row for it. Pinned by tools/run-live.sh (the level field
 * row) — drift fails loudly.
 */
public class ClientLevel {
}
