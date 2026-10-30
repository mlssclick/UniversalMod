package universalmod.render;

import universalmod.module.utility.NameProtect;
import universalmod.render.font.FontType;
import universalmod.render.font.msdf.MsdfIconRenderer;
import net.minecraft.client.gui.GuiGraphics;

final class RenderText {
    private RenderText() {
    }

    static void text(String nameFont, String text, float x, float y, float size, int color) {
            text(FontType.resolve(nameFont), NameProtect.replace(text), x, y, size, color);
        }

    static void text(FontType font, String text, float x, float y, float size, int color) {
            RenderImages.barrier();
            MsdfIconRenderer.getInstance().draw(font == null ? FontType.DEFAULT : font, NameProtect.replace(text), x, y, size, color);
        }

    static void animatedGlyphText(
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
            if (font == null || text == null || text.isEmpty()) {
                return;
            }
            text = NameProtect.replace(text);
            FontType resolved = font == null ? FontType.DEFAULT : font;
            RenderImages.barrier();
            int index = 0;
            while (index < text.length()) {
                int charStart = index;
                int codePoint = text.codePointAt(index);
                index += Character.charCount(codePoint);
                String glyph = new String(Character.toChars(codePoint));
                float glyphX = x + MsdfIconRenderer.getInstance().width(resolved, text.substring(0, index), size)
                        - MsdfIconRenderer.getInstance().width(resolved, glyph, size);
                if (charStart == firstGlyphIndex) {
                    drawAnimatedMsdfGlyph(resolved, glyph, previousFirst, glyphX, y, size, color,
                            firstProgress, verticalOffset);
                } else if (charStart == secondGlyphIndex) {
                    drawAnimatedMsdfGlyph(resolved, glyph, previousSecond, glyphX, y, size, color,
                            secondProgress, verticalOffset);
                } else {
                    MsdfIconRenderer.getInstance().draw(resolved, glyph, glyphX, y, size, color);
                }
            }
        }

    static void textFade(String nameFont, String text, float x, float y, float size, int color, float fadeLeftX, float fadeRightX, float fadeWidth, boolean fadeLeft, boolean fadeRight) {
            textFade(FontType.resolve(nameFont), text, x, y, size, color,
                    fadeLeftX, fadeRightX, fadeWidth, fadeLeft, fadeRight);
        }

    static void textFade(FontType font, String text, float x, float y, float size, int color, float fadeLeftX, float fadeRightX, float fadeWidth, boolean fadeLeft, boolean fadeRight) {
            RenderImages.barrier();
            MsdfIconRenderer.getInstance().draw(font == null ? FontType.DEFAULT : font, NameProtect.replace(text), x, y, size,
                    color, color, color, color, 0.0f, x, y, fadeLeft, fadeRight,
                    fadeLeftX, fadeRightX, fadeWidth,
                    fadeLeft ? 1.0f : 0.0f, fadeRight ? 1.0f : 0.0f);
        }

    static void textFade(String nameFont, String text, float x, float y, float size, int color, float fadeLeftX, float fadeRightX, float fadeWidth, float fadeLeftStrength, float fadeRightStrength) {
            textFade(FontType.resolve(nameFont), text, x, y, size, color,
                    fadeLeftX, fadeRightX, fadeWidth, fadeLeftStrength, fadeRightStrength);
        }

    static void textFade(FontType font, String text, float x, float y, float size, int color, float fadeLeftX, float fadeRightX, float fadeWidth, float fadeLeftStrength, float fadeRightStrength) {
            RenderImages.barrier();
            MsdfIconRenderer.getInstance().draw(font == null ? FontType.DEFAULT : font, NameProtect.replace(text), x, y, size,
                    color, color, color, color, 0.0f, x, y,
                    fadeLeftStrength > 0.001f, fadeRightStrength > 0.001f,
                    fadeLeftX, fadeRightX, fadeWidth, fadeLeftStrength, fadeRightStrength);
        }

    static void text(String nameFont, String text, float x, float y, float size, int color, float rotationDegrees, float rotationOriginX, float rotationOriginY) {
            text(FontType.resolve(nameFont), text, x, y, size, color,
                    rotationDegrees, rotationOriginX, rotationOriginY);
        }

