package universalmod.render.hud;

import universalmod.render.Render2D;
import universalmod.render.blur.BuiltBlur;
import universalmod.render.color.ColorUtil;
import universalmod.theme.ThemeColors;

public final class HudAppearance {
    private HudAppearance() {
    }

    public static void background(float x, float y, float width, float height,
                                  float radius, float blurRadius, float smoothness, int color) {
        float alpha = ((color >>> 24) & 255) / 255F;
        if (alpha <= 0.0001F || width <= 0.0F || height <= 0.0F) return;
        if (universalmod.module.render.Hud.isBlurStyle()) {
            Render2D.blur(x, y, width, height, radius, Math.max(6F, blurRadius * 1.75F),
                    Math.max(0.70F, smoothness), ThemeColors.hudBlurColor(
                        ColorUtil.rgba(5, 7, 11, Math.round(212F * alpha))));
        }
        Render2D.hudChrome(x, y, width, height, radius, 0.985F * alpha, 0.82F, 0.91F);
    }

    public static void header(float x, float y, float width, float height,
                              float radius, float blurRadius, float smoothness, int color) {
        background(x, y, width, height, radius, blurRadius, smoothness, color);
    }

    public static void background(BuiltBlur blur) {
        if (blur == null) return;
        float alpha = ((blur.color() >>> 24) & 255) / 255F;
        if (alpha <= 0.0001F) return;
        if (universalmod.module.render.Hud.isBlurStyle()) {
            BuiltBlur tinted = new BuiltBlur(blur.x(), blur.y(), blur.width(), blur.height(),
                    blur.radiusTopLeft(), blur.radiusTopRight(), blur.radiusBottomRight(), blur.radiusBottomLeft(),
                    Math.max(0.70F, blur.smoothness()), Math.max(6F, blur.blurRadius() * 1.75F),
                    ThemeColors.hudBlurColor(ColorUtil.rgba(5, 7, 11, Math.round(212F * alpha))));
            Render2D.blur(tinted);
        }
        Render2D.hudChrome(blur.x(), blur.y(), blur.width(), blur.height(),
                blur.radiusTopLeft(), 0.985F * alpha, 0.82F, 0.91F);
    }

    public static void glow(String texture, float x, float y, float width, float height, float radius, int color) {
        Render2D.image(texture, x, y, width, height, radius, color);
    }
}
