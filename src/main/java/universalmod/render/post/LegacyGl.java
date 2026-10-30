package universalmod.render.post;

import com.mojang.blaze3d.systems.RenderSystem;
import universalmod.render.GlUiState;

import java.nio.FloatBuffer;

import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

public final class LegacyGl {
    private static final int MAX_TRACKED_TEXTURE_UNITS = 16;
    private static final int[] savedTexture2D = new int[MAX_TRACKED_TEXTURE_UNITS];
    private static final boolean[] savedTextureUnit = new boolean[MAX_TRACKED_TEXTURE_UNITS];
    private static int savedActiveTexture = GL13.GL_TEXTURE0;
    private static boolean textureScopeActive;
    private static int vao;

    private LegacyGl() {
    }

    public static int program(String vertex, String fragment, String label) {
        int vs = compile(GL20.GL_VERTEX_SHADER, vertex, label + ":vertex");
        int fs = compile(GL20.GL_FRAGMENT_SHADER, fragment, label + ":fragment");
        int p = GL20.glCreateProgram();
        GL20.glAttachShader(p, vs);
        GL20.glAttachShader(p, fs);
        GL20.glBindAttribLocation(p, 0, "Position");
        GL20.glLinkProgram(p);
        if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(p);
            GL20.glDeleteShader(vs);
            GL20.glDeleteShader(fs);
            GL20.glDeleteProgram(p);
            throw new IllegalStateException(label + " link failed: " + log);
        }
        GL20.glDetachShader(p, vs);
        GL20.glDetachShader(p, fs);
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        return p;
    }

    private static int compile(int type, String source, String label) {
        int s = GL20.glCreateShader(type);
        GL20.glShaderSource(s, source);
        GL20.glCompileShader(s);
        if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(s);
            GL20.glDeleteShader(s);
            throw new IllegalStateException(label + " compile failed: " + log);
        }
        return s;
    }

    public static SimpleFramebuffer ensure(SimpleFramebuffer fbo, int w, int h, boolean depth) {
        w = Math.max(1, w);
        h = Math.max(1, h);
        if (fbo == null) fbo = new SimpleFramebuffer(w, h, depth, false);
        else if (fbo.textureWidth != w || fbo.textureHeight != h) fbo.resize(w, h, false);
        fbo.setTexFilter(GL11.GL_LINEAR);
        return fbo;
    }

    public static void copy(Framebuffer src, Framebuffer dst, boolean depth, boolean linear) {
        int mask = GL11.GL_COLOR_BUFFER_BIT | (depth ? GL11.GL_DEPTH_BUFFER_BIT : 0);
        int oldRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int oldDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, src.fbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, dst.fbo);
            GL30.glBlitFramebuffer(0, 0, src.textureWidth, src.textureHeight,
                    0, 0, dst.textureWidth, dst.textureHeight, mask,
                    linear ? GL11.GL_LINEAR : GL11.GL_NEAREST);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, oldRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, oldDraw);
        }
    }

    public static void drawFullscreen(int program, Framebuffer target) {
        drawFullscreen(program, target, true);
    }

    public static void drawFullscreen(int program, Framebuffer target, boolean blend) {
        boolean oldDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean oldCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean oldBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean oldDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int oldProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int oldVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int oldSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int oldDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int oldSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int oldDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        GlUiState savedState = GlUiState.capture();
        try {
            if (target != null) {
                target.beginWrite(false);
                GL11.glViewport(0, 0, target.textureWidth, target.textureHeight);
            }
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            if (blend) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
            } else RenderSystem.disableBlend();
            GL20.glUseProgram(program);
            ensureVao();
            GL30.glBindVertexArray(vao);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        } finally {
            GL30.glBindVertexArray(oldVao);
            GL20.glUseProgram(oldProgram);
            RenderSystem.depthMask(oldDepthMask);
            if (oldDepth) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
            if (oldCull) RenderSystem.enableCull();
            else RenderSystem.disableCull();
            if (oldBlend) RenderSystem.enableBlend();
            else RenderSystem.disableBlend();
            RenderSystem.blendFuncSeparate(oldSrcRgb, oldDstRgb, oldSrcAlpha, oldDstAlpha);
            savedState.close();
        }
    }

    public static void bindTexture(int unit, int texture) {
        if (unit < 0 || unit >= MAX_TRACKED_TEXTURE_UNITS)
            throw new IllegalArgumentException("Texture unit out of tracked range: " + unit);
        if (!textureScopeActive) {
            savedActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            textureScopeActive = true;
        }
        RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit);
        if (!savedTextureUnit[unit]) {
            savedTexture2D[unit] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            savedTextureUnit[unit] = true;
        }
        RenderSystem.bindTexture(texture);
    }

    public static void unbindTextures(int count) {
        if (!textureScopeActive) return;
        for (int i = 0; i < MAX_TRACKED_TEXTURE_UNITS; i++) {
            if (!savedTextureUnit[i]) continue;
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + i);
            RenderSystem.bindTexture(savedTexture2D[i]);
            savedTextureUnit[i] = false;
            savedTexture2D[i] = 0;
        }
        RenderSystem.activeTexture(savedActiveTexture);
        textureScopeActive = false;
    }

    public static void i(int p, String n, int v) {
        int l = GL20.glGetUniformLocation(p, n);
        if (l >= 0) GL20.glUniform1i(l, v);
    }

    public static void f(int p, String n, float v) {
        int l = GL20.glGetUniformLocation(p, n);
        if (l >= 0) GL20.glUniform1f(l, v);
    }

    public static void f2(int p, String n, float a, float b) {
        int l = GL20.glGetUniformLocation(p, n);
        if (l >= 0) GL20.glUniform2f(l, a, b);
    }

    public static void f3(int p, String n, float a, float b, float c) {
        int l = GL20.glGetUniformLocation(p, n);
        if (l >= 0) GL20.glUniform3f(l, a, b, c);
    }

    public static void f4(int p, String n, float a, float b, float c, float d) {
        int l = GL20.glGetUniformLocation(p, n);
        if (l >= 0) GL20.glUniform4f(l, a, b, c, d);
    }

    public static void m4(int p, String n, Matrix4f value) {
        int l = GL20.glGetUniformLocation(p, n);
        if (l < 0 || value == null) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer data = stack.mallocFloat(16);
            value.get(data);
            GL20.glUniformMatrix4fv(l, false, data);
        }
    }

    public static void delete(SimpleFramebuffer f) {
        if (f != null) try {
            f.delete();
        } catch (Throwable ignored) {
        }
    }

    public static void deleteProgram(int p) {
        if (p != 0) GL20.glDeleteProgram(p);
    }

    private static void ensureVao() {
        if (vao == 0) vao = GL30.glGenVertexArrays();
    }

    public static final String FULLSCREEN_VS = """
            #version 150
            out vec2 vUv;
            void main(){
            vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));
            vUv=p;
            gl_Position=vec4(p*2.0-1.0,0.0,1.0);
            }
            """;
}
