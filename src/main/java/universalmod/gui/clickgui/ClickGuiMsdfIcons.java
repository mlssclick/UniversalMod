package universalmod.gui.clickgui;

import universalmod.module.ModuleCategory;
import universalmod.render.Render2D;
import universalmod.render.font.FontType;







public final class ClickGuiMsdfIcons {
    private ClickGuiMsdfIcons() {
    }

    public static void drawCentered(
            FontType font,
            String glyph,
            float x,
            float y,
            float width,
            float height,
            float visibleFraction,
            int color
    ) {
        if (font == null || glyph == null || glyph.isEmpty() || width <= 0.0f || height <= 0.0f) {
            return;
        }

        float baseSize = Math.max(1.0f, Math.min(width, height));
        Render2D.TextVisualBounds baseBounds = Render2D.textVisualBounds(font, glyph, baseSize);
        if (baseBounds == null || baseBounds.empty()) {
            return;
        }

        float targetW = width * Math.max(0.05f, Math.min(1.0f, visibleFraction));
        float targetH = height * Math.max(0.05f, Math.min(1.0f, visibleFraction));
        float fit = Math.min(targetW / Math.max(0.001f, baseBounds.width()),
                targetH / Math.max(0.001f, baseBounds.height()));
        float glyphSize = Math.max(0.5f, baseSize * fit);

        Render2D.TextVisualBounds bounds = Render2D.textVisualBounds(font, glyph, glyphSize);
        if (bounds == null || bounds.empty()) {
            return;
        }

        float visualCenterX = (bounds.minX() + bounds.maxX()) * 0.5f;
        float visualCenterY = (bounds.minY() + bounds.maxY()) * 0.5f;
        float glyphX = x + width * 0.5f - visualCenterX;
        float glyphY = y + height * 0.5f - visualCenterY;
        Render2D.text(font, glyph, glyphX, glyphY, glyphSize, color);
    }

    public static void drawDeltaCentered(String glyph, float x, float y, float size, int color) {
        drawCentered(FontType.DELTA_ICONS, glyph, x, y, size, size, 0.76f, color);
    }

    public static void drawCategoryIcon(universalmod.module.ModuleCategory category, float x, float y, float size, int color) {
        String label = switch (category) {
            case RENDER -> "Render";
            case UTILS -> "Utils";
            case MISC -> "Misc";
            case CONFIGS -> "Configs";
        };
        ClickGuiChromeRenderer.renderCategoryIcon(label, x, y, size, color);
    }
}
