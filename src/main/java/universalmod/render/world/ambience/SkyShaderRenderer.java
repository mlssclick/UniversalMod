package universalmod.render.world.ambience;

import universalmod.render.ClientPipelines;

import com.mojang.blaze3d.systems.RenderSystem;

import java.awt.Color;

import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;




public final class SkyShaderRenderer {
    private static final Matrix4f IDENTITY = new Matrix4f();

    private static volatile Config config;
    private static long startTime = System.currentTimeMillis();
    private static volatile boolean lastRenderSucceeded;
    private static boolean atmosphereRendered;

    private SkyShaderRenderer() {
    }

    public static void resetTime() {
        startTime = System.currentTimeMillis();
    }

    public static void updateConfig(SkyShaderState state) {
        if (state == null) {
            return;
        }

        Color color = new Color(state.color(), true);
        config = new Config(
                state.mode(),
                state.color(),
                color.getRed() / 255.0F,
                color.getGreen() / 255.0F,
                color.getBlue() / 255.0F,
                state.starsEnabled(),
                state.starDensity(),
                state.nebulaStrength(),
                state.plasmaScale(),
                state.plasmaSpeed(),
                state.speed()
        );
    }

    public static boolean render(SkyShaderState state) {
        lastRenderSucceeded = false;
        if (state == null || !state.enabled()) {
            return false;
        }

        Config current = config;
        if (needsRefresh(state, current)) {
            updateConfig(state);
            current = config;
        }
        if (current == null) {
            return false;
        }

        var client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.getFramebuffer() == null || client.getFramebuffer().getColorAttachment() <= 0) {
            return false;
        }

        ShaderProgram previousShader = RenderSystem.getShader();
        ShaderProgramKey key = programFor(current.mode());
        ShaderProgram shader = ClientPipelines.bind(key, shaderNameFor(current.mode()));
        if (shader == null) {
            return false;
        }

