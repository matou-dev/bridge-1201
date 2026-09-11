package com.mojang.blaze3d.vertex;

/**
 * Shape-only compile stub for the 1.20.1 client PoseStack (Mojang
 * class, SRG names at runtime like every Mojmap-era member).
 * Never runs (compile classpath only). Only the surface the renderer
 * uploads through: {@code last} is {@code m_85850_} (public
 * {@code ()Leij$a}, measured via server.txt + joined.tsrg v2 + javap
 * on the pinned vanilla client jar — obf {@code eij}), and the nested
 * Pose compiles to the runtime binary name {@code PoseStack$Pose}
 * (obf {@code eij$a}, {@code pose} is {@code m_252922_} returning the
 * org.joml view matrix). Pinned by tools/run-live.sh (narrow map) —
 * drift fails loudly.
 */
public class PoseStack {
    public Pose last() {
        return null;
    }

    public static final class Pose {
        public org.joml.Matrix4f pose() {
            return null;
        }
    }
}
