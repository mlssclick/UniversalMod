package universalmod.render;

import universalmod.render.color.ColorUtil;
import universalmod.render.font.FontType;
import universalmod.render.blur.BuiltBlur;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.Sprite;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import universalmod.render.font.msdf.Fonts;
import universalmod.render.font.msdf.MsdfFont;
import universalmod.render.UiRender;

import java.util.function.Supplier;
import java.util.ArrayDeque;
import java.util.Deque;

public final class Render2D {
    public record ProjectedPoint(float x, float y) {
    }

    public record ProjectedRect(float x, float y, float width, float height) {
    }

    public record TextVisualBounds(float minX, float minY, float maxX, float maxY) {
        public static final TextVisualBounds EMPTY = new TextVisualBounds(0, 0, 0, 0);

        public float width() {
            return Math.max(0, maxX - minX);
        }

        public float height() {
            return Math.max(0, maxY - minY);
        }

        public float centerY() {
            return (minY + maxY) * .5f;
        }

        public boolean empty() {
            return width() <= 0 || height() <= 0;
        }
    }

    @FunctionalInterface
    public interface PointProjector {
        ProjectedPoint project(float x, float y);
    }

    private static DrawContext currentGraphics;
    private static final PointProjector IDENTITY_PROJECTOR = ProjectedPoint::new;
    private static final Deque<PointProjector> POINT_PROJECTORS = new ArrayDeque<>();
    private static int projectionOverrideDepth;

    private Render2D() {
    }