        uploadUniforms(shader, current);

        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        try {
            BufferBuilder buffer = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            float size = 100.0F;
            face(buffer, -size, -size, size, size, -size, size, size, size, size, -size, size, size);
            face(buffer, size, -size, -size, -size, -size, -size, -size, size, -size, size, size, -size);
            face(buffer, -size, -size, -size, -size, -size, size, -size, size, size, -size, size, -size);
            face(buffer, size, -size, size, size, -size, -size, size, size, -size, size, size, size);
            face(buffer, -size, size, size, size, size, size, size, size, -size, -size, size, -size);
            face(buffer, -size, -size, -size, size, -size, -size, size, -size, size, -size, -size, size);
            BufferRenderer.drawWithGlobalProgram(buffer.end());
            lastRenderSucceeded = true;
            return true;
        } finally {
            RenderSystem.depthMask(depthMask);
            if (depthTest) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull();
            else RenderSystem.disableCull();
            if (blend) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
            } else RenderSystem.disableBlend();
            if (previousShader != null) RenderSystem.setShader(previousShader);
            else RenderSystem.clearShader();
        }
    }

    public static void beginFrame() { lastRenderSucceeded=false; atmosphereRendered=false; }
    public static boolean renderAtmosphere(universalmod.module.render.Atmosphere atmosphere) {
        atmosphereRendered=AtmosphereSkyRenderer.render(atmosphere);
        return atmosphereRendered;
    }
    public static boolean render(universalmod.module.render.Ambience ambience) { return render(ambience.skyShaderState()); }
    public static boolean wasAnySkyRendered() { return lastRenderSucceeded || atmosphereRendered; }
    public static boolean wasAtmosphereSkyRendered() { return atmosphereRendered; }

    public static boolean lastRenderSucceeded() {
        return lastRenderSucceeded;
    }

    public static void close() {
        AtmosphereSkyRenderer.close();
        atmosphereRendered=false;
        lastRenderSucceeded = false;
        config = null;
    }

    private static boolean needsRefresh(SkyShaderState state, Config current) {
        return current == null
                || !current.mode().equals(state.mode())
                || current.color() != state.color()
                || current.showStars() != state.starsEnabled()
                || current.starDensity() != state.starDensity()
                || current.nebulaStrength() != state.nebulaStrength()
                || current.plasmaScale() != state.plasmaScale()
                || current.plasmaSpeed() != state.plasmaSpeed()
                || current.skySpeed() != state.speed();
    }

    private static void uploadUniforms(ShaderProgram shader, Config current) {
        float time = (System.currentTimeMillis() - startTime) / 1000.0F;
        float mode = genericMode(current.mode());
        float stars = current.showStars() ? current.starDensity() : 0.0F;
        boolean northernLights = "Северное сияние".equals(current.mode());
        float intensity = northernLights ? 0.03F : 0.8F;
        float scale = northernLights ? 5.8F : current.plasmaScale();
        float speed = northernLights ? current.skySpeed() * 0.7F : current.plasmaSpeed();

        set4(shader, "SkyData0", time, mode, stars, current.nebulaStrength());
        set4(shader, "SkyData1", intensity, scale, speed, current.showStars() ? 1.0F : 0.0F);
        set4(shader, "SkyTopData", current.r() * 0.10F, current.g() * 0.10F, current.b() * 0.18F + 0.02F, 0.0F);
        set4(shader, "SkyHorizonData", current.r() * 0.45F + 0.04F, current.g() * 0.45F + 0.04F, current.b() * 0.45F + 0.06F, 0.0F);
        set4(shader, "NebColor1Data", current.r(), current.g(), current.b(), 0.0F);
        set4(shader, "NebColor2Data", current.b(), Math.min(1.0F, current.r() + 0.25F), Math.min(1.0F, current.g() + 0.20F), 0.0F);
        set4(shader, "StarColorData", 0.90F, 0.94F, 1.0F, 0.0F);
        set4(shader, "UserColorData", current.r(), current.g(), current.b(), 0.0F);
    }

    private static void set4(ShaderProgram shader, String name, float x, float y, float z, float w) {
        var uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(x, y, z, w);
        }
    }

    private static void face(
            BufferBuilder buffer,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float x4, float y4, float z4
    ) {
        buffer.vertex(IDENTITY, x1, y1, z1).color(0xFFFFFFFF);
        buffer.vertex(IDENTITY, x2, y2, z2).color(0xFFFFFFFF);
        buffer.vertex(IDENTITY, x3, y3, z3).color(0xFFFFFFFF);
        buffer.vertex(IDENTITY, x4, y4, z4).color(0xFFFFFFFF);
    }

    private static ShaderProgramKey programFor(String mode) {
        return switch (mode) {
            case "Aurora" -> ClientPipelines.SKY_AURORA;
            case "Северное сияние" -> ClientPipelines.SKY_NORTHERN;
            case "Nebula" -> ClientPipelines.SKY_NEBULA;
            case "Plasma" -> ClientPipelines.SKY_PLASMA;
            default -> ClientPipelines.SKY_GENERIC;
        };
    }

    private static String shaderNameFor(String mode) {
        return switch (mode) {
            case "Aurora" -> "render_sky_aurora";
            case "Северное сияние" -> "render_sky_northern";
            case "Nebula" -> "render_sky_nebula";
            case "Plasma" -> "render_sky_plasma";
            default -> "render_sky";
        };
    }

    private static float genericMode(String mode) {
        return switch (mode) {
            case "Cosmic Veil" -> 2.0F;
            case "Deep Space" -> 3.0F;
            case "Void" -> 4.0F;
            default -> 0.0F;
        };
    }

    private record Config(
            String mode,
            int color,
            float r,
            float g,
            float b,
            boolean showStars,
            float starDensity,
            float nebulaStrength,
            float plasmaScale,
            float plasmaSpeed,
            float skySpeed
    ) {
    }
}
