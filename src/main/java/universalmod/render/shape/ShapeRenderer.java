package universalmod.render.shape;

import universalmod.render.GlUiState;

import universalmod.render.UiShaders;
import universalmod.render.ShaderDiagnostics;
import universalmod.render.UiRender;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;

public final class ShapeRenderer {
    private ShapeRenderer() {
    }

    public static void rect(float x, float y, float w, float h, float r1, float r2, float r3, float r4, float smoothness, int c1, int c2, int c3, int c4) {
        rectWithSplit(x, y, w, h, r1, r2, r3, r4, smoothness,
                c1, c2, c3, c4, -1f, 0);
    }

     
    public static void rect(RectangleShape shape) {
        if (shape == null || !shape.visible()) return;
        rectWithSplit(shape.x(), shape.y(), shape.width(), shape.height(),
                shape.radiusTopLeft(), shape.radiusTopRight(), shape.radiusBottomRight(), shape.radiusBottomLeft(),
                shape.smoothness(), shape.colorTopLeft(), shape.colorTopRight(),
                shape.colorBottomRight(), shape.colorBottomLeft(), shape.verticalSplit(), shape.splitColor());
    }

    private static void rectWithSplit(float x, float y, float w, float h,
            float r1, float r2, float r3, float r4, float smoothness,
            int c1, int c2, int c3, int c4, float verticalSplit, int splitColor) {
        if (w <= 0 || h <= 0) return;
        try (GlUiState ignored = GlUiState.capture()) {
            begin();
            ShaderProgram shader = UiShaders.bind(UiShaders.RECT, "ui_rect");
            if (shader == null) {
                ShaderDiagnostics.missing("ui_rect");
                return;
            }
            set2(shader, "Size", w, h);
            set4(shader, "Radius", r1, r2, r3, r4);
            set1(shader, "Smoothness", Math.max(0.001f, smoothness));
            setColor(shader, "TopLeft", c1);
            setColor(shader, "TopRight", c2);
            setColor(shader, "BottomRight", c3);
            setColor(shader, "BottomLeft", c4);
            set1(shader, "VerticalSplit", verticalSplit);
            setColor(shader, "SplitColor", splitColor);
            quad(x, y, w, h);
        }
    }

    public static void outline(float x, float y, float w, float h, float r1, float r2, float r3, float r4, float thickness, float smoothness, int c1, int c2, int c3, int c4) {
        if (w <= 0 || h <= 0 || thickness <= 0) return;
        try (GlUiState ignored = GlUiState.capture()) {
            begin();
            ShaderProgram shader = UiShaders.bind(UiShaders.OUTLINE, "ui_outline");
            if (shader == null) {
                ShaderDiagnostics.missing("ui_outline");
                return;
            }
            set2(shader, "Size", w, h);
            set4(shader, "Radius", r1, r2, r3, r4);
            set1(shader, "Thickness", thickness);
            set1(shader, "Smoothness", Math.max(0.001f, smoothness));
            setColor(shader, "TopLeft", c1);
            setColor(shader, "TopRight", c2);
            setColor(shader, "BottomRight", c3);
            setColor(shader, "BottomLeft", c4);
            quad(x, y, w, h);
        }
    }

    private static void begin() {
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static void end() {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
    }

    private static void quad(float x, float y, float w, float h) {
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        b.vertex(UiRender.matrix(), x, y, 0).texture(0.0f, 0.0f).color(0xFFFFFFFF).next();
        b.vertex(UiRender.matrix(), x, y + h, 0).texture(0.0f, 1.0f).color(0xFFFFFFFF).next();
        b.vertex(UiRender.matrix(), x + w, y + h, 0).texture(1.0f, 1.0f).color(0xFFFFFFFF).next();
        b.vertex(UiRender.matrix(), x + w, y, 0).texture(1.0f, 0.0f).color(0xFFFFFFFF).next();
        BufferRenderer.drawWithGlobalProgram(b.end());
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

    private static void setColor(ShaderProgram shader, String name, int argb) {
        set4(shader, name, ((argb >>> 16) & 255) / 255.0f, ((argb >>> 8) & 255) / 255.0f, (argb & 255) / 255.0f, ((argb >>> 24) & 255) / 255.0f);
    }
}
