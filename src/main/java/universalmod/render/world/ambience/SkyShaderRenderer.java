package universalmod.render.world.ambience;

import com.mojang.blaze3d.systems.RenderSystem;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import universalmod.render.post.LegacyGl;
import universalmod.module.render.Atmosphere;
import net.minecraft.util.math.MathHelper;

public final class SkyShaderRenderer {
    private static final Map<String, Integer> PROGRAMS = new HashMap<>();
    private static long startTime = System.currentTimeMillis();
     
    private static final Set<String> FAILED_MODES = new HashSet<>();
    private static int cubeVao, cubeVbo;
    private static int atmosphereProgram;
    private static final long ATMOSPHERE_EPOCH = System.currentTimeMillis();
    private static final float[][] WINTER = {{.22f,.28f,.37f},{.70f,.77f,.85f},{.92f,.95f,1f},{.59f,.68f,.79f}};
    private static final float[][] SAKURA = {{.69f,.80f,1f},{1f,.79f,.85f},{1f,.91f,.80f},{.94f,.73f,.81f}};
    private static final float[][] THUNDER = {{.09f,.12f,.16f},{.21f,.23f,.26f},{.32f,.39f,.50f},{.24f,.26f,.29f}};
    private static final float[][] AUTUMN = {{.37f,.42f,.48f},{.68f,.63f,.55f},{.92f,.72f,.44f},{.55f,.51f,.46f}};
    private static final float[][] SUNDAY = {{.25f,.48f,.82f},{.77f,.88f,.98f},{1f,.87f,.63f},{.67f,.78f,.86f}};
    private static final float[][] BLOODMOON = {{.04f,.04f,.07f},{.21f,.14f,.17f},{.65f,.10f,.10f},{.24f,.22f,.24f}};

    private SkyShaderRenderer() {
    }

    public static void resetTime() {
        startTime = System.currentTimeMillis();
        
        FAILED_MODES.clear();
    }

     




