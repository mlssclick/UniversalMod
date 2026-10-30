package universalmod.render.winter;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL33;
import universalmod.module.render.Atmosphere;
import universalmod.render.atmosphere.AtmosphereSunProjection;
import universalmod.render.atmosphere.WeatherUniformLayout;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;

public final class WinterWorldRenderer {
    private static final String SHADERS = "/assets/universalmod/shaders/post/winter/";
    private static final int WEATHER_BYTES = WeatherUniformLayout.TOTAL_BYTES;
    private static final int BLUR_BYTES = 16;
    private static final int PASS_COUNT = 5;

    private static final int SURFACE = 0, FLAKES = 1, BLUR = 2, COMPOSITE = 3, CORE = 4;
    private static final int[] programs = new int[PASS_COUNT];
    private static int vao;
    private static int surfaceData, snowfallData, blurHData, blurVData;
    private static int sceneFbo, sceneColor, sceneDepth;
    private static int flakeFbo, flakesColor, pingFbo, pingColor, pongFbo, pongColor;
    private static int width = -1, height = -1;
    private static long startedMillis = -1;
    private static long retryAfterMillis;

    private WinterWorldRenderer() { }

    public static void render(Atmosphere module, Camera camera, Matrix4f view,
                              Matrix4f projection, float partialTick) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (module == null || !module.isWinterActive() || client.world == null || client.player == null
                || camera == null || !camera.isReady() || view == null || projection == null
                || System.currentTimeMillis() < retryAfterMillis) return;
        Framebuffer main = client.getFramebuffer();
        if (main == null || main.fbo <= 0 || main.getColorAttachment() <= 0 || main.getDepthAttachment() <= 0
                || main.viewportWidth < 1 || main.viewportHeight < 1) return;

        GlState previous = new GlState();
        try {
            int w = main.viewportWidth, h = main.viewportHeight;
            initialize();
            ensureTargets(w, h);
            if (startedMillis < 0L) startedMillis = System.currentTimeMillis();
            float time = (System.currentTimeMillis() - startedMillis) * 0.001f;
            Vec3d cameraPos = camera.getPos();
            Matrix4f inverseViewProjection = new Matrix4f(projection).mul(view).invert();
            int sky = client.world.getSkyColor(cameraPos, partialTick);
            float skyR = ((sky >>> 16) & 255) / 255f;
            float skyG = ((sky >>> 8) & 255) / 255f;
            float skyB = (sky & 255) / 255f;
            AtmosphereSunProjection.Sun sun = AtmosphereSunProjection.sample(camera, partialTick, view, projection);

            GL33.glBindFramebuffer(GL33.GL_READ_FRAMEBUFFER, main.fbo);
            GL33.glBindFramebuffer(GL33.GL_DRAW_FRAMEBUFFER, sceneFbo);
            GL33.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h,
                    GL33.GL_COLOR_BUFFER_BIT, GL33.GL_NEAREST);

            GL33.glBindFramebuffer(GL33.GL_READ_FRAMEBUFFER, main.fbo);
            GL33.glBindFramebuffer(GL33.GL_DRAW_FRAMEBUFFER, 0);
            GL33.glActiveTexture(GL33.GL_TEXTURE0);
            GL33.glBindTexture(GL33.GL_TEXTURE_2D, sceneDepth);
            GL33.glCopyTexSubImage2D(GL33.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);

            GL33.glDisable(GL33.GL_DEPTH_TEST);
            GL33.glDisable(GL33.GL_CULL_FACE);
            GL33.glDisable(GL33.GL_SCISSOR_TEST);
            GL33.glDisable(GL33.GL_STENCIL_TEST);
            GL33.glDepthMask(false);
            GL33.glColorMask(true, true, true, true);
            GL33.glDisable(GL33.GL_BLEND);
            GL33.glBindVertexArray(vao);

            updateWeather(surfaceData, inverseViewProjection, cameraPos, projection, w, h,
                    module, time, sun, skyR, skyG, skyB, false);
            draw(SURFACE, main.fbo, w, h, surfaceData, sceneColor, sceneDepth, sceneColor);

