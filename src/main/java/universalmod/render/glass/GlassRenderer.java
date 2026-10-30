package universalmod.render.glass;

import universalmod.render.GlUiState;

import universalmod.render.UiShaders;
import universalmod.render.ShaderDiagnostics;
import universalmod.render.UiRender;
import universalmod.render.blur.KawaseBlurRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;

public final class GlassRenderer {
    private GlassRenderer() {
    }

    public static void glass(float x, float y, float w, float h, float radius, float globalAlpha, float fresnelPower, int fresnelColor, float baseAlpha, boolean fresnelInvert, float fresnelMix, float distortStrength) {
        glass(x, y, w, h, radius, radius, radius, radius,
                globalAlpha, fresnelPower, fresnelColor, baseAlpha,
                fresnelInvert, fresnelMix, distortStrength);
    }

    public static void glass(float x, float y, float w, float h, float rtl, float rtr, float rbr, float rbl,
                             float globalAlpha, float fresnelPower, int fresnelColor, float baseAlpha,
                             boolean fresnelInvert, float fresnelMix, float distortStrength) {
        if (w <= 0 || h <= 0 || globalAlpha <= 0) return;
        GlUiState savedState = GlUiState.capture();
        ShaderProgram shader = prepare(UiShaders.GLASS, "ui_glass", w, h);
        if (shader == null) { savedState.close(); return; }
        try {
            common(shader, w, h, rtl, rtr, rbr, rbl, 0.5f, globalAlpha,
                    fresnelPower, fresnelColor, baseAlpha, fresnelInvert, fresnelMix, distortStrength);
            quad(x, y, w, h);
        } finally {
            finish();
            savedState.close();
        }
    }

    public static void outline(float x, float y, float w, float h, float radius, float thickness, int tint, float globalAlpha, float fresnelPower, int fresnelColor, float baseAlpha, boolean fresnelInvert, float fresnelMix, float distortStrength, float shinePhase) {
        outline(x, y, w, h, radius, radius, radius, radius, thickness, tint,
                globalAlpha, fresnelPower, fresnelColor, baseAlpha, fresnelInvert,
                fresnelMix, distortStrength, shinePhase);
    }

    public static void outline(float x, float y, float w, float h, float rtl, float rtr, float rbr, float rbl,
                               float thickness, int tint, float globalAlpha, float fresnelPower,
                               int fresnelColor, float baseAlpha, boolean fresnelInvert,
                               float fresnelMix, float distortStrength, float shinePhase) {
        if (w <= 0 || h <= 0 || thickness <= 0 || globalAlpha <= 0) return;
        GlUiState savedState = GlUiState.capture();
        ShaderProgram shader = prepare(UiShaders.GLASS_OUTLINE, "ui_glass_outline", w, h);
        if (shader == null) { savedState.close(); return; }
        try {
            common(shader, w, h, rtl, rtr, rbr, rbl, 1.0f, globalAlpha,
                    fresnelPower, fresnelColor, baseAlpha, fresnelInvert, fresnelMix, distortStrength);
            set1(shader, "Thickness", thickness);
            set1(shader, "ShinePhase", shinePhase);
            setColor(shader, "TintColor", tint);
            quad(x, y, w, h);
        } finally {
            finish();
            savedState.close();
        }
    }

    public static void draw(net.minecraft.client.gui.DrawContext context, GlassQuad glass) {
        if (glass == null || !glass.visible()) return;
        float limit = Math.max(0.0f, Math.min(glass.width(), glass.height()) * 0.5f);
        GlUiState savedState = GlUiState.capture();
        ShaderProgram shader = prepare(UiShaders.GLASS, "ui_glass", glass.width(), glass.height());
        if (shader == null) { savedState.close(); return; }
        try {
            common(shader, glass.width(), glass.height(),
                    clamp(glass.radiusTopLeft(), limit), clamp(glass.radiusTopRight(), limit),
                    clamp(glass.radiusBottomRight(), limit), clamp(glass.radiusBottomLeft(), limit),
                    0.5f, clamp01(glass.globalAlpha()), Math.max(0.001f, glass.fresnelPower()),
                    glass.fresnelColor(), clamp01(glass.baseAlpha()), glass.fresnelInvert(),
                    clamp01(glass.fresnelMix()), glass.distortStrength());
            
            quad(glass.x(), glass.y(), glass.width(), glass.height(), glass.z());
        } finally {
            finish();
            savedState.close();
        }
    }

