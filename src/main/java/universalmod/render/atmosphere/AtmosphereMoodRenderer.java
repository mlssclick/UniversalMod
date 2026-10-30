package universalmod.render.atmosphere;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL33;
import universalmod.module.render.Atmosphere;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;

public final class AtmosphereMoodRenderer {
    private static final String ATMOSPHERE_SHADERS = "/assets/universalmod/shaders/post/atmosphere/";
    private static final String WINTER_VERTEX = "/assets/universalmod/shaders/post/winter/winter.vsh";
    private static final int PUMPKIN_LIMIT = 20;
    private static final int WEATHER_BYTES = WeatherUniformLayout.TOTAL_BYTES + PUMPKIN_LIMIT * 16;
    private static final int POST_BYTES = 32;
    private static final int WEATHER = 0, BRIGHT = 1, BLUR = 2;
    private static final int[] programs = new int[3];
    private static int vao, weatherUbo, brightUbo, horizontalUbo, verticalUbo;
    private static int sceneFbo, sceneColor, sceneDepth;
    private static int brightFbo, brightTexture, horizontalFbo, horizontalTexture;
    private static int verticalFbo, verticalTexture;
    private static int width = -1, height = -1;
    private static long epoch = -1, retryAfterMillis;

    private AtmosphereMoodRenderer() { }

    public static void render(Atmosphere module, Camera camera, Matrix4f view,
                              Matrix4f projection, float partialTick) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (module == null || !module.isMoodActive() || client.world == null || client.player == null
                || camera == null || !camera.isReady() || view == null || projection == null
                || System.currentTimeMillis() < retryAfterMillis) return;
        Framebuffer main = client.getFramebuffer();
        if (main == null || main.fbo <= 0 || main.getColorAttachment() <= 0
                || main.getDepthAttachment() <= 0 || main.viewportWidth < 1 || main.viewportHeight < 1) return;

        GlState state = new GlState();
        try {
            int w = main.viewportWidth, h = main.viewportHeight;
            initialize();
            ensureTargets(w, h);
            if (epoch < 0) epoch = System.currentTimeMillis();
            float time = module.isSakuraMode()
                    ? (System.currentTimeMillis() - epoch) * 0.001f * module.getSakuraSkySpeed()
                    : client.world.getTime() * 0.05f;
            Vec3d pos = camera.getPos();
            Matrix4f inverse = new Matrix4f(projection).mul(view).invert();
            AtmosphereSunProjection.Sun sun = AtmosphereSunProjection.sample(camera, partialTick, view, projection);
            float renderDistance = Math.max(64f, client.options.getViewDistance().getValue() * 16.0f);

            GL33.glBindFramebuffer(GL33.GL_READ_FRAMEBUFFER, main.fbo);
            GL33.glBindFramebuffer(GL33.GL_DRAW_FRAMEBUFFER, sceneFbo);
            GL33.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL33.GL_COLOR_BUFFER_BIT, GL33.GL_NEAREST);
            GL33.glBindFramebuffer(GL33.GL_READ_FRAMEBUFFER, main.fbo);
            GL33.glActiveTexture(GL33.GL_TEXTURE0);
            GL33.glBindTexture(GL33.GL_TEXTURE_2D, sceneDepth);
            GL33.glCopyTexSubImage2D(GL33.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);

            GL33.glDisable(GL33.GL_BLEND);
            GL33.glDisable(GL33.GL_DEPTH_TEST);
            GL33.glDisable(GL33.GL_CULL_FACE);
            GL33.glDisable(GL33.GL_SCISSOR_TEST);
            GL33.glDisable(GL33.GL_STENCIL_TEST);
            GL33.glDepthMask(false);
            GL33.glColorMask(true, true, true, true);
            GL33.glBindVertexArray(vao);

