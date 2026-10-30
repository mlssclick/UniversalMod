package universalmod.render;

import universalmod.render.GlUiState;

import universalmod.render.font.msdf.MsdfFont;
import universalmod.render.image.ImageRenderer;
import universalmod.render.effect.EffectIconRenderer;
import universalmod.render.glass.GlassRenderer;
import universalmod.render.hud.HudChromeRenderer;
import universalmod.render.blur.KawaseBlurRenderer;
import universalmod.render.blur.BlurRenderer;
import universalmod.render.shape.ShapeRenderer;

import java.util.function.Supplier;
import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.client.texture.Sprite;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import universalmod.module.utility.NameProtect;

public final class UiRender {
    private static final Matrix4f FRAME_MATRIX = new Matrix4f();
    private static GlUiState frameState;
    private static final Deque<Matrix4f> MATRIX_OVERRIDES = new ArrayDeque<>();

    private UiRender() {
    }

    public static void beginFrame(DrawContext context) {
        beginFrame(context, context.getMatrices().peek().getPositionMatrix());
    }

    public static void beginFrame(DrawContext context, Matrix4f matrix) {
        if (frameState != null) { frameState.close(); frameState = null; }
        frameState = GlUiState.capture();
        KawaseBlurRenderer.beginFrame();
        FRAME_MATRIX.set(matrix == null ? context.getMatrices().peek().getPositionMatrix() : matrix);
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.disableCull();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    public static Matrix4f matrix() {
        Matrix4f override = MATRIX_OVERRIDES.peekLast();
        return override == null ? FRAME_MATRIX : override;
    }

    public static void pushMatrix(Matrix4f matrix) {
        MATRIX_OVERRIDES.addLast(matrix == null ? new Matrix4f(FRAME_MATRIX) : new Matrix4f(matrix));
    }

    public static void popMatrix() {
        if (!MATRIX_OVERRIDES.isEmpty()) MATRIX_OVERRIDES.removeLast();
    }

    public static void endFrame() {
        try {
            KawaseBlurRenderer.endFrame();
        } finally {
            MATRIX_OVERRIDES.clear();
            if (frameState != null) {
                frameState.close();
                frameState = null;
            }
        }
    }

    public static int rgba(int r, int g, int b, int a) {
        r = clamp255(r);
        g = clamp255(g);
        b = clamp255(b);
        a = clamp255(a);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int alpha(int color, float multiplier) {
        int a = Math.round(((color >>> 24) & 0xFF) * Math.max(0.0f, Math.min(1.0f, multiplier)));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    public static int lerp(int a, int b, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        int aa = (a >>> 24) & 255, ar = (a >>> 16) & 255, ag = (a >>> 8) & 255, ab = a & 255;
        int ba = (b >>> 24) & 255, br = (b >>> 16) & 255, bg = (b >>> 8) & 255, bb = b & 255;
        return rgba(Math.round(ar + (br - ar) * t), Math.round(ag + (bg - ag) * t), Math.round(ab + (bb - ab) * t), Math.round(aa + (ba - aa) * t));
    }

    public static void rect(float x, float y, float w, float h, float radius, int color) {
        rect(x, y, w, h, radius, radius, radius, radius, color, color, color, color);
    }

    public static void rect(float x, float y, float w, float h, float r1, float r2, float r3, float r4, int color) {
        rect(x, y, w, h, r1, r2, r3, r4, color, color, color, color);
    }

    public static void rect(float x, float y, float w, float h, float radius, int c1, int c2, int c3, int c4) {
        rect(x, y, w, h, radius, radius, radius, radius, c1, c2, c3, c4);
    }

    public static void rect(float x, float y, float w, float h, float r1, float r2, float r3, float r4, int c1, int c2, int c3, int c4) {
        if (w <= 0 || h <= 0) return;
        universalmod.render.shape.RectangleRenderer.getInstance().enqueue(
                new universalmod.render.shape.RectangleShape(x, y, w, h,
                        r1, r2, r3, r4, c1, c2, c3, c4, 1.0f));
    }

    public static void outline(float x, float y, float w, float h, float radius, float thickness, int color) {
        if (w <= 0 || h <= 0 || thickness <= 0) return;
        universalmod.render.shape.OutlineRenderer.getInstance().enqueue(
                new universalmod.render.shape.OutlineShape(x, y, w, h, radius, thickness, color));
    }

    public static void blur(float x, float y, float w, float h, float radius, float blurRadius, float smoothness, int color) {
        if (w <= 0 || h <= 0) return;
        BlurRenderer.blur(x, y, w, h, radius, radius, radius, radius, blurRadius, smoothness, color);
    }

    public static void blur(float x, float y, float w, float h, float r1, float r2, float r3, float r4, float blurRadius, float smoothness, int cTL, int cTR, int cBR, int cBL) {
        if (w <= 0 || h <= 0) return;
        BlurRenderer.blur(x, y, w, h, r1, r2, r3, r4, blurRadius, smoothness, cTL, cTR, cBR, cBL);
    }

    public static void glass(float x, float y, float w, float h, float radius, float globalAlpha, float fresnelPower, int fresnelColor, float baseAlpha, boolean fresnelInvert, float fresnelMix, float distortStrength) {
        GlassRenderer.glass(x, y, w, h, radius, globalAlpha, fresnelPower, fresnelColor, baseAlpha, fresnelInvert, fresnelMix, distortStrength);
    }

    public static void glass(float x, float y, float w, float h,
                             float rtl, float rtr, float rbr, float rbl,
                             float globalAlpha, float fresnelPower, int fresnelColor,
                             float baseAlpha, boolean fresnelInvert, float fresnelMix,
                             float distortStrength) {
        GlassRenderer.glass(x, y, w, h, rtl, rtr, rbr, rbl, globalAlpha,
                fresnelPower, fresnelColor, baseAlpha, fresnelInvert, fresnelMix, distortStrength);
    }

    public static void glassOutline(float x, float y, float w, float h, float radius, float thickness, int tint, float globalAlpha, float fresnelPower, int fresnelColor, float baseAlpha, boolean fresnelInvert, float fresnelMix, float distortStrength, float shinePhase) {
        GlassRenderer.outline(x, y, w, h, radius, thickness, tint, globalAlpha, fresnelPower, fresnelColor, baseAlpha, fresnelInvert, fresnelMix, distortStrength, shinePhase);
    }

    public static void hudChrome(float x, float y, float w, float h, float topLeft, float topRight,
                                 float bottomRight, float bottomLeft, float alpha, float smoothness, float darkness) {
        universalmod.render.hud.HudSurfaceRenderer.getInstance().enqueue(
                new universalmod.render.hud.HudSurface(x, y, w, h, topLeft, topRight,
                        bottomRight, bottomLeft, alpha, smoothness, darkness));
    }

    public static void hudChrome(float x, float y, float w, float h, float radius, float alpha, float smoothness, float darkness) {
        universalmod.render.hud.HudSurfaceRenderer.getInstance().enqueue(
                new universalmod.render.hud.HudSurface(x, y, w, h, radius, alpha, smoothness, darkness));
    }

    public static void effectIcon(Sprite sprite, float x, float y, float size, int color) {
        EffectIconRenderer.draw(sprite, x, y, size, color);
    }

    public static void text(Supplier<MsdfFont> font, String text, float x, float y, float size, int color) {
        if (text == null || text.isEmpty() || size <= 0.0f) return;
        font.get().draw(NameProtect.replace(text), x, y, size, color);
    }

    public static float textWidth(Supplier<MsdfFont> font, String text, float size) {
        return text == null || text.isEmpty() ? 0.0f : font.get().width(NameProtect.replace(text), size);
    }

    public static float lineHeight(Supplier<MsdfFont> font, float size) {
        return font.get().lineHeight(size);
    }

    public static void image(String path, float x, float y, float w, float h, float radius, int color) {
        if (path == null || path.isBlank()) return;
        Identifier id = ImageRenderer.resolveIdentifier(path);
        ImageRenderer.draw(id, 0f, 0f, 1f, 1f, x, y, w, h, radius, 1.0f, color);
    }

    public static void image(Identifier id, float x, float y, float w, float h, float radius, int color) {
        if (id == null) return;
        ImageRenderer.draw(id, 0f, 0f, 1f, 1f, x, y, w, h, radius, 1.0f, color);
    }

    public static void texture(Identifier id, float u, float v, float texW, float texH, float x, float y, float w, float h, float radius, int color) {
        ImageRenderer.draw(id, u, v, u + texW, v + texH, x, y, w, h, radius, 1.0f, color);
    }

    public static void image(Identifier id, float u0, float v0, float u1, float v1, float x, float y, float w, float h, float radius, float smoothness, int cTL, int cTR, int cBR, int cBL, boolean nearest) {
        ImageRenderer.draw(id, u0, v0, u1, v1, x, y, w, h, radius, smoothness, cTL, cTR, cBR, cBL, nearest);
    }

    public static void pushScissor(float x, float y, float w, float h) {
        ScissorStack.push(x, y, w, h);
    }

    public static void popScissor() {
        ScissorStack.pop();
    }

    public static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
