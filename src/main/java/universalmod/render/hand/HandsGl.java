package universalmod.render.hand;

import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;

final class HandsGl {
    static final int NO_COLOR = 0;
    static final int R8 = GL30.GL_R8;
    static final int RGBA8 = GL11.GL_RGBA8;
    private static final int MAX_TEXTURE_UNITS = 8;
    private static int vao;

    private HandsGl() {
    }

    static int program(String vertexPath, String fragmentPath, String label) {
        int vertex = compile(GL20.GL_VERTEX_SHADER, source(vertexPath), label + " vertex");
        int fragment = compile(GL20.GL_FRAGMENT_SHADER, source(fragmentPath), label + " fragment");
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertex);
        GL20.glAttachShader(program, fragment);
        GL20.glLinkProgram(program);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String message = GL20.glGetProgramInfoLog(program);
            GL20.glDeleteShader(vertex);
            GL20.glDeleteShader(fragment);
            GL20.glDeleteProgram(program);
            throw new IllegalStateException(label + " link failed " + message);
        }
        GL20.glDetachShader(program, vertex);
        GL20.glDetachShader(program, fragment);
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
        return program;
    }

    private static int compile(int type, String source, String label) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String message = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException(label + " compile failed " + message);
        }
        return shader;
    }

    private static String source(String path) {
        try (InputStream input = HandsGl.class.getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Missing shader resource " + path);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read shader resource " + path, exception);
        }
    }

    static Target target(Target current, int width, int height, int colorFormat, boolean depth, int filter) {
        width = Math.max(1, width);
        height = Math.max(1, height);
        if (current != null && current.matches(width, height, colorFormat, depth, filter)) return current;
        delete(current);
        return new Target(width, height, colorFormat, depth, filter);
    }

    static void copy(Framebuffer source, Target target, boolean color, boolean depth) {
        if (source == null || target == null) return;
        int mask = 0;
        if (color && target.colorTexture > 0) mask |= GL11.GL_COLOR_BUFFER_BIT;
        if (depth && target.depthTexture > 0) mask |= GL11.GL_DEPTH_BUFFER_BIT;
        if (mask == 0) return;
        int oldRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int oldDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        if (scissor) GL11.glDisable(GL11.GL_SCISSOR_TEST);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.fbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.fbo);
            GL30.glBlitFramebuffer(0, 0, source.textureWidth, source.textureHeight, 0, 0, target.width, target.height, mask, GL11.GL_NEAREST);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, oldRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, oldDraw);
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
        }
    }

    static void prepareTarget(Framebuffer target, boolean clear, boolean blend) {
        target.beginWrite(false);
        prepare(target.textureWidth, target.textureHeight, clear, blend);
    }

    static void prepareTarget(Target target, boolean clear, boolean blend) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, target.fbo);
        prepare(target.width, target.height, clear, blend);
    }

    private static void prepare(int width, int height, boolean clear, boolean blend) {
        GL11.glViewport(0, 0, width, height);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glColorMask(true, true, true, true);
        if (clear) {
            GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        }
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
        if (blend) {
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        } else {
            GL11.glDisable(GL11.GL_BLEND);
        }
    }

    static void drawFullscreen() {
        if (vao == 0) vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }


    static void filterTexture(int texture, int filter) {
        if (texture <= 0) return;
        int oldActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int oldTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, oldTexture);
            GL13.glActiveTexture(oldActive);
        }
    }

    static void texture(int unit, int texture) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    static void i(int program, String name, int value) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location >= 0) GL20.glUniform1i(location, value);
    }

    static void f4(int program, String name, float x, float y, float z, float w) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location >= 0) GL20.glUniform4f(location, x, y, z, w);
    }

    static State state(int textureUnits) {
        return new State(textureUnits);
    }

    static void delete(Target target) {
        if (target != null) target.delete();
    }

    static void deleteProgram(int program) {
        if (program != 0) GL20.glDeleteProgram(program);
    }

    static final class Target {
        final int width;
        final int height;
        final int colorFormat;
        final boolean depth;
        final int filter;
        int fbo;
        int colorTexture;
        int depthTexture;

        Target(int width, int height, int colorFormat, boolean depth, int filter) {
            this.width = width;
            this.height = height;
            this.colorFormat = colorFormat;
            this.depth = depth;
            this.filter = filter;
            create();
        }

        boolean matches(int width, int height, int colorFormat, boolean depth, int filter) {
            return fbo != 0 && this.width == width && this.height == height && this.colorFormat == colorFormat && this.depth == depth && this.filter == filter;
        }

        private void create() {
            int oldRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int oldDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            int oldActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            int oldTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            try {
                fbo = GL30.glGenFramebuffers();
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
                if (colorFormat != NO_COLOR) {
                    colorTexture = createColorTexture(width, height, colorFormat, filter);
                    GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorTexture, 0);
                    GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
                    GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
                } else {
                    GL11.glDrawBuffer(GL11.GL_NONE);
                    GL11.glReadBuffer(GL11.GL_NONE);
                }
                if (depth) {
                    depthTexture = createDepthTexture(width, height);
                    GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depthTexture, 0);
                }
                int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
                if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                    delete();
                    throw new IllegalStateException("Hands framebuffer incomplete " + status);
                }
            } finally {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, oldTexture);
                GL13.glActiveTexture(oldActive);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, oldRead);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, oldDraw);
            }
        }

        private static int createColorTexture(int width, int height, int format, int filter) {
            int texture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            int external = format == R8 ? GL11.GL_RED : GL11.GL_RGBA;
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, format, width, height, 0, external, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            return texture;
        }

        private static int createDepthTexture(int width, int height) {
            int texture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT, width, height, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (ByteBuffer) null);
            return texture;
        }

        void delete() {
            if (colorTexture != 0) GL11.glDeleteTextures(colorTexture);
            if (depthTexture != 0) GL11.glDeleteTextures(depthTexture);
            if (fbo != 0) GL30.glDeleteFramebuffers(fbo);
            colorTexture = 0;
            depthTexture = 0;
            fbo = 0;
        }
    }

    static final class State implements AutoCloseable {
        private final int program;
        private final int activeTexture;
        private final int readFramebuffer;
        private final int drawFramebuffer;
        private final int vaoBinding;
        private final int arrayBufferBinding;
        private final int elementArrayBufferBinding;
        private final int[] viewport = new int[4];
        private final float[] clearColor = new float[4];
        private final boolean depth;
        private final boolean cull;
        private final boolean blend;
        private final boolean scissor;
        private final boolean stencil;
        private final boolean depthMask;
        private final boolean[] colorMask = new boolean[4];
        private final int srcRgb;
        private final int dstRgb;
        private final int srcAlpha;
        private final int dstAlpha;
        private final int[] textures;
        private boolean closed;

        State(int textureUnits) {
            program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            vaoBinding = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            arrayBufferBinding = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            elementArrayBufferBinding = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
            IntBuffer viewportBuffer = BufferUtils.createIntBuffer(4);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewportBuffer);
            for (int index = 0; index < 4; index++) viewport[index] = viewportBuffer.get(index);
            FloatBuffer clearBuffer = BufferUtils.createFloatBuffer(4);
            GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearBuffer);
            for (int index = 0; index < 4; index++) clearColor[index] = clearBuffer.get(index);
            depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            blend = GL11.glIsEnabled(GL11.GL_BLEND);
            scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
            depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            ByteBuffer colorMaskBuffer = BufferUtils.createByteBuffer(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMaskBuffer);
            for (int index = 0; index < 4; index++) colorMask[index] = colorMaskBuffer.get(index) != 0;
            srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
            dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
            srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
            dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
            int count = Math.max(1, Math.min(MAX_TEXTURE_UNITS, textureUnits));
            textures = new int[count];
            for (int unit = 0; unit < count; unit++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                textures[unit] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            }
            GL13.glActiveTexture(activeTexture);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            GL20.glUseProgram(program);
            for (int unit = 0; unit < textures.length; unit++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures[unit]);
            }
            GL13.glActiveTexture(activeTexture);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GL30.glBindVertexArray(vaoBinding);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBufferBinding);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, elementArrayBufferBinding);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            GL11.glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
            GL11.glDepthMask(depthMask);
            if (depth) GL11.glEnable(GL11.GL_DEPTH_TEST); else GL11.glDisable(GL11.GL_DEPTH_TEST);
            if (cull) GL11.glEnable(GL11.GL_CULL_FACE); else GL11.glDisable(GL11.GL_CULL_FACE);
            if (blend) GL11.glEnable(GL11.GL_BLEND); else GL11.glDisable(GL11.GL_BLEND);
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
            if (stencil) GL11.glEnable(GL11.GL_STENCIL_TEST); else GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
            GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        }
    }
}