    public static void draw(net.minecraft.client.gui.DrawContext context, GlassOutline shape) {
        if (shape == null || !shape.visible()) return;
        float limit = Math.max(0.0f, Math.min(shape.width(), shape.height()) * 0.5f);
        GlUiState savedState = GlUiState.capture();
        ShaderProgram shader = prepare(UiShaders.GLASS_OUTLINE, "ui_glass_outline", shape.width(), shape.height());
        if (shader == null) { savedState.close(); return; }
        try {
            common(shader, shape.width(), shape.height(),
                    clamp(shape.radiusTopLeft(), limit), clamp(shape.radiusTopRight(), limit),
                    clamp(shape.radiusBottomRight(), limit), clamp(shape.radiusBottomLeft(), limit),
                    Math.max(0.5f, shape.smoothness()), clamp01(shape.globalAlpha()),
                    Math.max(0.001f, shape.fresnelPower()), shape.fresnelColor(),
                    clamp01(shape.baseAlpha()), shape.fresnelInvert(), clamp01(shape.fresnelMix()),
                    shape.distortStrength());
            set1(shader, "Thickness", clamp(shape.thickness(), limit));
            
            set1(shader, "ShinePhase", Math.max(0.001f, shape.squirt()));
            setColor(shader, "TintColor", shape.color());
            quad(shape.x(), shape.y(), shape.width(), shape.height(), shape.z());
        } finally {
            finish();
            savedState.close();
        }
    }

    private static ShaderProgram prepare(ShaderProgram key, String name, float w, float h) {
        int blurredTexture = KawaseBlurRenderer.prepareGlass(30.0f, w, h);
        if (blurredTexture == 0) return null;
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShaderTexture(0, blurredTexture);
        ShaderProgram shader = UiShaders.bind(key, name);
        if (shader == null) {
            ShaderDiagnostics.missing(name);
            finish();
            return null;
        }
        return shader;
    }

    private static void common(ShaderProgram shader, float w, float h,
                               float rtl, float rtr, float rbr, float rbl, float smoothness,
                               float globalAlpha, float fresnelPower, int fresnelColor, float baseAlpha,
                               boolean invert, float fresnelMix, float distortStrength) {
        Framebuffer main = MinecraftClient.getInstance().getFramebuffer();
        set2(shader, "Size", w, h);
        float limit = Math.max(0f, Math.min(w, h) * .5f);
        set4(shader, "Radius", clamp(rtl, limit), clamp(rtr, limit),
                clamp(rbr, limit), clamp(rbl, limit));
        set1(shader, "Smoothness", smoothness);
        set4(shader, "BlurRegion", 0f, 0f, Math.max(1, main.textureWidth), Math.max(1, main.textureHeight));
        set1(shader, "GlobalAlpha", globalAlpha);
        set1(shader, "FresnelPower", Math.max(0.001f, fresnelPower));
        set1(shader, "BaseAlpha", baseAlpha);
        set1(shader, "FresnelMix", fresnelMix);
        set1(shader, "FresnelInvert", invert ? 1.0f : 0.0f);
        set1(shader, "DistortStrength", distortStrength);
        setColor(shader, "FresnelColor", fresnelColor);
    }

    private static void quad(float x, float y, float w, float h) {
        quad(x, y, w, h, 0f);
    }

    private static void quad(float x, float y, float w, float h, float z) {
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        b.vertex(UiRender.matrix(), x, y, z).texture(0.0f, 0.0f).color(0xFFFFFFFF).next();
        b.vertex(UiRender.matrix(), x, y + h, z).texture(0.0f, 1.0f).color(0xFFFFFFFF).next();
        b.vertex(UiRender.matrix(), x + w, y + h, z).texture(1.0f, 1.0f).color(0xFFFFFFFF).next();
        b.vertex(UiRender.matrix(), x + w, y, z).texture(1.0f, 0.0f).color(0xFFFFFFFF).next();
        BufferRenderer.drawWithGlobalProgram(b.end());
    }

    private static void finish() {
        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
    }

    private static float clamp(float r, float limit) { return Math.max(0f, Math.min(r, limit)); }
    private static float clamp01(float value) { return Math.max(0f, Math.min(value, 1f)); }

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

    private static void setColor(ShaderProgram s, String n, int c) {
        var u = s.getUniform(n);
        if (u != null)
            u.set(((c >>> 16) & 255) / 255f, ((c >>> 8) & 255) / 255f, (c & 255) / 255f, ((c >>> 24) & 255) / 255f);
    }
}
