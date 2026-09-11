package org.lwjgl.opengl;

import java.nio.FloatBuffer;

/**
 * Shape-only compile stub for LWJGL 3 GL15C (1.16.5 era).
 * Never runs (compile classpath only). Same surface as the 1122 GL15
 * stub, core-class spelling.
 */
public class GL15C {
    public static int GL_ARRAY_BUFFER;
    public static int GL_STATIC_DRAW;
    public static int GL_DYNAMIC_DRAW;
    public static int GL_STREAM_DRAW;

    public static int glGenBuffers() {
        return 0;
    }

    public static void glBindBuffer(int target, int buffer) {}

    public static void glBufferData(int target, FloatBuffer data, int usage) {}

    public static void glDeleteBuffers(int buffer) {}
}
