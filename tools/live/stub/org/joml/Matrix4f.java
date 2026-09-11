package org.joml;

import java.nio.FloatBuffer;

/**
 * Shape-only compile stub for the JOML Matrix4f (1.20.1 ships the view
 * and projection matrices as org.joml through the frame event).
 * Never runs (compile classpath only). Library class, never obfuscated:
 * no narrow-map row, presence is the pin (measured
 * {@code get(FloatBuffer)} on the provisioned joml 1.10.5 bytes — an
 * absolute 16-float write at the current position that never moves it,
 * so the renderer clear()s then get()s with no flip()).
 */
public class Matrix4f {
    public FloatBuffer get(FloatBuffer buf) {
        return null;
    }
}