    public static int getFixedScaledWidth() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc == null || mc.getWindow() == null ? 1 : Math.max(1, (int) Math.ceil(mc.getWindow().getWidth() / Render2DCoordinateSpace.designGuiScale()));
    }

    public static int getFixedScaledHeight() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc == null || mc.getWindow() == null ? 1 : Math.max(1, (int) Math.ceil(mc.getWindow().getHeight() / Render2DCoordinateSpace.designGuiScale()));
    }

    public static float guiToFixed(float value) {
        float s = Render2DCoordinateSpace.guiIndependentScale();
        return Math.abs(s) < .0001f ? value : value / s;
    }

    public static double guiToFixed(double value) {
        float s = Render2DCoordinateSpace.guiIndependentScale();
        return Math.abs(s) < .0001f ? value : value / s;
    }

    public static float fixedToGui(float value) {
        return value * Render2DCoordinateSpace.guiIndependentScale();
    }

    public static double fixedToGui(double value) {
        return value * Render2DCoordinateSpace.guiIndependentScale();
    }


     
    public static void hudSurface(float x, float y, float width, float height,
                                  float radius, float alpha, float smoothness, float darkness) {
        hudChrome(x, y, width, height, radius, alpha, smoothness, darkness);
    }

    public static void circle(float cx, float cy, float diameter, int color) {
        if (diameter <= 0.0f) return;
        float half = diameter * 0.5f;
        rect(cx - half, cy - half, diameter, diameter, half, color);
    }

    public static void circleOutline(float cx, float cy, float diameter, float thickness, int color) {
        if (diameter <= 0.0f || thickness <= 0.0f) return;
        float half = diameter * 0.5f;
        outline(cx - half, cy - half, diameter, diameter, half, thickness, color);
    }
    public static void beginFrame(DrawContext graphics) {
        currentGraphics = graphics;
        if (graphics == null) return;
        Matrix4f matrix = new Matrix4f(graphics.getMatrices().peek().getPositionMatrix());
        float scale = Render2DCoordinateSpace.guiIndependentScale();
        matrix.scale(scale, scale, 1f);
        UiRender.beginFrame(graphics, matrix);
        Render2DBackends.beginFrame(graphics);
    }

    public static void flush() {
        Render2DBackends.flush();
        RenderScissorStack.clear();
        UiRender.endFrame();
        POINT_PROJECTORS.clear();
        projectionOverrideDepth = 0;
        currentGraphics = null;
    }

    public static DrawContext currentGraphics() {
        return currentGraphics;
    }

    public static void withCurrentMatrix(Runnable block) {
        if (currentGraphics == null) {
            block.run();
            return;
        }
        Matrix4f matrix = new Matrix4f(currentGraphics.getMatrices().peek().getPositionMatrix());
        float scale = Render2DCoordinateSpace.guiIndependentScale();
        matrix.scale(scale, scale, 1f);
        UiRender.pushMatrix(matrix);
        try {
            block.run();
        } finally {
            UiRender.popMatrix();
        }
    }

     
    public static void pushProjection(PointProjector projector) {
        projectionOverrideDepth++;
        POINT_PROJECTORS.addLast(projector == null ? IDENTITY_PROJECTOR : projector);
    }

    public static void popProjection() {
        if (!POINT_PROJECTORS.isEmpty()) POINT_PROJECTORS.removeLast();
        if (projectionOverrideDepth > 0) projectionOverrideDepth--;
    }

    public static PointProjector getCurrentProjector() {
        return POINT_PROJECTORS.peekLast();
    }

    public static void withProjection(Matrix4f projection, PointProjector projector, Runnable block) {
        if (block == null) return;
        pushProjection(projector);
        UiRender.pushMatrix(projection == null ? UiRender.matrix() : projection);
        try {
            block.run();
        } finally {
            UiRender.popMatrix();
            popProjection();
        }
    }

    public static boolean hasProjectionOverride() {
        return projectionOverrideDepth > 0;
    }

    public static ProjectedRect projectRect(float x, float y, float width, float height) {
        PointProjector projector = getCurrentProjector();
        if (projector == null) return new ProjectedRect(x, y, width, height);
        ProjectedPoint[] points = {
                projector.project(x, y),
                projector.project(x + width, y),
                projector.project(x + width, y + height),
                projector.project(x, y + height)
        };
        float left = Float.POSITIVE_INFINITY;
        float top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY;
        float bottom = Float.NEGATIVE_INFINITY;
        boolean valid = false;
        for (ProjectedPoint point : points) {
            if (point == null || !Float.isFinite(point.x()) || !Float.isFinite(point.y())) continue;
            valid = true;
            left = Math.min(left, point.x());
            top = Math.min(top, point.y());
            right = Math.max(right, point.x());
            bottom = Math.max(bottom, point.y());
        }
        return valid ? new ProjectedRect(left, top, Math.max(0.0f, right - left), Math.max(0.0f, bottom - top))
                : null;
    }

    public static void rect(float x, float y, float w, float h, int c) {
        rect(x, y, w, h, 0, c);
    }

    public static void rect(float x, float y, float w, float h, float r, int c) {
        UiRender.rect(x, y, w, h, r, c);
    }

    public static void rect(float x, float y, float w, float h, float r, int tl, int tr, int br, int bl) {
        UiRender.rect(x, y, w, h, r, tl, tr, br, bl);
    }

    public static void rect(float x, float y, float w, float h, float rtl, float rtr, float rbr, float rbl, int c) {
        UiRender.rect(x, y, w, h, rtl, rtr, rbr, rbl, c);
    }

    public static void rect(float x, float y, float w, float h, float rtl, float rtr, float rbr, float rbl, int tl, int tr, int br, int bl) {
        UiRender.rect(x, y, w, h, rtl, rtr, rbr, rbl, tl, tr, br, bl);
    }

    public static void outline(float x, float y, float w, float h, float r, float t, int c) {
        UiRender.outline(x, y, w, h, r, t, c);
    }

    public static void blur(float x, float y, float w, float h, float radius) {
        blur(x, y, w, h, radius, 16f, 1f, ColorUtil.WHITE);
    }

    public static void blur(float x, float y, float w, float h, float radius, float blurRadius) {
        blur(x, y, w, h, radius, blurRadius, 1f, ColorUtil.WHITE);
    }

    public static void blur(float x, float y, float w, float h, float radius, float blurRadius, float smoothness, int color) {
        UiRender.blur(x, y, w, h, radius, blurRadius, smoothness, color);
    }

    public static void blur(float x, float y, float w, float h, float radius, float blurRadius, float smoothness, int tl, int tr, int br, int bl) {
        UiRender.blur(x, y, w, h, radius, radius, radius, radius, blurRadius, smoothness, tl, tr, br, bl);
    }

    public static void blur(universalmod.render.blur.BlurPass blur) {
        if (blur != null) blur.render(currentGraphics);
    }

    public static void blur(BuiltBlur blur) {
        if (blur != null)
            UiRender.blur(blur.x(), blur.y(), blur.width(), blur.height(), blur.radiusTopLeft(), blur.radiusTopRight(), blur.radiusBottomRight(), blur.radiusBottomLeft(), blur.blurRadius(), blur.smoothness(), blur.colorTopLeft(), blur.colorTopRight(), blur.colorBottomRight(), blur.colorBottomLeft());
    }

    public static void hudChrome(float x, float y, float w, float h, float topLeft, float topRight,
                                 float bottomRight, float bottomLeft, float alpha, float smoothness, float darkness) {
        UiRender.hudChrome(x, y, w, h, topLeft, topRight, bottomRight, bottomLeft, alpha, smoothness, darkness);
    }

    public static void hudChrome(float x, float y, float w, float h, float radius, float alpha, float smoothness, float darkness) {
        UiRender.hudChrome(x, y, w, h, radius, alpha, smoothness, darkness);
    }

    public static void glass(float x, float y, float w, float h, float radius, int color, float globalAlpha, float fresnelPower, int fresnelColor, float baseAlpha, boolean invert, float fresnelMix, float distort, float squirt, float z) {
        UiRender.glass(x, y, w, h, radius, globalAlpha, fresnelPower, fresnelColor, baseAlpha, invert, fresnelMix, distort);
    }

    public static void glass(float x, float y, float w, float h, float rtl, float rtr, float rbr, float rbl, int color, float globalAlpha, float fresnelPower, int fresnelColor, float baseAlpha, boolean invert, float fresnelMix, float distort, float squirt, float z) {
        UiRender.glass(x, y, w, h, rtl, rtr, rbr, rbl, globalAlpha,
                fresnelPower, fresnelColor, baseAlpha, invert, fresnelMix, distort);
    }

    public static void glassOutline(float x, float y, float w, float h, float radius, float thickness, int tint, float globalAlpha, float fresnelPower, int fresnelColor, float baseAlpha, boolean invert, float fresnelMix, float distort, float squirt, float z, float shinePhase) {
        UiRender.glassOutline(x, y, w, h, radius, thickness, tint, globalAlpha, fresnelPower, fresnelColor, baseAlpha, invert, fresnelMix, distort, shinePhase);
    }

     


    public static void withTextBatch(Runnable textOnlyDraw) {
        universalmod.render.font.msdf.MsdfTextBatch.run(textOnlyDraw);
    }

    public static void text(FontType font, String text, float x, float y, float size, int color) {
        UiRender.text(font(font), text, x, y, size, color);
    }

    public static void text(String font, String text, float x, float y, float size, int color) {
        text(FontType.resolve(font), text, x, y, size, color);
    }

    public static void text(FontType font, String text, float x, float y, float size, int tl, int tr, int br, int bl) {
        if (text == null || text.isEmpty() || size <= 0f) return;
        font(font).get().drawGradient(universalmod.module.utility.NameProtect.replace(text),
                x, y, size, tl, tr, br, bl);
    }

    public static void text(FontType font, String text, float x, float y, float size, int color, float rotation, float ox, float oy) {
        if (Math.abs(rotation) < .001f) {
            text(font, text, x, y, size, color);
            return;
        }
        Matrix4f m = new Matrix4f(UiRender.matrix());
        m.translate(ox, oy, 0).rotateZ((float) Math.toRadians(rotation)).translate(-ox, -oy, 0);
        UiRender.pushMatrix(m);
        try {
            text(font, text, x, y, size, color);
        } finally {
            UiRender.popMatrix();
        }
    }

    public static void animatedGlyphText(FontType font, String text, float x, float y, float size, int color, int firstGlyphIndex, String previousFirst, float firstProgress, int secondGlyphIndex, String previousSecond, float secondProgress, float verticalOffset) {
        if (font == null || text == null || text.isEmpty()) return;
        int index = 0;
        while (index < text.length()) {
            int start = index;
            int cp = text.codePointAt(index);
            index += Character.charCount(cp);
            String glyph = new String(Character.toChars(cp));
            float gx = x + textWidth(font, text.substring(0, index), size) - textWidth(font, glyph, size);
            if (start == firstGlyphIndex)
                drawAnimatedGlyph(font, glyph, previousFirst, gx, y, size, color, firstProgress, verticalOffset);
            else if (start == secondGlyphIndex)
                drawAnimatedGlyph(font, glyph, previousSecond, gx, y, size, color, secondProgress, verticalOffset);
            else text(font, glyph, gx, y, size, color);
        }
    }

    private static void drawAnimatedGlyph(FontType font, String current, String previous, float x, float y, float size, int color, float progress, float offset) {
        float p = Math.max(0f, Math.min(1f, progress));
        if (previous != null && !previous.isEmpty() && p < .999f)
            text(font, previous, x, y + offset * p, size, scaleAlpha(color, 1 - p));
        text(font, current, x, y - offset + offset * p, size, scaleAlpha(color, p));
    }

    private static int scaleAlpha(int c, float m) {
        int a = (c >>> 24) & 255;
        return (Math.round(a * Math.max(0f, Math.min(1f, m))) << 24) | (c & 0x00ffffff);
    }

    public static void textFade(FontType font, String text, float x, float y, float size, int color, float left, float right, float width, boolean fadeLeft, boolean fadeRight) {
        textFade(font, text, x, y, size, color, left, right, width, fadeLeft ? 1f : 0f, fadeRight ? 1f : 0f);
    }

    public static void textFade(FontType font, String text, float x, float y, float size, int color, float left, float right, float width, float ls, float rs) {
        font(font).get().drawFade(text, x, y, size, color, left, right, width, ls, rs);
    }

    public static float textWidth(FontType font, String text, float size) {
        return UiRender.textWidth(font(font), text, size);
    }

    public static float textWidth(String font, String text, float size) {
        return textWidth(FontType.resolve(font), text, size);
    }

    public static float textHeight(FontType font, String text, float size) {
        return font == null || text == null || text.isEmpty() || size <= 0 ? 0 : size;
    }

    public static TextVisualBounds textVisualBounds(FontType font, String text, float size) {
        var b = font(font).get().visualBounds(text, size);
        return b.empty() ? TextVisualBounds.EMPTY : new TextVisualBounds(b.minX(), b.minY(), b.maxX(), b.maxY());
    }

    public static float textGlyphPositionX(FontType font, String text, int glyphIndex, float size) {
        if (text == null || glyphIndex <= 0) return 0;
        return textWidth(font, text.substring(0, Math.min(glyphIndex, text.length())), size);
    }

    public static void warmupText(FontType font, String text, float size) {
        font(font).get();
    }

    public static void image(String texture, float x, float y, float size, float radius) {
        image(new universalmod.render.image.ImageCommand(texture, x, y, size, radius));
    }

    public static void image(String texture, float x, float y, float size, float radius, int color) {
        image(new universalmod.render.image.ImageCommand(texture, x, y, size, radius, color));
    }

    public static void image(universalmod.render.image.ImageCommand command) {
        universalmod.render.image.ImageRenderer.draw(command);
    }

    public static void image(String texture, float x, float y, float w, float h, float radius, int color) {
        Identifier id = id(texture);
        UiRender.image(id, 0, 0, 1, 1, x, y, w, h, radius, 1f, color, color, color, color, false);
    }

    public static void image(String texture, float x, float y, float w, float h, float radius, int... colors) {
        int[] c = colors(colors);
        Identifier id = id(texture);
        UiRender.image(id, 0, 0, 1, 1, x, y, w, h, radius, 1f, c[0], c[1], c[2], c[3], false);
    }

    public static void image(String texture, float x, float y, float size, float radius,
                             float rotation, float originX, float originY, int color) {
        image(new universalmod.render.image.ImageCommand(texture, x, y, size, radius, color)
                .withRotation(rotation, originX, originY));
    }

    public static void imageUv(String texture, float x, float y, float w, float h, float radius, float u0, float v0, float u1, float v1, int color) {
        UiRender.image(id(texture), u0, v0, u1, v1, x, y, w, h, radius, 1f, color, color, color, color, false);
    }

    public static void imageUvNearest(String texture, float x, float y, float w, float h, float radius, float smoothness, float u0, float v0, float u1, float v1, int color) {
        UiRender.image(id(texture), u0, v0, u1, v1, x, y, w, h, radius, smoothness, color, color, color, color, true);
    }

    public static void effectIcon(StatusEffectInstance effect, float x, float y, float size) {
        effectIcon(effect, x, y, size, ColorUtil.WHITE);
    }

    public static void effectIcon(StatusEffectInstance effect, float x, float y, float size, int color) {
        if (effect == null || !effect.shouldShowIcon()) return;
        effectIcon(effect.getEffectType(), x, y, size, color);
    }

    public static void effectIcon(StatusEffect effect, float x, float y, float size, int color) {
        if (effect == null) return;
        Sprite sprite = MinecraftClient.getInstance().getStatusEffectSpriteManager().getSprite(effect);
        UiRender.effectIcon(sprite, x, y, size, color);
    }

    public static void pushScissor(DrawContext graphics, float x, float y, float w, float h) {
        ProjectedRect bounds = hasProjectionOverride() ? projectRect(x, y, w, h) : new ProjectedRect(x, y, w, h);
        
        if (bounds == null) bounds = new ProjectedRect(0, 0, 0, 0);
        RenderScissorStack.push(bounds.x(), bounds.y(), bounds.width(), bounds.height());
    }

    public static void popScissor(DrawContext graphics) {
        RenderScissorStack.pop();
    }

    private static Supplier<MsdfFont> font(FontType f) {
        if (f == null) f = FontType.DEFAULT;
        return switch (f) {
            case BOLD, SEMIBOLD -> Fonts.BOLD;
            case ONEST -> Fonts.ONEST;
            case GT -> Fonts.GT;
            case DELTA_ICONS -> Fonts.DELTA_ICONS;
            case ICONS -> Fonts.ICONS;
            case GUI_ICONS -> Fonts.GUI_ICONS;
            case ICONNEW -> Fonts.ICONNEW;
            case VIREX_ICONS -> Fonts.VIREX_ICONS;
            case VIREX_WONDERFUL -> Fonts.VIREX_WONDERFUL;
            case DIVINE -> Fonts.DIVINE;
            default -> Fonts.REGULAR;
        }
                ;
    }

    private static Identifier id(String texture) {
        return universalmod.render.image.ImageRenderer.resolveIdentifier(texture);
    }

    private static int[] colors(int[] in) {
        int[] out = {
                ColorUtil.WHITE, ColorUtil.WHITE, ColorUtil.WHITE, ColorUtil.WHITE
        };
        if (in == null || in.length == 0) return out;
        if (in.length == 1) {
            for (int i = 0; i < 4; i++) out[i] = in[0];
            return out;
        }
        for (int i = 0; i < 4; i++) out[i] = in[Math.min(i, in.length - 1)];
        return out;
    }
}
