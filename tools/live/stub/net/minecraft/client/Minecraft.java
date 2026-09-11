package net.minecraft.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

/**
 * Shape-only compile stub for Minecraft 1.20.1 client.
 * Never runs (compile classpath only). The single Minecraft stub — the
 * former second one under tools/autoplay/stub is merged here (same
 * duplicate-class trouvaille as 1122/1165/1710: two stubs, one class,
 * javac refuses). Two consumers share it: the client-only forge renderer
 * needs getInstance/level/getCameraEntity (first executed by the direct
 * client proof, loud at runtime until then), the dev-only autoplay
 * companion needs getInstance/stop (pinned by the AUTOPLAY derive,
 * tools/autoplay/want.txt).
 *
 * <p>Renderer anchors are narrow-map-derived, never recalled (same
 * derive discipline as every 1201 row): {@code getInstance} is
 * {@code m_91087_} (static, measured {@code ()Lenn} on the pinned
 * vanilla client jar), {@code level} is the ClientLevel-typed
 * {@code f_91073_} (measured {@code Lfew} — a Level-typed fieldref
 * would die linking: the JVM matches field descriptors exactly),
 * {@code getCameraEntity} is {@code m_91288_} (measured
 * {@code ()Lbfj} — Entity-typed, no Camera reads). All pinned by
 * tools/run-live.sh.
 */
public class Minecraft {
    // True type is ClientLevel — the renderer reads getEntitiesOfClass
    // through the EntityGetter interface (owner discipline: stubs never
    // ship, a Level-owned ref walks nowhere in Reobf and passes through
    // to die linking live).
    public ClientLevel level;

    public static Minecraft getInstance() {
        return null;
    }

    public Entity getCameraEntity() {
        return null;
    }

    public void stop() {
    }
}
