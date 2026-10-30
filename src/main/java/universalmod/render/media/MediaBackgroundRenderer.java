package universalmod.render.media;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import universalmod.render.UiShaders;
import universalmod.service.media.ArtworkPalette;

public final class MediaBackgroundRenderer {
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static long retryAt;
    private MediaBackgroundRenderer() {}

    public static boolean render(float width, float height, ArtworkPalette colors, float energy, float phase, float opacity) {
        if (width <= 0 || height <= 0 || colors == null || System.currentTimeMillis() < retryAt) return false;
        ShaderProgram previous = RenderSystem.getShader();
        ShaderProgram shader;
        try {
            shader = UiShaders.bind(UiShaders.MEDIA_BACKGROUND, "ui_media_background");
            if (shader == null) return false;
        } catch (Throwable error) {
            retryAt = System.currentTimeMillis() + 2500L;
            return false;
        }
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean writeDepth = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        try {
            var resolution = shader.getUniform("Resolution");
            if (resolution != null) resolution.set(width, height);
            var clock = shader.getUniform("Time");
            if (clock != null) clock.set(phase % 100000F);
            var level = shader.getUniform("Energy");
            if (level != null) level.set(Math.max(0F, Math.min(1F, energy)));
            setColor(shader, "ColorA4", colors.primary());
            setColor(shader, "ColorB4", colors.secondary());
            setColor(shader, "ColorC4", colors.highlight());
            setColor(shader, "ColorD4", colors.fourth());
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            BufferBuilder b = Tessellator.getInstance().getBuffer();
            b.begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            int vertexColor = (Math.max(0, Math.min(255, Math.round(opacity * 255F))) << 24) | 0xFFFFFF;
            b.vertex(IDENTITY, -1F, -1F, 0F).color(vertexColor).next();
            b.vertex(IDENTITY, -1F, 1F, 0F).color(vertexColor).next();
            b.vertex(IDENTITY, 1F, 1F, 0F).color(vertexColor).next();
            b.vertex(IDENTITY, 1F, -1F, 0F).color(vertexColor).next();
            BufferRenderer.drawWithGlobalProgram(b.end());
            return true;
        } catch (Throwable error) {
            retryAt = System.currentTimeMillis() + 2500L;
            return false;
        } finally {
            RenderSystem.depthMask(writeDepth);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (previous != null) RenderSystem.setShader(() -> previous);;
        }
    }

    private static void setColor(ShaderProgram shader, String name, int color) {
        var uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(((color >>> 16) & 255) / 255F,
                ((color >>> 8) & 255) / 255F, (color & 255) / 255F, 1F);
    }

    public static void reload() { retryAt = 0L; }
}
