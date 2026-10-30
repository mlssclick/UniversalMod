package universalmod.gui.ui.control;

import universalmod.render.color.ColorUtil;
import universalmod.render.smooth.SmoothCircleRenderer;
import java.awt.Color;
public final class ColorSwatchRenderer {
    private ColorSwatchRenderer() {
    }

    public static void render(float x, float y, float size, Color color, float opacity) {
        if (color == null || size <= 0.0F || opacity <= 0.0F) {
            return;
        }
        int argb = ColorUtil.rgba(
                color.getRed(), color.getGreen(), color.getBlue(),
                Math.round(color.getAlpha() * clamp01(opacity))
        );
        SmoothCircleRenderer.fill(x + size * 0.5F, y + size * 0.5F, size, argb);
    }

    public static void renderArgb(float x, float y, float size, int argb) {
        if (size <= 0.0F) {
            return;
        }
        SmoothCircleRenderer.fill(x + size * 0.5F, y + size * 0.5F, size, argb);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
