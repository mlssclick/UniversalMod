package universalmod.render.blur;

import universalmod.render.GlUiState;

import universalmod.render.UiShaders;
import universalmod.render.ShaderDiagnostics;
import universalmod.render.UiRender;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;

public final class BlurRenderer {
    private BlurRenderer() {
    }

    public static void blur(float x, float y, float w, float h, float r1, float r2, float r3, float r4, float blurRadius, float smoothness, int tint) {
        blur(x, y, w, h, r1, r2, r3, r4, blurRadius, smoothness, tint, tint, tint, tint);
    }

    public static void blur(float x, float y, float w, float h, float r1, float r2, float r3, float r4, float blurRadius, float smoothness, int cTL, int cTR, int cBR, int cBL) {
        blur(x, y, w, h, r1, r2, r3, r4, blurRadius, smoothness, cTL, cTR, cBR, cBL,
                BlurPass.NO_VERTICAL_SPLIT, 0xFFFFFFFF);
    }

    public static void blur(float x, float y, float w, float h, float r1, float r2, float r3, float r4,
                            float blurRadius, float smoothness, int cTL, int cTR, int cBR, int cBL,
                            float verticalSplit, int splitColor) {
        blur(x, y, w, h, r1, r2, r3, r4, blurRadius, smoothness,
                cTL, cTR, cBR, cBL, verticalSplit, splitColor, BlurAlgorithm.KAWASE);
    }

    public static void blur(float x, float y, float w, float h, float r1, float r2, float r3, float r4,
                            float blurRadius, float smoothness, int cTL, int cTR, int cBR, int cBL,
                            float verticalSplit, int splitColor, BlurAlgorithm algorithm) {
        if (w <= 0 || h <= 0) return;
        ScoreboardBlurRenderer.Region scoreboard = algorithm == BlurAlgorithm.SCOREBOARD_SEPARABLE
                ? ScoreboardBlurRenderer.prepare(blurRadius, x, y, w, h) : null;
        int blurredTexture = scoreboard != null ? scoreboard.texture()
                : algorithm == BlurAlgorithm.SCOREBOARD_SEPARABLE ? 0
                : KawaseBlurRenderer.prepareBlur(blurRadius, w, h);
        if (blurredTexture == 0) return;
        Framebuffer main = MinecraftClient.getInstance().getFramebuffer();
        GlUiState savedState = GlUiState.capture();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShaderTexture(0, blurredTexture);
        try {
            ShaderProgram shader = UiShaders.bind(UiShaders.BLUR, "ui_blur");
            if (shader == null) {
                ShaderDiagnostics.missing("ui_blur");
                return;
            }
            set2(shader, "Size", w, h);
            set4(shader, "Radius", r1, r2, r3, r4);
            set1(shader, "Smoothness", Math.max(0.001f, smoothness));
            set1(shader, "VerticalSplit", verticalSplit);
            set4(shader, "SplitColor",
                    ((splitColor >>> 16) & 255) / 255f,
                    ((splitColor >>> 8) & 255) / 255f,
                    (splitColor & 255) / 255f,
                    ((splitColor >>> 24) & 255) / 255f);
            if (scoreboard != null) {
                set4(shader, "BlurRegion", scoreboard.x(), scoreboard.y(), scoreboard.width(), scoreboard.height());
            } else {
                set4(shader, "BlurRegion", 0f, 0f, Math.max(1, main.textureWidth), Math.max(1, main.textureHeight));
            }
            BufferBuilder b = Tessellator.getInstance().getBuffer();
            b.begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            b.vertex(UiRender.matrix(), x, y, 0).texture(0.0f, 0.0f).color(cTL).next();
            b.vertex(UiRender.matrix(), x, y + h, 0).texture(0.0f, 1.0f).color(cBL).next();
            b.vertex(UiRender.matrix(), x + w, y + h, 0).texture(1.0f, 1.0f).color(cBR).next();
            b.vertex(UiRender.matrix(), x + w, y, 0).texture(1.0f, 0.0f).color(cTR).next();
            BufferRenderer.drawWithGlobalProgram(b.end());
        } finally {
            savedState.close();
        }
    }

    private static void set1(ShaderProgram s, String n, float a) {
        var u = s.getUniform(n);
        if (u != null) u.set(a);
    }

    private static void set2(ShaderProgram s, String n, float a, float b) {
        var u = s.getUniform(n);
        if (u != null) u.set(a, b);
    }

    private static void set4(ShaderProgram s, String n, float a, float b, float c, float d) {
        var u = s.getUniform(n);
        if (u != null) u.set(a, b, c, d);
    }
}
