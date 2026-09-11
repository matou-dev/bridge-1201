package fr.iamacat.bridge.forge;

import fr.iamacat.spi.render.GlBackend;
import java.nio.FloatBuffer;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.opengl.GL33C;

/**
 * LWJGL 3 driver implementing the pure SPI GlBackend instancing contract.
 * Targets OpenGL 3.1+ instancing primitives on 1.16.5 / 1.20.1 (Core-ready
 * profile): same call-for-call shape as the LWJGL 2 driver on 1.7.10 /
 * 1.12.2, only the binding classes change ({@code GL*C}, hub
 * decisions/GL_INSTANCING_ADAPTER.md). Zero Minecraft imports, pure
 * LWJGL3 calls.
 */
public class Lwjgl3Backend implements GlBackend {

    @Override
    public int genBuffers() {
        return GL15C.glGenBuffers();
    }

    @Override
    public void bindBuffer(int target, int buffer) {
        GL15C.glBindBuffer(target, buffer);
    }

    @Override
    public void bufferData(int target, FloatBuffer data, int usage) {
        if (data == null) {
            throw new NullPointerException("E_GL_BUFFER:null");
        }
        GL15C.glBufferData(target, data, usage);
    }

    @Override
    public void deleteBuffers(int buffer) {
        GL15C.glDeleteBuffers(buffer);
    }

    @Override
    public int genVertexArrays() {
        return GL30C.glGenVertexArrays();
    }

    @Override
    public void bindVertexArray(int array) {
        GL30C.glBindVertexArray(array);
    }

    @Override
    public void deleteVertexArrays(int array) {
        GL30C.glDeleteVertexArrays(array);
    }

    @Override
    public void enableVertexAttribArray(int index) {
        GL20C.glEnableVertexAttribArray(index);
    }

    @Override
    public void disableVertexAttribArray(int index) {
        GL20C.glDisableVertexAttribArray(index);
    }

    @Override
    public void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long offset) {
        GL20C.glVertexAttribPointer(index, size, type, normalized, stride, offset);
    }

    @Override
    public void vertexAttribDivisor(int index, int divisor) {
        GL33C.glVertexAttribDivisor(index, divisor);
    }

    @Override
    public int createShader(int type) {
        return GL20C.glCreateShader(type);
    }

    @Override
    public void shaderSource(int shader, String source) {
        GL20C.glShaderSource(shader, source);
    }

    @Override
    public void compileShader(int shader) {
        GL20C.glCompileShader(shader);
    }

    @Override
    public boolean getShaderCompileStatus(int shader) {
        return GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) == GL11C.GL_TRUE;
    }

    @Override
    public String getShaderInfoLog(int shader) {
        int len = GL20C.glGetShaderi(shader, GL20C.GL_INFO_LOG_LENGTH);
        return GL20C.glGetShaderInfoLog(shader, len > 0 ? len : 1024);
    }

    @Override
    public void deleteShader(int shader) {
        GL20C.glDeleteShader(shader);
    }

    @Override
    public int createProgram() {
        return GL20C.glCreateProgram();
    }

    @Override
    public void attachShader(int program, int shader) {
        GL20C.glAttachShader(program, shader);
    }

    @Override
    public void linkProgram(int program) {
        GL20C.glLinkProgram(program);
    }

    @Override
    public boolean getProgramLinkStatus(int program) {
        return GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == GL11C.GL_TRUE;
    }

    @Override
    public String getProgramInfoLog(int program) {
        int len = GL20C.glGetProgrami(program, GL20C.GL_INFO_LOG_LENGTH);
        return GL20C.glGetProgramInfoLog(program, len > 0 ? len : 1024);
    }

    @Override
    public void useProgram(int program) {
        GL20C.glUseProgram(program);
    }

    @Override
    public void deleteProgram(int program) {
        GL20C.glDeleteProgram(program);
    }

    @Override
    public int getUniformLocation(int program, String name) {
        return GL20C.glGetUniformLocation(program, name);
    }

    @Override
    public void uniformMatrix4fv(int location, boolean transpose, FloatBuffer matrices) {
        // Measured on the provisioned LWJGL 3.3.1 bytes (javap GL20C on
        // the 1.20.1 client libraries — the full *C surface re-verified
        // the same way, all other names hold): the vector form is
        // glUniformMatrix4fv — the LWJGL2-era glUniformMatrix4 name does
        // not exist here (found live as NoSuchMethodError on the first
        // 1165 draw, never recalled again).
        GL20C.glUniformMatrix4fv(location, transpose, matrices);
    }

    @Override
    public void uniform1i(int location, int value) {
        GL20C.glUniform1i(location, value);
    }

    @Override
    public void uniform1f(int location, float value) {
        GL20C.glUniform1f(location, value);
    }

    @Override
    public void uniform4f(int location, float x, float y, float z, float w) {
        GL20C.glUniform4f(location, x, y, z, w);
    }

    @Override
    public void drawArraysInstanced(int mode, int first, int count, int instanceCount) {
        GL31C.glDrawArraysInstanced(mode, first, count, instanceCount);
    }
}