            if (module.isSnowflakesEnabled() && module.getSnowAmount() > 0.0f) {

                updateWeather(snowfallData, inverseViewProjection, cameraPos, projection, w, h,
                        module, time, sun, skyR, skyG, skyB, true);
                draw(FLAKES, flakeFbo, w, h, snowfallData, sceneColor, sceneDepth, sceneColor);

                uploadBlur(blurHData, 3.2f);
                draw(BLUR, pingFbo, Math.max(1, w / 2), Math.max(1, h / 2),
                        blurHData, flakesColor, 0, 0);
                uploadBlur(blurVData, -1.6f);
                draw(BLUR, pongFbo, Math.max(1, w / 2), Math.max(1, h / 2),
                        blurVData, pingColor, 0, 0);

                GL33.glEnable(GL33.GL_BLEND);
                GL33.glBlendEquation(GL33.GL_FUNC_ADD);
                GL33.glBlendFuncSeparate(GL33.GL_SRC_ALPHA, GL33.GL_ONE, GL33.GL_ONE, GL33.GL_ONE);
                draw(COMPOSITE, main.fbo, w, h, 0, pongColor, 0, 0);
                draw(CORE, main.fbo, w, h, 0, flakesColor, 0, 0);
            }
        } catch (RuntimeException | LinkageError error) {

            System.err.println("[UniversalMod] Winter post-render failed: " + error);
            retryAfterMillis = System.currentTimeMillis() + 2000L;
            releaseGpu();
        } finally {
            previous.restore();
        }
    }

    private static void initialize() {
        if (programs[SURFACE] != 0) return;
        String vertex = resource("winter.vsh");
        programs[SURFACE] = program(vertex, resource("winter_particles.fsh"), "WinterWeatherData");

        programs[FLAKES] = program(vertex, resource("winter_particles.fsh"), "WinterWeatherData");
        programs[BLUR] = program(vertex, resource("winter_blur.fsh"), "WinterBlurData");
        programs[COMPOSITE] = program(vertex, resource("winter_composite.fsh"), null);
        programs[CORE] = program(vertex, resource("winter_core.fsh"), null);
        vao = GL33.glGenVertexArrays();
        surfaceData = buffer(WEATHER_BYTES);
        snowfallData = buffer(WEATHER_BYTES);
        blurHData = buffer(BLUR_BYTES);
        blurVData = buffer(BLUR_BYTES);
    }

    private static String resource(String name) {
        try (InputStream stream = WinterWorldRenderer.class.getResourceAsStream(SHADERS + name)) {
            if (stream == null) throw new IllegalStateException("Missing original winter shader: " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read winter shader: " + name, exception);
        }
    }

    private static int compile(int shaderType, String text) {
        int shader = GL33.glCreateShader(shaderType);
        GL33.glShaderSource(shader, text);
        GL33.glCompileShader(shader);
        if (GL33.glGetShaderi(shader, GL33.GL_COMPILE_STATUS) == GL33.GL_FALSE) {
            String log = GL33.glGetShaderInfoLog(shader);
            GL33.glDeleteShader(shader);
            throw new IllegalStateException("Winter GLSL compile error: " + log);
        }
        return shader;
    }

    private static int program(String vertex, String fragment, String uniformBlock) {
        int vs = compile(GL33.GL_VERTEX_SHADER, vertex);
        int fs = compile(GL33.GL_FRAGMENT_SHADER, fragment);
        int p = GL33.glCreateProgram();
        try {
            GL33.glAttachShader(p, vs);
            GL33.glAttachShader(p, fs);
            GL33.glLinkProgram(p);
            if (GL33.glGetProgrami(p, GL33.GL_LINK_STATUS) == GL33.GL_FALSE)
                throw new IllegalStateException("Winter GLSL link error: " + GL33.glGetProgramInfoLog(p));
            GL33.glUseProgram(p);
            for (int n = 0; n < 3; n++) {
                int location = GL33.glGetUniformLocation(p, "Sampler" + n);
                if (location >= 0) GL33.glUniform1i(location, n);
            }
            if (uniformBlock != null) {
                int index = GL33.glGetUniformBlockIndex(p, uniformBlock);
                if (index == GL33.GL_INVALID_INDEX || index < 0)
                    throw new IllegalStateException("Missing std140 block: " + uniformBlock);
                GL33.glUniformBlockBinding(p, index, 0);
            }
            return p;
        } catch (RuntimeException exception) {
            GL33.glDeleteProgram(p);
            throw exception;
        } finally {
            GL33.glDeleteShader(vs);
            GL33.glDeleteShader(fs);
        }
    }

    private static int buffer(int bytes) {
        int id = GL33.glGenBuffers();
        GL33.glBindBuffer(GL33.GL_UNIFORM_BUFFER, id);
        GL33.glBufferData(GL33.GL_UNIFORM_BUFFER, bytes, GL33.GL_DYNAMIC_DRAW);
        return id;
    }

    private static void upload(int buffer, ByteBuffer data) {
        data.position(0);
        GL33.glBindBuffer(GL33.GL_UNIFORM_BUFFER, buffer);
        GL33.glBufferSubData(GL33.GL_UNIFORM_BUFFER, 0, data);
    }

    private static void uploadBlur(int target, float offset) {
        ByteBuffer data = BufferUtils.createByteBuffer(BLUR_BYTES).order(ByteOrder.nativeOrder());
        data.putFloat(0, offset);
        upload(target, data);
    }

    static ByteBuffer weatherUniforms(Matrix4f inverse, Vec3d pos, Matrix4f projection,
                                      int w, int h, Atmosphere module, float time,
                                      AtmosphereSunProjection.Sun sun, float sr, float sg, float sb,
                                      boolean captureParticles) {
        ByteBuffer data = BufferUtils.createByteBuffer(WEATHER_BYTES).order(ByteOrder.nativeOrder());
        inverse.get(0, data);
        WeatherUniformLayout.vec3(data, 1, (float) pos.x, (float) pos.y, (float) pos.z);
        WeatherUniformLayout.vec3(data, 2, w, h, 0.0f);
        WeatherUniformLayout.scalar(data, 3, time);
        WeatherUniformLayout.scalar(data, 4, 2.0f / Math.max(0.01f, Math.abs(projection.m11())) / Math.max(1, h));
        WeatherUniformLayout.vec3(data, 5, sun.x(), sun.y(), sun.z());
        WeatherUniformLayout.vec3(data, 6, sun.u(), sun.v(), sun.onScreen());
        WeatherUniformLayout.scalar(data, 7, Math.max(48.0f, module.getFogDistance()));
        WeatherUniformLayout.scalar(data, 8, (float) pos.y - 7.0f);
        WeatherUniformLayout.scalar(data, 9, 1.0f);
        WeatherUniformLayout.scalar(data, 10, captureParticles ? module.getSnowAmount() : 0.0f);
        WeatherUniformLayout.scalar(data, 11, module.getSnowflakeSpeed());

        WeatherUniformLayout.scalar(data, 16, captureParticles ? 0.0f : module.getWinterGodRays());
        WeatherUniformLayout.scalar(data, 20, captureParticles ? 0.0f : 0.16f);
        WeatherUniformLayout.scalar(data, 21, captureParticles ? 0.0f : 0.25f);
        WeatherUniformLayout.scalar(data, 22, captureParticles ? 0.0f : module.getSnowIntensity());
        WeatherUniformLayout.vec3(data, 23, 0.6f, 0.7f, 0.9f);
        WeatherUniformLayout.vec3(data, 24, 0.85f, 0.9f, 1.0f);
        WeatherUniformLayout.vec3(data, 25, 1.0f, 0.94f, 0.79f);
        WeatherUniformLayout.vec3(data, 26, 0.78f, 0.84f, 0.95f);
        WeatherUniformLayout.vec3(data, 27, 0.92f, 0.97f, 1.0f);
        WeatherUniformLayout.vec3(data, 28, 0.75f, 0.83f, 1.0f);
        WeatherUniformLayout.vec3(data, 29, 1.0f, 1.0f, 1.0f);
        WeatherUniformLayout.vec3(data, 30, sun.x(), sun.y(), sun.z());
        WeatherUniformLayout.vec3(data, 31, 1.0f, 1.0f, 1.0f);
        WeatherUniformLayout.vec4(data, 32, 1.0f, 1.0f, 1.0f, 0.0f);
        WeatherUniformLayout.scalar(data, 33, sun.reversedDepth());
        WeatherUniformLayout.scalar(data, 34, captureParticles ? 1.0f : 0.0f);
        data.position(0);
        return data;
    }

    private static void updateWeather(int target, Matrix4f inverse, Vec3d pos, Matrix4f projection,
                                      int w, int h, Atmosphere module, float time,
                                      AtmosphereSunProjection.Sun sun, float sr, float sg, float sb,
                                      boolean captureParticles) {
        upload(target, weatherUniforms(inverse, pos, projection, w, h,
                module, time, sun, sr, sg, sb, captureParticles));
    }

    private static void draw(int pass, int framebuffer, int w, int h,
                             int ubo, int texture0, int texture1, int texture2) {
        GL33.glBindFramebuffer(GL33.GL_FRAMEBUFFER, framebuffer);
        GL33.glViewport(0, 0, w, h);
        GL33.glUseProgram(programs[pass]);
        GL33.glBindBufferBase(GL33.GL_UNIFORM_BUFFER, 0, ubo);
        int[] textures = { texture0, texture1, texture2 };
        for (int i = 0; i < textures.length; i++) {
            GL33.glActiveTexture(GL33.GL_TEXTURE0 + i);
            GL33.glBindTexture(GL33.GL_TEXTURE_2D, textures[i]);
        }
        GL33.glDrawArrays(GL33.GL_TRIANGLES, 0, 6);
    }

    private static int color(int w, int h, boolean linear) {
        int texture = GL33.glGenTextures();
        GL33.glBindTexture(GL33.GL_TEXTURE_2D, texture);
        GL33.glTexImage2D(GL33.GL_TEXTURE_2D, 0, GL33.GL_RGBA8, w, h,
                0, GL33.GL_RGBA, GL33.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL33.glTexParameteri(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MIN_FILTER,
                linear ? GL33.GL_LINEAR : GL33.GL_NEAREST);
        GL33.glTexParameteri(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MAG_FILTER,
                linear ? GL33.GL_LINEAR : GL33.GL_NEAREST);
        GL33.glTexParameteri(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_WRAP_S, GL33.GL_CLAMP_TO_EDGE);
        GL33.glTexParameteri(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_WRAP_T, GL33.GL_CLAMP_TO_EDGE);
        return texture;
    }

    private static int framebuffer(int color, int depth) {
        int fbo = GL33.glGenFramebuffers();
        GL33.glBindFramebuffer(GL33.GL_FRAMEBUFFER, fbo);
        GL33.glFramebufferTexture2D(GL33.GL_FRAMEBUFFER, GL33.GL_COLOR_ATTACHMENT0,
                GL33.GL_TEXTURE_2D, color, 0);
        if (depth > 0) GL33.glFramebufferTexture2D(GL33.GL_FRAMEBUFFER,
                GL33.GL_DEPTH_ATTACHMENT, GL33.GL_TEXTURE_2D, depth, 0);
        if (GL33.glCheckFramebufferStatus(GL33.GL_FRAMEBUFFER) != GL33.GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("Incomplete winter FBO");
        return fbo;
    }

    private static void ensureTargets(int w, int h) {
        if (w == width && h == height && sceneFbo != 0 && flakeFbo != 0) return;
        releaseTargets();
        sceneColor = color(w, h, true);
        sceneDepth = GL33.glGenTextures();
        GL33.glBindTexture(GL33.GL_TEXTURE_2D, sceneDepth);
        GL33.glTexImage2D(GL33.GL_TEXTURE_2D, 0, GL33.GL_DEPTH_COMPONENT24, w, h,
                0, GL33.GL_DEPTH_COMPONENT, GL33.GL_UNSIGNED_INT, (ByteBuffer) null);
        GL33.glTexParameteri(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MIN_FILTER, GL33.GL_NEAREST);
        GL33.glTexParameteri(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MAG_FILTER, GL33.GL_NEAREST);
        GL33.glTexParameteri(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_WRAP_S, GL33.GL_CLAMP_TO_EDGE);
        GL33.glTexParameteri(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_WRAP_T, GL33.GL_CLAMP_TO_EDGE);
        sceneFbo = framebuffer(sceneColor, sceneDepth);
        flakesColor = color(w, h, true);
        flakeFbo = framebuffer(flakesColor, 0);
        pingColor = color(Math.max(1, w / 2), Math.max(1, h / 2), true);
        pingFbo = framebuffer(pingColor, 0);
        pongColor = color(Math.max(1, w / 2), Math.max(1, h / 2), true);
        pongFbo = framebuffer(pongColor, 0);
        width = w;
        height = h;
    }

    public static void resetTime() { startedMillis = -1; }
    public static void reset() { resetTime(); }

    public static void close() {
        releaseGpu();
        retryAfterMillis = 0L;
        resetTime();
    }

    private static void releaseTargets() {
        for (int id : new int[]{sceneFbo, flakeFbo, pingFbo, pongFbo}) if (id != 0) GL33.glDeleteFramebuffers(id);
        for (int id : new int[]{sceneColor, sceneDepth, flakesColor, pingColor, pongColor})
            if (id != 0) GL33.glDeleteTextures(id);
        sceneFbo = flakeFbo = pingFbo = pongFbo = 0;
        sceneColor = sceneDepth = flakesColor = pingColor = pongColor = 0;
        width = height = -1;
    }

    private static void releaseGpu() {
        releaseTargets();
        for (int i = 0; i < programs.length; i++) {
            if (programs[i] != 0) GL33.glDeleteProgram(programs[i]);
            programs[i] = 0;
        }
        for (int id : new int[]{surfaceData, snowfallData, blurHData, blurVData})
            if (id != 0) GL33.glDeleteBuffers(id);
        surfaceData = snowfallData = blurHData = blurVData = 0;
        if (vao != 0) GL33.glDeleteVertexArrays(vao);
        vao = 0;
    }

    private static final class GlState {
        private final int readFbo = GL33.glGetInteger(GL33.GL_READ_FRAMEBUFFER_BINDING);
        private final int drawFbo = GL33.glGetInteger(GL33.GL_DRAW_FRAMEBUFFER_BINDING);
        private final int program = GL33.glGetInteger(GL33.GL_CURRENT_PROGRAM);
        private final int vertexArray = GL33.glGetInteger(GL33.GL_VERTEX_ARRAY_BINDING);
        private final int activeTexture = GL33.glGetInteger(GL33.GL_ACTIVE_TEXTURE);
        private final int uniformBuffer = GL33.glGetInteger(GL33.GL_UNIFORM_BUFFER_BINDING);
        private final int[] bindings = new int[3];
        private final IntBuffer viewport = BufferUtils.createIntBuffer(4);
        private final boolean blend = GL33.glIsEnabled(GL33.GL_BLEND);
        private final boolean depth = GL33.glIsEnabled(GL33.GL_DEPTH_TEST);
        private final boolean cull = GL33.glIsEnabled(GL33.GL_CULL_FACE);
        private final boolean scissor = GL33.glIsEnabled(GL33.GL_SCISSOR_TEST);
        private final boolean stencil = GL33.glIsEnabled(GL33.GL_STENCIL_TEST);
        private final boolean depthWrite = GL33.glGetBoolean(GL33.GL_DEPTH_WRITEMASK);
        private final boolean[] colorWrite = new boolean[4];
        private final int blendEquationRgb = GL33.glGetInteger(GL33.GL_BLEND_EQUATION_RGB);
        private final int blendEquationAlpha = GL33.glGetInteger(GL33.GL_BLEND_EQUATION_ALPHA);
        private final int srcRgb = GL33.glGetInteger(GL33.GL_BLEND_SRC_RGB);
        private final int dstRgb = GL33.glGetInteger(GL33.GL_BLEND_DST_RGB);
        private final int srcAlpha = GL33.glGetInteger(GL33.GL_BLEND_SRC_ALPHA);
        private final int dstAlpha = GL33.glGetInteger(GL33.GL_BLEND_DST_ALPHA);
        private final int uboZero;

        private GlState() {
            GL33.glGetIntegerv(GL33.GL_VIEWPORT, viewport);
            for (int i = 0; i < 3; i++) {
                GL33.glActiveTexture(GL33.GL_TEXTURE0 + i);
                bindings[i] = GL33.glGetInteger(GL33.GL_TEXTURE_BINDING_2D);
            }
            GL33.glActiveTexture(activeTexture);
            ByteBuffer mask = BufferUtils.createByteBuffer(4);
            GL33.glGetBooleanv(GL33.GL_COLOR_WRITEMASK, mask);
            for (int i = 0; i < 4; i++) colorWrite[i] = mask.get(i) != 0;
            uboZero = GL33.glGetIntegeri(GL33.GL_UNIFORM_BUFFER_BINDING, 0);
        }

        private void restore() {
            GL33.glBindFramebuffer(GL33.GL_READ_FRAMEBUFFER, readFbo);
            GL33.glBindFramebuffer(GL33.GL_DRAW_FRAMEBUFFER, drawFbo);
            GL33.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
            GL33.glUseProgram(program);
            GL33.glBindVertexArray(vertexArray);
            GL33.glBindBufferBase(GL33.GL_UNIFORM_BUFFER, 0, uboZero);
            GL33.glBindBuffer(GL33.GL_UNIFORM_BUFFER, uniformBuffer);
            for (int i = 0; i < bindings.length; i++) {
                GL33.glActiveTexture(GL33.GL_TEXTURE0 + i);
                GL33.glBindTexture(GL33.GL_TEXTURE_2D, bindings[i]);
            }
            GL33.glActiveTexture(activeTexture);
            GL33.glDepthMask(depthWrite);
            GL33.glColorMask(colorWrite[0], colorWrite[1], colorWrite[2], colorWrite[3]);
            GL33.glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
            GL33.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            toggle(GL33.GL_BLEND, blend);
            toggle(GL33.GL_DEPTH_TEST, depth);
            toggle(GL33.GL_CULL_FACE, cull);
            toggle(GL33.GL_SCISSOR_TEST, scissor);
            toggle(GL33.GL_STENCIL_TEST, stencil);
        }

        private static void toggle(int capability, boolean enable) {
            if (enable) GL33.glEnable(capability);
            else GL33.glDisable(capability);
        }
    }
}