            if (module.isSakuraMode()) {
                uploadBright(Math.max(1, w / 2), Math.max(1, h / 2));
                draw(BRIGHT, brightFbo, Math.max(1, w / 2), Math.max(1, h / 2),
                        brightUbo, sceneColor, 0, 0);
                uploadBlur(horizontalUbo, Math.max(1, w / 2), Math.max(1, h / 2), 1, 0, 5.0f);
                draw(BLUR, horizontalFbo, Math.max(1, w / 2), Math.max(1, h / 2),
                        horizontalUbo, brightTexture, 0, 0);
                uploadBlur(verticalUbo, Math.max(1, w / 2), Math.max(1, h / 2), 0, 1, 5.0f);
                draw(BLUR, verticalFbo, Math.max(1, w / 2), Math.max(1, h / 2),
                        verticalUbo, horizontalTexture, 0, 0);
            }
            uploadWeather(inverse, projection, pos, w, h, time, sun, renderDistance, module);
            draw(WEATHER, main.fbo, w, h, weatherUbo, sceneColor, sceneDepth, verticalTexture);
        } catch (RuntimeException | LinkageError failure) {
            System.err.println("[UniversalMod] Atmosphere Mood render failed: " + failure);
            retryAfterMillis = System.currentTimeMillis() + 2000L;
            releaseGpu();
        } finally {
            state.restore();
        }
    }

    private static void initialize() {
        if (programs[WEATHER] != 0) return;
        String vertex = resource(WINTER_VERTEX);
        programs[WEATHER] = program(vertex, resource(ATMOSPHERE_SHADERS + "dust_ash.fsh"),
                "MoodWeatherData", "Sampler0", "Sampler1", "Sampler2");
        programs[BRIGHT] = program(vertex, resource(ATMOSPHERE_SHADERS + "bright.fsh"),
                "BrightData", "InSampler");
        programs[BLUR] = program(vertex, resource(ATMOSPHERE_SHADERS + "blur.fsh"),
                "MoodBlurData", "InSampler");
        vao = GL33.glGenVertexArrays();
        weatherUbo = buffer(WEATHER_BYTES);
        brightUbo = buffer(POST_BYTES);
        horizontalUbo = buffer(POST_BYTES);
        verticalUbo = buffer(POST_BYTES);
    }

    private static String resource(String path) {
        try (InputStream stream = AtmosphereMoodRenderer.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing original Atmosphere shader: " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load Atmosphere shader: " + path, exception);
        }
    }

    private static int compile(int type, String source) {
        int shader = GL33.glCreateShader(type);
        GL33.glShaderSource(shader, source);
        GL33.glCompileShader(shader);
        if (GL33.glGetShaderi(shader, GL33.GL_COMPILE_STATUS) == GL33.GL_FALSE) {
            String log = GL33.glGetShaderInfoLog(shader);
            GL33.glDeleteShader(shader);
            throw new IllegalStateException("Atmosphere GLSL compilation error: " + log);
        }
        return shader;
    }

    private static int program(String vertex, String fragment, String block, String... samplers) {
        int v = compile(GL33.GL_VERTEX_SHADER, vertex);
        int f = compile(GL33.GL_FRAGMENT_SHADER, fragment);
        int p = GL33.glCreateProgram();
        try {
            GL33.glAttachShader(p, v);
            GL33.glAttachShader(p, f);
            GL33.glLinkProgram(p);
            if (GL33.glGetProgrami(p, GL33.GL_LINK_STATUS) == GL33.GL_FALSE)
                throw new IllegalStateException("Atmosphere GLSL linking error: " + GL33.glGetProgramInfoLog(p));
            GL33.glUseProgram(p);
            for (int i = 0; i < samplers.length; i++) {
                int location = GL33.glGetUniformLocation(p, samplers[i]);
                if (location >= 0) GL33.glUniform1i(location, i);
            }
            int index = GL33.glGetUniformBlockIndex(p, block);
            if (index < 0 || index == GL33.GL_INVALID_INDEX)
                throw new IllegalStateException("Missing Atmosphere uniform block " + block);
            GL33.glUniformBlockBinding(p, index, 0);
            return p;
        } catch (RuntimeException failure) {
            GL33.glDeleteProgram(p);
            throw failure;
        } finally {
            GL33.glDeleteShader(v);
            GL33.glDeleteShader(f);
        }
    }

    private static int buffer(int bytes) {
        int id = GL33.glGenBuffers();
        GL33.glBindBuffer(GL33.GL_UNIFORM_BUFFER, id);
        GL33.glBufferData(GL33.GL_UNIFORM_BUFFER, bytes, GL33.GL_DYNAMIC_DRAW);
        return id;
    }

    private static void upload(int dst, ByteBuffer data) {
        data.position(0);
        GL33.glBindBuffer(GL33.GL_UNIFORM_BUFFER, dst);
        GL33.glBufferSubData(GL33.GL_UNIFORM_BUFFER, 0, data);
    }

    static ByteBuffer weatherUniforms(Matrix4f inverse, Matrix4f projection, Vec3d cam,
                                      int w, int h, float time, AtmosphereSunProjection.Sun sun,
                                      float viewDistance, Atmosphere settings) {
        float sx = sun.x(), sy = sun.y(), sz = sun.z();
        float sunU = sun.u(), sunV = sun.v(), sunVisibility = sun.onScreen();
        float reversedDepth = sun.reversedDepth();
        Color warm = settings.getMoodSunColor();
        ByteBuffer b = BufferUtils.createByteBuffer(WEATHER_BYTES).order(ByteOrder.nativeOrder());
            inverse.get(0, b);
            vec3(b, 1, (float)cam.x, (float)cam.y, (float)cam.z);
            vec2(b, 2, w, h);
            scalar(b, 3, time);
            scalar(b, 4, 2.0f / Math.max(0.01f, Math.abs(projection.m11())) / Math.max(1, h));
            vec3(b, 5, sx, sy, sz);
            vec3(b, 6, sunU, sunV, sunVisibility);
            scalar(b, 7, viewDistance);
            scalar(b, 8, (float)cam.y - 5.0f);
            scalar(b, 9, settings.getMoodKind());
            scalar(b, 10, settings.getRainAmount());
            scalar(b, 11, settings.getMoodWindSpeed());
            scalar(b, 12, 0.12f + settings.getMoodHaze() * 0.40f);
            scalar(b, 13, settings.getMoodGrade());
            scalar(b, 14, settings.isSakuraMode() ? settings.getSakuraBloom()*0.78f : 0f);
            scalar(b, 15, settings.getMoodVignette());
            scalar(b, 16, settings.isSundayMode() ? settings.getMoodGodRays() : settings.isSakuraMode() ? settings.getMoodGodRays()*0.28f : 0f);
            scalar(b, 17, 0.0f);
            scalar(b, 18, 0.0f);
            scalar(b, 19, 0.015f);
            scalar(b, 20, 0.05f);
            scalar(b, 21, 0.0f);
            scalar(b, 22, 0.0f);
            if (settings.isSakuraMode()) {
                vec3(b,23,.69f,.80f,1.0f); vec3(b,24,1.0f,.79f,.85f);
                vec3(b,25,1.0f,.91f,.80f); vec3(b,26,.94f,.73f,.81f);
                vec3(b,27,1.0f,.79f,.86f); vec3(b,28,.22f,.08f,.22f);
                vec3(b,29,1.0f,.92f,.91f);
            } else if (settings.isThunderMode()) {
                vec3(b,23,.09f,.12f,.16f); vec3(b,24,.21f,.23f,.26f);
                vec3(b,25,.32f,.39f,.50f); vec3(b,26,.24f,.26f,.29f);
                vec3(b,27,.39f,.46f,.55f); vec3(b,28,.12f,.15f,.19f);
                vec3(b,29,.68f,.72f,.78f);
            } else if (settings.isAutumnMode()) {
                vec3(b,23,.37f,.42f,.48f); vec3(b,24,.68f,.63f,.55f);
                vec3(b,25,.92f,.72f,.44f); vec3(b,26,.55f,.51f,.46f);
                vec3(b,27,.76f,.55f,.33f); vec3(b,28,.21f,.20f,.22f);
                vec3(b,29,.89f,.72f,.49f);
            } else if (settings.isSundayMode()) {
                vec3(b,23,.25f,.48f,.82f); vec3(b,24,.77f,.88f,.98f);
                vec3(b,25,1.0f,.87f,.63f); vec3(b,26,.67f,.78f,.86f);
                vec3(b,27,.84f,.92f,1.0f); vec3(b,28,.17f,.23f,.33f);
                vec3(b,29,1.0f,.92f,.76f);
            } else {
                vec3(b,23,.04f,.04f,.07f); vec3(b,24,.21f,.14f,.17f);
                vec3(b,25,.65f,.10f,.10f); vec3(b,26,.24f,.22f,.24f);
                vec3(b,27,.55f,.11f,.14f); vec3(b,28,.16f,.06f,.08f);
                vec3(b,29,.83f,.25f,.28f);
            }
            vec3(b, 30, sx, sy, sz);
            vec3(b, 31, 1.0f, 1.0f, 1.0f);
            vec4(b, 32, 1.04f, 1.01f, 1.06f, 0.02f);
            scalar(b, 33, reversedDepth);
            scalar(b, 34, 1.0f);
        b.position(0);
        return b;
    }

    private static void uploadWeather(Matrix4f inverse, Matrix4f projection, Vec3d cam,
                                      int w, int h, float time, AtmosphereSunProjection.Sun sun,
                                      float viewDistance, Atmosphere settings) {
        upload(weatherUbo, weatherUniforms(inverse, projection, cam, w, h, time, sun, viewDistance, settings));
    }

    private static void uploadBright(int w, int h) {
        ByteBuffer b = BufferUtils.createByteBuffer(POST_BYTES).order(ByteOrder.nativeOrder());
        b.putFloat(0, w); b.putFloat(4, h);
        b.putFloat(8, 0.78f); b.putFloat(12, 1.0f);
        upload(brightUbo, b);
    }

    private static void uploadBlur(int target, int w, int h, float dirX, float dirY, float radius) {
        ByteBuffer b = BufferUtils.createByteBuffer(POST_BYTES).order(ByteOrder.nativeOrder());
        b.putFloat(0, w); b.putFloat(4, h);
        b.putFloat(8, dirX); b.putFloat(12, dirY);
        b.putFloat(16, radius);
        upload(target, b);
    }

    private static void scalar(ByteBuffer b, int index, float value) { WeatherUniformLayout.scalar(b, index, value); }
    private static void vec2(ByteBuffer b, int index, float x, float y) { WeatherUniformLayout.vec2(b, index, x, y); }
    private static void vec3(ByteBuffer b, int index, float x, float y, float z) { WeatherUniformLayout.vec3(b, index, x, y, z); }
    private static void vec4(ByteBuffer b, int index, float x, float y, float z, float w) { WeatherUniformLayout.vec4(b, index, x, y, z, w); }

    private static void draw(int program, int framebuffer, int w, int h,
                             int ubo, int texture0, int texture1, int texture2) {
        GL33.glBindFramebuffer(GL33.GL_FRAMEBUFFER, framebuffer);
        GL33.glViewport(0, 0, w, h);
        GL33.glUseProgram(programs[program]);
        GL33.glBindBufferBase(GL33.GL_UNIFORM_BUFFER, 0, ubo);
        int[] textures = { texture0, texture1, texture2 };
        for (int i = 0; i < textures.length; i++) {
            GL33.glActiveTexture(GL33.GL_TEXTURE0 + i);
            GL33.glBindTexture(GL33.GL_TEXTURE_2D, textures[i]);
        }
        GL33.glDrawArrays(GL33.GL_TRIANGLES, 0, 6);
    }

    private static int color(int w, int h) {
        int texture = GL33.glGenTextures();
        GL33.glBindTexture(GL33.GL_TEXTURE_2D, texture);
        GL33.glTexImage2D(GL33.GL_TEXTURE_2D, 0, GL33.GL_RGBA8, w, h, 0,
                GL33.GL_RGBA, GL33.GL_UNSIGNED_BYTE, (ByteBuffer)null);
        GL33.glTexParameteri(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MIN_FILTER, GL33.GL_LINEAR);
        GL33.glTexParameteri(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MAG_FILTER, GL33.GL_LINEAR);
        GL33.glTexParameteri(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_WRAP_S, GL33.GL_CLAMP_TO_EDGE);
        GL33.glTexParameteri(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_WRAP_T, GL33.GL_CLAMP_TO_EDGE);
        return texture;
    }

    private static int framebuffer(int texture, int depth) {
        int fbo = GL33.glGenFramebuffers();
        GL33.glBindFramebuffer(GL33.GL_FRAMEBUFFER, fbo);
        GL33.glFramebufferTexture2D(GL33.GL_FRAMEBUFFER, GL33.GL_COLOR_ATTACHMENT0,
                GL33.GL_TEXTURE_2D, texture, 0);
        if (depth != 0) GL33.glFramebufferTexture2D(GL33.GL_FRAMEBUFFER, GL33.GL_DEPTH_ATTACHMENT,
                GL33.GL_TEXTURE_2D, depth, 0);
        if (GL33.glCheckFramebufferStatus(GL33.GL_FRAMEBUFFER) != GL33.GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("Atmosphere framebuffer incomplete");
        return fbo;
    }

    private static void ensureTargets(int w, int h) {
        if (width == w && height == h && sceneFbo != 0 && verticalFbo != 0) return;
        releaseTargets();
        sceneColor = color(w, h);
        sceneDepth = GL33.glGenTextures();
        GL33.glBindTexture(GL33.GL_TEXTURE_2D, sceneDepth);
        GL33.glTexImage2D(GL33.GL_TEXTURE_2D, 0, GL33.GL_DEPTH_COMPONENT24, w, h, 0,
                GL33.GL_DEPTH_COMPONENT, GL33.GL_UNSIGNED_INT, (ByteBuffer)null);
        GL33.glTexParameteri(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MIN_FILTER, GL33.GL_NEAREST);
        GL33.glTexParameteri(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MAG_FILTER, GL33.GL_NEAREST);
        GL33.glTexParameteri(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_WRAP_S, GL33.GL_CLAMP_TO_EDGE);
        GL33.glTexParameteri(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_WRAP_T, GL33.GL_CLAMP_TO_EDGE);
        sceneFbo = framebuffer(sceneColor, sceneDepth);
        int halfW = Math.max(1, w / 2), halfH = Math.max(1, h / 2);
        brightTexture = color(halfW, halfH);
        brightFbo = framebuffer(brightTexture, 0);
        horizontalTexture = color(halfW, halfH);
        horizontalFbo = framebuffer(horizontalTexture, 0);
        verticalTexture = color(halfW, halfH);
        verticalFbo = framebuffer(verticalTexture, 0);
        width = w; height = h;
    }

    public static void resetTime() { epoch = -1L; }
    public static void close() { resetTime(); releaseGpu(); retryAfterMillis = 0L; }

    private static void releaseTargets() {
        for (int id : new int[]{sceneFbo, brightFbo, horizontalFbo, verticalFbo})
            if (id != 0) GL33.glDeleteFramebuffers(id);
        for (int id : new int[]{sceneColor, sceneDepth, brightTexture, horizontalTexture, verticalTexture})
            if (id != 0) GL33.glDeleteTextures(id);
        sceneFbo = brightFbo = horizontalFbo = verticalFbo = 0;
        sceneColor = sceneDepth = brightTexture = horizontalTexture = verticalTexture = 0;
        width = height = -1;
    }

    private static void releaseGpu() {
        releaseTargets();
        for (int i = 0; i < programs.length; i++) {
            if (programs[i] != 0) GL33.glDeleteProgram(programs[i]);
            programs[i] = 0;
        }
        for (int id : new int[]{weatherUbo, brightUbo, horizontalUbo, verticalUbo})
            if (id != 0) GL33.glDeleteBuffers(id);
        weatherUbo = brightUbo = horizontalUbo = verticalUbo = 0;
        if (vao != 0) GL33.glDeleteVertexArrays(vao);
        vao = 0;
    }

    static final class GlState {
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

        GlState() {
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

        void restore() {
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
