package universalmod.hud.element;

import universalmod.render.Render2D;
import universalmod.render.color.ColorUtil;
import universalmod.render.font.FontType;
import universalmod.theme.ThemeColors;
import universalmod.module.render.Hud;

final class HudVisualStyle {
    static final int TEXT = ColorUtil.rgba(246, 246, 249, 255);
    static final int MUTED = ColorUtil.rgba(139, 139, 151, 255);
    static final int DIM = ColorUtil.rgba(80, 80, 92, 255);
    static final int RED = ColorUtil.rgba(239, 70, 83, 255);
    static final int GREEN = ColorUtil.rgba(91, 210, 139, 255);

    private HudVisualStyle() {
    }

    static void panel(float x, float y, float width, float height, float radius, float alpha) {
        float a = clamp01(alpha);
        if (Hud.isBlurStyle()) {
            Hud.renderHudBackground(x, y, width, height, radius, 4.5F, 1.0F,
                    ColorUtil.rgba(9, 11, 15, Math.round(255.0F * a)));
        } else {
            Render2D.hudSurface(x, y, width, height, radius, 0.985F * a, 0.82F, 0.91F);
        }
    }

    static void inset(float x, float y, float width, float height, float radius, float alpha) {
        float a = clamp01(alpha);
        if (Hud.isBlurStyle()) {
            Hud.renderHudBackground(x, y, width, height, radius, 4.5F, 1.0F,
                    ColorUtil.rgba(9, 11, 15, Math.round(224.0F * a)));
        } else {
            Render2D.hudSurface(x, y, width, height, radius, 0.95F * a, 0.86F, 0.90F);
        }
    }

    static void blackPanel(float x, float y, float width, float height, float radius, float alpha) {
        float a = clamp01(alpha);
        if (Hud.isBlurStyle()) {
            Hud.renderHudBackground(x, y, width, height, radius, 4.5F, 1.0F,
                    ColorUtil.rgba(9, 11, 15, Math.round(255.0F * a)));
        } else {
            Render2D.hudSurface(x, y, width, height, radius, 0.99F * a, 0.84F, 1.0F);
        }
    }

    static void title(String text, float x, float y, float alpha) {
        Render2D.text(FontType.BOLD, text, x, y, 5.6F, alpha(TEXT, alpha));
    }

    static void text(String text, float x, float y, float size, float alpha) {
        Render2D.text(FontType.BOLD, text, x, y, size, alpha(TEXT, alpha));
    }

    static void muted(String text, float x, float y, float size, float alpha) {
        Render2D.text(FontType.BOLD, text, x, y, size, alpha(MUTED, alpha));
    }

    static int accent(float alpha) {
        return ThemeColors.hudAccentColor(Math.round(255.0F * clamp01(alpha)));
    }

    static int alpha(int color, float alpha) {
        return ColorUtil.multAlpha(color, clamp01(alpha));
    }

    static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
