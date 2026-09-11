package org.lwjgl.opengl;

/**
 * Shape-only compile stub for LWJGL 3 GL11C (1.16.5 era).
 * Never runs (compile classpath only). Same role as the 1122 GL11 stub
 * minus the fixed-function matrix reads: the 1165 renderer takes its
 * view/projection matrices from the RenderWorldLastEvent MatrixStack
 * (hub decisions/GL_INSTANCING_ADAPTER.md), never glGetFloat.
 */
public class GL11C {
    public static int GL_TRUE;
    public static int GL_DEPTH_TEST;
    public static int GL_CULL_FACE;
    public static int GL_BACK;
    // Measured on the provisioned LWJGL 3.2.2 bytes (javap:
    // public static int glGetError(), GL_NO_ERROR) — the draw-proof
    // tripwire in InstancedMeshRenderer judges its own draw, never MC's.
    public static int GL_NO_ERROR;

    public static int glGetError() { return 0; }

    public static void glEnable(int cap) {}
    public static void glDepthMask(boolean flag) {}
    public static void glCullFace(int mode) {}
}