    public static boolean render(SkyShaderState state, Matrix4f modelView, Matrix4f projection) {
        if (state == null || !state.enabled() || modelView == null || projection == null) return false;
        String key = shaderKey(state.mode());
        if (FAILED_MODES.contains(key)) return false;
        try {
            int program = PROGRAMS.computeIfAbsent(key, SkyShaderRenderer::compile);
            if (program == 0) throw new IllegalStateException("Sky shader program was not created: " + key);
            ensureCube();
            MinecraftClient mc = MinecraftClient.getInstance();
            Framebuffer target = mc.getFramebuffer();
            if (target == null) return false;

            
            Matrix4f skyView = new Matrix4f(modelView);
            skyView.m30(0f);
            skyView.m31(0f);
            skyView.m32(0f);

            boolean oldDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            boolean oldCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            boolean oldBlend = GL11.glIsEnabled(GL11.GL_BLEND);
            boolean oldDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            int oldProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int oldVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            int oldFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            java.nio.IntBuffer oldViewport = BufferUtils.createIntBuffer(4);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, oldViewport);
            try {
                target.beginWrite(false);
                GL11.glViewport(0, 0, target.textureWidth, target.textureHeight);
                RenderSystem.disableDepthTest();
                RenderSystem.depthMask(false);
                RenderSystem.disableCull();
                RenderSystem.disableBlend();
                GL20.glUseProgram(program);
                LegacyGl.m4(program, "uProjection", projection);
                LegacyGl.m4(program, "uView", skyView);

                
                float time = (System.currentTimeMillis() - startTime) / 1000f;
                float generic = genericMode(state.mode());
                float stars = state.starsEnabled() ? state.starDensity() : 0f;
                boolean north = "Северное сияние".equals(state.mode());
                float intensity = north ? .03f : .8f;
                float scale = north ? 5.8f : state.plasmaScale();
                float speed = north ? state.speed() * .7f : state.plasmaSpeed();
                int color = state.color();
                float r = ((color >> 16) & 255) / 255f;
                float g = ((color >> 8) & 255) / 255f;
                float b = (color & 255) / 255f;
                LegacyGl.f4(program, "SkyData0", time, generic, stars, state.nebulaStrength());
                LegacyGl.f4(program, "SkyData1", intensity, scale, speed, state.starsEnabled() ? 1f : 0f);
                LegacyGl.f4(program, "SkyTopData", r * .10f, g * .10f, b * .18f + .02f, 0f);
                LegacyGl.f4(program, "SkyHorizonData", r * .45f + .04f, g * .45f + .04f, b * .45f + .06f, 0f);
                LegacyGl.f4(program, "NebColor1Data", r, g, b, 0f);
                LegacyGl.f4(program, "NebColor2Data", b, Math.min(1f, r + .25f), Math.min(1f, g + .20f), 0f);
                LegacyGl.f4(program, "StarColorData", .90f, .94f, 1f, 0f);
                LegacyGl.f4(program, "UserColorData", r, g, b, 0f);
                GL30.glBindVertexArray(cubeVao);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 36);
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
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, oldFramebuffer);
                GL11.glViewport(oldViewport.get(0), oldViewport.get(1), oldViewport.get(2), oldViewport.get(3));
            }
            return true;
        } catch (Exception failure) {
            FAILED_MODES.add(key);
            ;
            ;
            return false;
        }
    }

     
    public static boolean renderAtmosphere(Atmosphere atmosphere, Matrix4f modelView, Matrix4f projection) {
        if (atmosphere == null || !(atmosphere.isMoodActive() || atmosphere.isWinterActive())
                || modelView == null || projection == null) return false;
        try {
            if (atmosphereProgram == 0) {
                String fragment = load("/assets/universalmod/shaders/sky/atmosphere_dust_ash.fsh")
                        .replace("layout(std140) uniform AtmosphereSkyData { vec4 AtmosphereSkyParams[6]; };",
                                "uniform vec4 AtmosphereSkyParams[6];");
                atmosphereProgram = LegacyGl.program(ATMOSPHERE_VERTEX, fragment, "atmosphere_sky_1201");
            }
            ensureCube();
            MinecraftClient client = MinecraftClient.getInstance();
            Framebuffer target = client.getFramebuffer();
            if (target == null || client.world == null) return false;
            float[][] palette = atmosphere.isWinterActive() ? WINTER : atmosphere.isSakuraMode() ? SAKURA
                    : atmosphere.isThunderMode() ? THUNDER : atmosphere.isAutumnMode() ? AUTUMN
                    : atmosphere.isSundayMode() ? SUNDAY : BLOODMOON;
            float[] uniforms = new float[24];
            float angle = client.world.getSkyAngleRadians(client.getTickDelta());
            uniforms[0] = -MathHelper.sin(angle);
            uniforms[1] = MathHelper.cos(angle);
            uniforms[2] = 0f;
            uniforms[3] = (System.currentTimeMillis() - ATMOSPHERE_EPOCH) * .001f;
            for (int i = 0; i < 4; i++) {
                uniforms[(i+1)*4] = palette[i][0];
                uniforms[(i+1)*4+1] = palette[i][1];
                uniforms[(i+1)*4+2] = palette[i][2];
            }
            uniforms[20] = atmosphere.isWinterActive() ? 1f : atmosphere.getMoodKind();
            boolean oldDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            boolean oldCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            boolean oldBlend = GL11.glIsEnabled(GL11.GL_BLEND);
            boolean oldMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            int oldProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int oldVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            try {
                target.beginWrite(false);
                GL11.glViewport(0, 0, target.textureWidth, target.textureHeight);
                RenderSystem.disableDepthTest();
                RenderSystem.disableCull();
                RenderSystem.disableBlend();
                RenderSystem.depthMask(false);
                GL20.glUseProgram(atmosphereProgram);
                Matrix4f skyView = new Matrix4f(modelView);
                skyView.m30(0f);skyView.m31(0f);skyView.m32(0f);
                LegacyGl.m4(atmosphereProgram, "uProjection", projection);
                LegacyGl.m4(atmosphereProgram, "uView", skyView);
                int params = GL20.glGetUniformLocation(atmosphereProgram, "AtmosphereSkyParams[0]");
                if (params >= 0) GL20.glUniform4fv(params, uniforms);
                GL30.glBindVertexArray(cubeVao);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 36);
                return true;
            } finally {
                GL30.glBindVertexArray(oldVao);
                GL20.glUseProgram(oldProgram);
                RenderSystem.depthMask(oldMask);
                if (oldDepth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
                if (oldCull) RenderSystem.enableCull(); else RenderSystem.disableCull();
                if (oldBlend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
                target.beginWrite(false);
            }
        } catch (Throwable failure) {
            ;
            return false;
        }
    }

    private static final String ATMOSPHERE_VERTEX = """
            #version 150
            in vec3 Position;
            uniform mat4 uProjection;
            uniform mat4 uView;
            out vec4 FragColor;
            out vec3 vPos;
            void main() {
                vPos = Position;
                FragColor = vec4(1.0);
                gl_Position = uProjection * uView * vec4(Position, 1.0);
            }
            """;

    public static void close() {
        if (atmosphereProgram != 0) { LegacyGl.deleteProgram(atmosphereProgram); atmosphereProgram = 0; }
        for (int p : PROGRAMS.values()) LegacyGl.deleteProgram(p);
        PROGRAMS.clear();
        if (cubeVbo != 0) {
            GL15.glDeleteBuffers(cubeVbo);
            cubeVbo = 0;
        }
        if (cubeVao != 0) {
            GL30.glDeleteVertexArrays(cubeVao);
            cubeVao = 0;
        }
        FAILED_MODES.clear();
    }

    private static int compile(String key) {
        
        
        String source = load("/assets/universalmod/shaders/core/render_" + key + ".fsh");
        return LegacyGl.program(VERTEX, source, "universal_sky_" + key);
    }

    private static void ensureCube() {
        if (cubeVao != 0 && cubeVbo != 0) return;
        float s = 100f;
        float[] vertices = {
                -s, -s, s, s, -s, s, s, s, s, -s, -s, s, s, s, s, -s, s, s,
                s, -s, -s, -s, -s, -s, -s, s, -s, s, -s, -s, -s, s, -s, s, s, -s,
                -s, -s, -s, -s, -s, s, -s, s, s, -s, -s, -s, -s, s, s, -s, s, -s,
                s, -s, s, s, -s, -s, s, s, -s, s, -s, s, s, s, -s, s, s, s,
                -s, s, s, s, s, s, s, s, -s, -s, s, s, s, s, -s, -s, s, -s,
                -s, -s, -s, s, -s, -s, s, -s, s, -s, -s, -s, s, -s, s, -s, -s, s
        };
        FloatBuffer data = BufferUtils.createFloatBuffer(vertices.length);
        data.put(vertices).flip();
        
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        try {
            cubeVao = GL30.glGenVertexArrays();
            cubeVbo = GL15.glGenBuffers();
            GL30.glBindVertexArray(cubeVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, cubeVbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * Float.BYTES, 0L);
        } finally {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousBuffer);
            GL30.glBindVertexArray(previousVao);
        }
    }

    private static String load(String path) {
        try (InputStream in = SkyShaderRenderer.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }

    private static String shaderKey(String mode) {
        return switch (mode == null ? "" : mode) {
            case "Aurora" -> "sky_aurora";
            case "Северное сияние" -> "sky_northern";
            case "Nebula" -> "sky_nebula";
            case "Plasma" -> "sky_plasma";
            default -> "sky";
        }
                ;
    }

    private static float genericMode(String mode) {
        return switch (mode == null ? "" : mode) {
            case "Cosmic Veil" -> 2f;
            case "Deep Space" -> 3f;
            case "Void" -> 4f;
            default -> 0f;
        }
                ;
    }

    private static final String VERTEX = """
            #version 150
            in vec3 Position;
            uniform mat4 uProjection;
            uniform mat4 uView;
            out vec4 FragColor;
            out vec3 vPos;
            void main(){
            vPos=Position;
            FragColor=vec4(1.0);
            gl_Position=uProjection*uView*vec4(Position,1.0);
            }
            """;
}