    static void text(FontType font, String text, float x, float y, float size, int color, float rotationDegrees, float rotationOriginX, float rotationOriginY) {
            RenderImages.barrier();
            MsdfIconRenderer.getInstance().draw(font == null ? FontType.DEFAULT : font, NameProtect.replace(text), x, y, size,
                    color, color, color, color, rotationDegrees, rotationOriginX, rotationOriginY,
                    false, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        }

    static void text(
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
            text(FontType.resolve(nameFont), NameProtect.replace(text), x, y, size,
                    colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft);
        }

    static void text(
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
            RenderImages.barrier();
            MsdfIconRenderer.getInstance().draw(font == null ? FontType.DEFAULT : font, NameProtect.replace(text), x, y, size,
                    colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft,
                    0.0f, x, y, false, false,
                    0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        }

    static void text(
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
            text(FontType.resolve(nameFont), NameProtect.replace(text), x, y, size,
                    colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft,
                    rotationDegrees, rotationOriginX, rotationOriginY);
        }

    static void text(
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
            RenderImages.barrier();
            MsdfIconRenderer.getInstance().draw(font == null ? FontType.DEFAULT : font, NameProtect.replace(text), x, y, size,
                    colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft,
                    rotationDegrees, rotationOriginX, rotationOriginY, false, false,
                    0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        }

    static void text(GuiGraphics graphics, String nameFont, String text, float x, float y, float size, int color) {
            Render2D.beginFrame(graphics);
            text(nameFont, NameProtect.replace(text), x, y, size, color);
            Render2D.flush();
        }

    static void text(GuiGraphics graphics, FontType font, String text, float x, float y, float size, int color) {
            Render2D.beginFrame(graphics);
            text(font, NameProtect.replace(text), x, y, size, color);
            Render2D.flush();
        }

    static float textWidth(String nameFont, String text, float size) {
            return textWidth(FontType.resolve(nameFont), text, size);
        }

    static float textWidth(FontType font, String text, float size) {
            return MsdfIconRenderer.getInstance().width(font == null ? FontType.DEFAULT : font, NameProtect.replace(text), size);
        }

    static float textHeight(FontType font, String text, float size) {
            if (font == null || text == null || text.isEmpty() || size <= 0.0f) {
                return 0.0f;
            }
            return size;
        }

    static Render2D.TextVisualBounds textVisualBounds(FontType font, String text, float size) {
            if (font == null || text == null || text.isEmpty() || size <= 0.0f) {
                return Render2D.TextVisualBounds.EMPTY;
            }
            MsdfIconRenderer.VisualBounds bounds = MsdfIconRenderer.getInstance().visualBounds(font, NameProtect.replace(text), size);
            if (bounds.empty()) {
                return Render2D.TextVisualBounds.EMPTY;
            }
            return new Render2D.TextVisualBounds(bounds.minX(), bounds.minY(), bounds.maxX(), bounds.maxY());
        }

    static float textGlyphPositionX(FontType font, String text, int glyphIndex, float size) {
            if (font == null || text == null || text.isEmpty() || glyphIndex <= 0) {
                return 0.0f;
            }
            text = NameProtect.replace(text);
            String prefix = text.substring(0, Math.min(glyphIndex, text.length()));
            return MsdfIconRenderer.getInstance().width(font, prefix, size);
        }

    static void warmupText(String nameFont, String text, float size) {
            warmupText(FontType.resolve(nameFont), text, size);
        }

    static void warmupText(FontType font, String text, float size) {
            MsdfIconRenderer.getInstance().glyphLayout(font == null ? FontType.DEFAULT : font, NameProtect.replace(text), size);
        }

    private static void drawAnimatedMsdfGlyph(
                FontType font,
                String current,
                String previous,
                float x,
                float y,
                float size,
                int color,
                float progress,
                float verticalOffset
        ) {
            float clamped = Math.max(0.0f, Math.min(1.0f, progress));
            if (previous != null && !previous.isEmpty() && clamped < 0.999f) {
                MsdfIconRenderer.getInstance().draw(font, previous, x, y + verticalOffset * clamped, size,
                        scaleTextAlpha(color, 1.0f - clamped));
            }
            MsdfIconRenderer.getInstance().draw(font, current, x, y - verticalOffset + verticalOffset * clamped, size,
                    scaleTextAlpha(color, clamped));
        }

    private static int scaleTextAlpha(int color, float multiplier) {
            int alpha = (color >>> 24) & 0xFF;
            int scaled = Math.round(alpha * Math.max(0.0f, Math.min(1.0f, multiplier)));
            return (scaled << 24) | (color & 0x00FFFFFF);
        }
}
