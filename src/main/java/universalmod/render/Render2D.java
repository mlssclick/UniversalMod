package universalmod.render;

import universalmod.render.blur.BlurPass;
import universalmod.render.font.FontType;
import universalmod.render.glass.GlassQuad;
import universalmod.render.shape.OutlineShape;
import universalmod.render.glass.GlassOutline;
import universalmod.render.shape.RectangleShape;
import net.minecraft.core.Holder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import universalmod.render.ScissorUtil;

import java.util.ArrayDeque;
import java.util.Deque;

public final class Render2D {
    @FunctionalInterface
    public interface PointProjector {
        ProjectedPoint project(float x, float y);
    }

    public record ProjectedPoint(float x, float y) {
    }

    public record ProjectedRect(float x, float y, float width, float height) {
    }

    private static final PointProjector IDENTITY_PROJECTOR = ProjectedPoint::new;
    private static final Deque<PointProjector> POINT_PROJECTORS = new ArrayDeque<>();
    private static int projectionOverrideDepth;
    private static GuiGraphics currentGraphics;

    private Render2D() {
    }

    public static int getFixedScaledWidth() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(minecraft.getWindow().getWidth() / Render2DCoordinateSpace.designGuiScale()));
    }

    public static int getFixedScaledHeight() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(minecraft.getWindow().getHeight() / Render2DCoordinateSpace.designGuiScale()));
    }

    public static float guiToFixed(float value) {
        float scale = Render2DCoordinateSpace.guiIndependentScale();
        if (Math.abs(scale) <= 0.0001f) {
            return value;
        }
        return value / scale;
    }

    public static double guiToFixed(double value) {
        float scale = Render2DCoordinateSpace.guiIndependentScale();
        if (Math.abs(scale) <= 0.0001f) {
            return value;
        }
        return value / scale;
    }

    public static void pushProjection(PointProjector projector) {
        projectionOverrideDepth++;
        POINT_PROJECTORS.addLast(projector == null ? IDENTITY_PROJECTOR : projector);
    }

    public static void popProjection() {
        if (!POINT_PROJECTORS.isEmpty()) {
            POINT_PROJECTORS.removeLast();
        }
        if (projectionOverrideDepth > 0) {
            projectionOverrideDepth--;
        }
    }

    public static PointProjector getCurrentProjector() {
        return POINT_PROJECTORS.peekLast();
    }

    public static boolean hasProjectionOverride() {
        return projectionOverrideDepth > 0;
    }

    public static ProjectedRect projectRect(float x, float y, float width, float height) {
        PointProjector projector = getCurrentProjector();
        if (projector == null) {
            return new ProjectedRect(x, y, width, height);
        }

        ProjectedPoint p0 = projector.project(x, y);
        ProjectedPoint p1 = projector.project(x + width, y);
        ProjectedPoint p2 = projector.project(x + width, y + height);
        ProjectedPoint p3 = projector.project(x, y + height);

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        boolean hasPoint = false;

        if (p0 != null) {
            hasPoint = true;
            minX = p0.x(); minY = p0.y(); maxX = p0.x(); maxY = p0.y();
        }
        if (p1 != null) {
            hasPoint = true;
            minX = Math.min(minX, p1.x()); minY = Math.min(minY, p1.y());
            maxX = Math.max(maxX, p1.x()); maxY = Math.max(maxY, p1.y());
        }
        if (p2 != null) {
            hasPoint = true;
            minX = Math.min(minX, p2.x()); minY = Math.min(minY, p2.y());
            maxX = Math.max(maxX, p2.x()); maxY = Math.max(maxY, p2.y());
        }
        if (p3 != null) {
            hasPoint = true;
            minX = Math.min(minX, p3.x()); minY = Math.min(minY, p3.y());
            maxX = Math.max(maxX, p3.x()); maxY = Math.max(maxY, p3.y());
        }

        if (!hasPoint) {
            return null;
        }
        return new ProjectedRect(minX, minY, Math.max(0.0f, maxX - minX), Math.max(0.0f, maxY - minY));
    }

    public static void beginFrame(GuiGraphics graphics) {
        currentGraphics = graphics;
        Render2DBackends.beginFrame(graphics);
    }

    public static void flush() {
        Render2DBackends.flush();
        currentGraphics = null;
    }

    public static GuiGraphics currentGraphics() {
        return currentGraphics;
    }

    public static void hudSurface(
                float x, float y, float width, float height, float radius,
                float alpha, float smoothness, float darkness
        ) {
        RenderPrimitives.hudSurface(x, y, width, height, radius, alpha, smoothness, darkness);
    }
    public static void blur(float x, float y, float width, float height, float radius) {
        RenderPrimitives.blur(x, y, width, height, radius);
    }
    public static void blur(float x, float y, float width, float height, float radius, float blurRadius) {
        RenderPrimitives.blur(x, y, width, height, radius, blurRadius);
    }
    public static void blur(float x, float y, float width, float height, float radius, float blurRadius, float smoothness, int color) {
        RenderPrimitives.blur(x, y, width, height, radius, blurRadius, smoothness, color);
    }
    public static void blur(
                float x,
                float y,
                float width,
                float height,
                float radius,
                float blurRadius,
                float smoothness,
                int colorTopLeft,
                int colorTopRight,
                int colorBottomRight,
                int colorBottomLeft
        ) {
        RenderPrimitives.blur(x, y, width, height, radius, blurRadius, smoothness, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft);
    }
    public static void blur(GuiGraphics graphics, float x, float y, float width, float height, float radius, float blurRadius) {
        RenderPrimitives.blur(graphics, x, y, width, height, radius, blurRadius);
    }
    public static void blur(BlurPass blur) {
        RenderPrimitives.blur(blur);
    }
    public static void glass(
                float x,
                float y,
                float width,
                float height,
                float[] radius,
                int color,
                float globalAlpha,
                float fresnelPower,
                int fresnelColor,
                float baseAlpha,
                boolean fresnelInvert,
                float fresnelMix,
                float distortStrength,
                float squirt,
                float z
        ) {
        RenderPrimitives.glass(x, y, width, height, radius, color, globalAlpha, fresnelPower, fresnelColor, baseAlpha, fresnelInvert, fresnelMix, distortStrength, squirt, z);
    }
    public static void glass(
                float x,
                float y,
                float width,
                float height,
                float radiusTopLeft,
                float radiusTopRight,
                float radiusBottomRight,
                float radiusBottomLeft,
                int color,
                float globalAlpha,
                float fresnelPower,
                int fresnelColor,
                float baseAlpha,
                boolean fresnelInvert,
                float fresnelMix,
                float distortStrength,
                float squirt,
                float z
        ) {
        RenderPrimitives.glass(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, color, globalAlpha, fresnelPower, fresnelColor, baseAlpha, fresnelInvert, fresnelMix, distortStrength, squirt, z);
    }
    public static void glass(
                float x,
                float y,
                float width,
                float height,
                float radius,
                int color,
                float globalAlpha,
                float fresnelPower,
                int fresnelColor,
                float baseAlpha,
                boolean fresnelInvert,
                float fresnelMix,
                float distortStrength,
                float squirt,
                float z
        ) {
        RenderPrimitives.glass(x, y, width, height, radius, color, globalAlpha, fresnelPower, fresnelColor, baseAlpha, fresnelInvert, fresnelMix, distortStrength, squirt, z);
    }
    public static void glass(GlassQuad glass) {
        RenderPrimitives.glass(glass);
    }
    public static void glassOutline(
                float x,
                float y,
                float width,
                float height,
                float[] radius,
                float thickness,
                int color,
                float globalAlpha,
                float fresnelPower,
                int fresnelColor,
                float baseAlpha,
                boolean fresnelInvert,
                float fresnelMix,
                float distortStrength,
                float squirt,
                float z
        ) {
        RenderPrimitives.glassOutline(x, y, width, height, radius, thickness, color, globalAlpha, fresnelPower, fresnelColor, baseAlpha, fresnelInvert, fresnelMix, distortStrength, squirt, z);
    }
    public static void glassOutline(
                float x,
                float y,
                float width,
                float height,
                float radiusTopLeft,
                float radiusTopRight,
                float radiusBottomRight,
                float radiusBottomLeft,
                float thickness,
                int color,
                float globalAlpha,
                float fresnelPower,
                int fresnelColor,
                float baseAlpha,
                boolean fresnelInvert,
                float fresnelMix,
                float distortStrength,
                float squirt,
                float z
        ) {
        RenderPrimitives.glassOutline(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, thickness, color, globalAlpha, fresnelPower, fresnelColor, baseAlpha, fresnelInvert, fresnelMix, distortStrength, squirt, z);
    }
    public static void glassOutline(
                float x,
                float y,
                float width,
                float height,
                float radius,
                float thickness,
                int color,
                float globalAlpha,
                float fresnelPower,
                int fresnelColor,
                float baseAlpha,
                boolean fresnelInvert,
                float fresnelMix,
                float distortStrength,
                float squirt,
                float z
        ) {
        RenderPrimitives.glassOutline(x, y, width, height, radius, thickness, color, globalAlpha, fresnelPower, fresnelColor, baseAlpha, fresnelInvert, fresnelMix, distortStrength, squirt, z);
    }
    public static void glassOutline(GlassOutline outline) {
        RenderPrimitives.glassOutline(outline);
    }
    public static void rect(float x, float y, float width, float height, int color) {
        RenderPrimitives.rect(x, y, width, height, color);
    }
    public static void rect(float x, float y, float width, float height, float radius, int color) {
        RenderPrimitives.rect(x, y, width, height, radius, color);
    }
    public static void rect(
                float x,
                float y,
                float width,
                float height,
                float radiusTopLeft,
                float radiusTopRight,
                float radiusBottomRight,
                float radiusBottomLeft,
                int color
        ) {
        RenderPrimitives.rect(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, color);
    }
    public static void rect(
                float x,
                float y,
                float width,
                float height,
                float radius,
                int colorTopLeft,
                int colorTopRight,
                int colorBottomRight,
                int colorBottomLeft
        ) {
        RenderPrimitives.rect(x, y, width, height, radius, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft);
    }
    public static void rect(
                float x,
                float y,
                float width,
                float height,
                float radiusTopLeft,
                float radiusTopRight,
                float radiusBottomRight,
                float radiusBottomLeft,
                int colorTopLeft,
                int colorTopRight,
                int colorBottomRight,
                int colorBottomLeft
        ) {
        RenderPrimitives.rect(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft);
    }
    public static void rect(GuiGraphics graphics, float x, float y, float width, float height, float radius, int color) {
        RenderPrimitives.rect(graphics, x, y, width, height, radius, color);
    }
    public static void rect(RectangleShape rectangle) {
        RenderPrimitives.rect(rectangle);
    }
    private static final String SMOOTH_CIRCLE_TEXTURE = "ui/smooth_circle.png";

    public static void circle(float centerX, float centerY, float diameter, int color) {
        float size = Math.max(0.0F, diameter);
        if (size <= 0.0F || ((color >>> 24) & 0xFF) == 0) {
            return;
        }
        image(SMOOTH_CIRCLE_TEXTURE, centerX - size * 0.5F, centerY - size * 0.5F, size, size, 0.0F, color);
    }

    public static void capsule(float x, float y, float width, float height, int color) {
        float w = Math.max(0.0F, width);
        float h = Math.max(0.0F, height);
        if (w <= 0.0F || h <= 0.0F || ((color >>> 24) & 0xFF) == 0) {
            return;
        }
        if (Math.abs(w - h) <= 0.0001F) {
            circle(x + w * 0.5F, y + h * 0.5F, w, color);
            return;
        }

        final float centerU0 = 0.4921875F; 
        final float centerU1 = 0.5078125F; 
        if (w > h) {
            float r = h * 0.5F;
            imageUv(SMOOTH_CIRCLE_TEXTURE, x, y, r, h, 0.0F, 0.0F, 0.0F, 0.5F, 1.0F, color);
            float middle = Math.max(0.0F, w - h);
            if (middle > 0.0F) {
                imageUv(SMOOTH_CIRCLE_TEXTURE, x + r, y, middle, h, 0.0F, centerU0, 0.0F, centerU1, 1.0F, color);
            }
            imageUv(SMOOTH_CIRCLE_TEXTURE, x + w - r, y, r, h, 0.0F, 0.5F, 0.0F, 1.0F, 1.0F, color);
        } else {
            float r = w * 0.5F;
            imageUv(SMOOTH_CIRCLE_TEXTURE, x, y, w, r, 0.0F, 0.0F, 0.0F, 1.0F, 0.5F, color);
            float middle = Math.max(0.0F, h - w);
            if (middle > 0.0F) {
                imageUv(SMOOTH_CIRCLE_TEXTURE, x, y + r, w, middle, 0.0F, 0.0F, centerU0, 1.0F, centerU1, color);
            }
            imageUv(SMOOTH_CIRCLE_TEXTURE, x, y + h - r, w, r, 0.0F, 0.0F, 0.5F, 1.0F, 1.0F, color);
        }
    }

    public static void circleOutline(float centerX, float centerY, float diameter, float thickness, int color) {
        float size = Math.max(0.0F, diameter);
        outline(centerX - size * 0.5F, centerY - size * 0.5F, size, size, size * 0.5F, thickness, color);
    }
    public static void outline(float x, float y, float width, float height, float radius, float thickness, int color) {
        RenderPrimitives.outline(x, y, width, height, radius, thickness, color);
    }
    public static void outline(
                float x,
                float y,
                float width,
                float height,
                float radiusTopLeft,
                float radiusTopRight,
                float radiusBottomRight,
                float radiusBottomLeft,
                float thickness,
                int color
        ) {
        RenderPrimitives.outline(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, thickness, color);
    }
    public static void outline(
                float x,
                float y,
                float width,
                float height,
                float radius,
                float thickness,
                int colorTopLeft,
                int colorTopRight,
                int colorBottomRight,
                int colorBottomLeft
        ) {
        RenderPrimitives.outline(x, y, width, height, radius, thickness, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft);
    }
    public static void outline(
                float x,
                float y,
                float width,
                float height,
                float radiusTopLeft,
                float radiusTopRight,
                float radiusBottomRight,
                float radiusBottomLeft,
                float thickness,
                int colorTopLeft,
                int colorTopRight,
                int colorBottomRight,
                int colorBottomLeft
        ) {
        RenderPrimitives.outline(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, thickness, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft);
    }
    public static void outline(GuiGraphics graphics, float x, float y, float width, float height, float radius, float thickness, int color) {
        RenderPrimitives.outline(graphics, x, y, width, height, radius, thickness, color);
    }
    public static void outline(OutlineShape outline) {
        RenderPrimitives.outline(outline);
    }
    public static void text(String nameFont, String text, float x, float y, float size, int color) {
        RenderText.text(nameFont, text, x, y, size, color);
    }
    public static void text(FontType font, String text, float x, float y, float size, int color) {
        RenderText.text(font, text, x, y, size, color);
    }
    public static void animatedGlyphText(
                FontType font,
                String text,
                float x,
                float y,
                float size,
                int color,
                int firstGlyphIndex,
                String previousFirst,
                float firstProgress,
                int secondGlyphIndex,
                String previousSecond,
                float secondProgress,
                float verticalOffset
        ) {
        RenderText.animatedGlyphText(font, text, x, y, size, color, firstGlyphIndex, previousFirst, firstProgress, secondGlyphIndex, previousSecond, secondProgress, verticalOffset);
    }
    public static void textFade(String nameFont, String text, float x, float y, float size, int color, float fadeLeftX, float fadeRightX, float fadeWidth, boolean fadeLeft, boolean fadeRight) {
        RenderText.textFade(nameFont, text, x, y, size, color, fadeLeftX, fadeRightX, fadeWidth, fadeLeft, fadeRight);
    }
    public static void textFade(FontType font, String text, float x, float y, float size, int color, float fadeLeftX, float fadeRightX, float fadeWidth, boolean fadeLeft, boolean fadeRight) {
        RenderText.textFade(font, text, x, y, size, color, fadeLeftX, fadeRightX, fadeWidth, fadeLeft, fadeRight);
    }
    public static void textFade(String nameFont, String text, float x, float y, float size, int color, float fadeLeftX, float fadeRightX, float fadeWidth, float fadeLeftStrength, float fadeRightStrength) {
        RenderText.textFade(nameFont, text, x, y, size, color, fadeLeftX, fadeRightX, fadeWidth, fadeLeftStrength, fadeRightStrength);
    }
    public static void textFade(FontType font, String text, float x, float y, float size, int color, float fadeLeftX, float fadeRightX, float fadeWidth, float fadeLeftStrength, float fadeRightStrength) {
        RenderText.textFade(font, text, x, y, size, color, fadeLeftX, fadeRightX, fadeWidth, fadeLeftStrength, fadeRightStrength);
    }
    public static void text(String nameFont, String text, float x, float y, float size, int color, float rotationDegrees, float rotationOriginX, float rotationOriginY) {
        RenderText.text(nameFont, text, x, y, size, color, rotationDegrees, rotationOriginX, rotationOriginY);
    }
    public static void text(FontType font, String text, float x, float y, float size, int color, float rotationDegrees, float rotationOriginX, float rotationOriginY) {
        RenderText.text(font, text, x, y, size, color, rotationDegrees, rotationOriginX, rotationOriginY);
    }
    public static void text(
                String nameFont,
                String text,
                float x,
                float y,
                float size,
                int colorTopLeft,
                int colorTopRight,
                int colorBottomRight,
                int colorBottomLeft
        ) {
        RenderText.text(nameFont, text, x, y, size, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft);
    }
    public static void text(
                FontType font,
                String text,
                float x,
                float y,
                float size,
                int colorTopLeft,
                int colorTopRight,
                int colorBottomRight,
                int colorBottomLeft
        ) {
        RenderText.text(font, text, x, y, size, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft);
    }
    public static void text(
                String nameFont,
                String text,
                float x,
                float y,
                float size,
                int colorTopLeft,
                int colorTopRight,
                int colorBottomRight,
                int colorBottomLeft,
                float rotationDegrees,
                float rotationOriginX,
                float rotationOriginY
        ) {
        RenderText.text(nameFont, text, x, y, size, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft, rotationDegrees, rotationOriginX, rotationOriginY);
    }
    public static void text(
                FontType font,
                String text,
                float x,
                float y,
                float size,
                int colorTopLeft,
                int colorTopRight,
                int colorBottomRight,
                int colorBottomLeft,
                float rotationDegrees,
                float rotationOriginX,
                float rotationOriginY
        ) {
        RenderText.text(font, text, x, y, size, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft, rotationDegrees, rotationOriginX, rotationOriginY);
    }
    public static void text(GuiGraphics graphics, String nameFont, String text, float x, float y, float size, int color) {
        RenderText.text(graphics, nameFont, text, x, y, size, color);
    }
    public static void text(GuiGraphics graphics, FontType font, String text, float x, float y, float size, int color) {
        RenderText.text(graphics, font, text, x, y, size, color);
    }
    public static void image(String texture, float x, float y, float size, float radius) {
        RenderImages.image(texture, x, y, size, radius);
    }
    public static void image(String texture, float x, float y, float size, float radius, int color) {
        RenderImages.image(texture, x, y, size, radius, color);
    }
    public static void image(String texture, float x, float y, float size, float radius, int... colors) {
        RenderImages.image(texture, x, y, size, radius, colors);
    }
    public static void image(String texture, float x, float y, float width, float height, float radius, int color) {
        RenderImages.image(texture, x, y, width, height, radius, color);
    }
    public static void image(String texture, float x, float y, float width, float height, float radius, int... colors) {
        RenderImages.image(texture, x, y, width, height, radius, colors);
    }
    public static void imageUv(String texture, float x, float y, float width, float height, float radius, float u0, float v0, float u1, float v1, int color) {
        RenderImages.imageUv(texture, x, y, width, height, radius, u0, v0, u1, v1, color);
    }
    public static void imageUv(String texture, float x, float y, float width, float height, float radius, float u0, float v0, float u1, float v1, int... colors) {
        RenderImages.imageUv(texture, x, y, width, height, radius, u0, v0, u1, v1, colors);
    }
    public static void imageUvNearest(String texture, float x, float y, float width, float height, float radius, float smoothness, float u0, float v0, float u1, float v1, int color) {
        RenderImages.imageUvNearest(texture, x, y, width, height, radius, smoothness, u0, v0, u1, v1, color);
    }
    public static void imageUvNearest(String texture, float x, float y, float width, float height, float radius, float smoothness, float u0, float v0, float u1, float v1, int... colors) {
        RenderImages.imageUvNearest(texture, x, y, width, height, radius, smoothness, u0, v0, u1, v1, colors);
    }
    public static void image(String texture, float x, float y, float size, float radius, float rotationDegrees, float originX, float originY, int color) {
        RenderImages.image(texture, x, y, size, radius, rotationDegrees, originX, originY, color);
    }
    public static void image(String texture, float x, float y, float size, float radius, float rotationDegrees, float originX, float originY, int... colors) {
        RenderImages.image(texture, x, y, size, radius, rotationDegrees, originX, originY, colors);
    }
    public static void effectIcon(Holder<MobEffect> effect, float x, float y, float size) {
        RenderImages.effectIcon(effect, x, y, size);
    }
    public static void effectIcon(Holder<MobEffect> effect, float x, float y, float size, int color) {
        RenderImages.effectIcon(effect, x, y, size, color);
    }
    public static void effectIcon(MobEffectInstance effect, float x, float y, float size) {
        RenderImages.effectIcon(effect, x, y, size);
    }
    public static void effectIcon(MobEffectInstance effect, float x, float y, float size, int color) {
        RenderImages.effectIcon(effect, x, y, size, color);
    }
    public static float textWidth(String nameFont, String text, float size) {
        return RenderText.textWidth(nameFont, text, size);
    }
    public static float textWidth(FontType font, String text, float size) {
        return RenderText.textWidth(font, text, size);
    }
    public static float textHeight(FontType font, String text, float size) {
        return RenderText.textHeight(font, text, size);
    }
    public static TextVisualBounds textVisualBounds(FontType font, String text, float size) {
        return RenderText.textVisualBounds(font, text, size);
    }
    public static float textGlyphPositionX(FontType font, String text, int glyphIndex, float size) {
        return RenderText.textGlyphPositionX(font, text, glyphIndex, size);
    }
    public static void warmupText(String nameFont, String text, float size) {
        RenderText.warmupText(nameFont, text, size);
    }
    public static void warmupText(FontType font, String text, float size) {
        RenderText.warmupText(font, text, size);
    }

    public static void pushScissor(GuiGraphics graphics, float x, float y, float width, float height) {
        if (graphics == null) {
            return;
        }
        ProjectedRect area = hasProjectionOverride() ? projectRect(x, y, width, height) : new ProjectedRect(x, y, width, height);
        if (area == null) {
            area = new ProjectedRect(0.0f, 0.0f, 0.0f, 0.0f);
        }
        RenderScissorStack.push(graphics, area.x(), area.y(), area.width(), area.height());
    }

    public static void popScissor(GuiGraphics graphics) {
        if (graphics != null) {
            RenderScissorStack.pop(graphics);
        }
    }

    public static void close() {
        currentGraphics = null;
        projectionOverrideDepth = 0;
        POINT_PROJECTORS.clear();
        ScissorUtil.clear();
        Render2DBackends.closeAll();
    }

    public record TextVisualBounds(float minX, float minY, float maxX, float maxY) {
        public static final TextVisualBounds EMPTY = new TextVisualBounds(0.0f, 0.0f, 0.0f, 0.0f);

        public float width() {
            return Math.max(0.0f, maxX - minX);
        }

        public float height() {
            return Math.max(0.0f, maxY - minY);
        }

        public float centerY() {
            return (minY + maxY) * 0.5f;
        }

        public boolean empty() {
            return width() <= 0.0f || height() <= 0.0f;
        }
    }
}
