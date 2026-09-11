package net.minecraftforge.client.event;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraftforge.eventbus.api.Event;

/**
 * Shape-only compile stub for Forge 1.20.1 RenderLevelStageEvent.
 * Never runs (compile classpath only). Forge class, never obfuscated:
 * presence-pinned against the provisioned 47.2.0 universal by
 * tools/run-live.sh (getStage/getPoseStack/getProjectionMatrix/
 * getPartialTick measured there — the 1.16.5 RenderWorldLastEvent does
 * not exist on 1.20.1; partial ticks ride getPartialTick with no
 * trailing s, and both matrices ride the event directly as org.joml).
 */
public class RenderLevelStageEvent extends Event {
    public Stage getStage() {
        return null;
    }

    public PoseStack getPoseStack() {
        return null;
    }

    public org.joml.Matrix4f getProjectionMatrix() {
        return null;
    }

    public float getPartialTick() {
        return 0;
    }

    public static final class Stage {
        public static final Stage AFTER_ENTITIES = new Stage();
    }
}
