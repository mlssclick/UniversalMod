package universalmod.gui.ui.control;

import universalmod.render.color.ColorUtil;
import universalmod.render.smooth.SmoothRoundedRenderer;
import universalmod.render.smooth.SmoothRoundedShape;
import universalmod.theme.ThemeColors;

public final class SmoothSwitchRenderer {
    public static final float WIDTH = 14.0F;
    public static final float HEIGHT = 9.0F;

    private static final int DARK_THEME_BACKGROUND = 0xFF000000;
    private static final int DARK_THEME_FOREGROUND = 0xFFFFFFFF;

    private SmoothSwitchRenderer() {
    }

    public static void render(float x, float y, float scale, float alpha, float progress) {
        float s = Math.max(0.0001F, scale);
        renderSized(x, y, WIDTH * s, HEIGHT * s, alpha, progress);
    }

    public static void renderSized(float x, float y, float width, float height, float alpha, float progress) {
        float p = clamp01(progress);
        float a = clamp01(alpha);
        if (a <= 0.0005F || width <= 0.0F || height <= 0.0F) {
            return;
        }

        SmoothRoundedRenderer renderer = SmoothRoundedRenderer.getInstance();

        int baseRgb = ColorUtil.lerpColor(DARK_THEME_BACKGROUND, DARK_THEME_FOREGROUND, 0.20F);
        int baseColor = ColorUtil.multAlpha(baseRgb, 0.18F + a * a * 0.28F);
        renderer.enqueue(new SmoothRoundedShape(x, y, width, height, height * 0.5F, baseColor));
        int frameColor = ColorUtil.multAlpha(ColorUtil.rgba(255, 255, 255, 255), (0.06F + (1.0F - p) * 0.08F) * a);
        renderer.enqueue(new SmoothRoundedShape(x + 0.25F * (height / HEIGHT), y + 0.25F * (height / HEIGHT),
                width - 0.5F * (height / HEIGHT), height - 0.5F * (height / HEIGHT), height * 0.5F - 0.25F * (height / HEIGHT), frameColor));

        
        int accent = ColorUtil.withAlpha(ThemeColors.clickGuiAccentColor(255, 1.0F), 255);
        int trackColor = ColorUtil.multAlpha(ColorUtil.lerpColor(0x00000000, accent, p), a);
        renderer.enqueue(new SmoothRoundedShape(x, y, width, height, height * 0.5F, trackColor));

        int knobOff = DARK_THEME_FOREGROUND;
        int knobOn = 0xFF000000;
        int knobColor = ColorUtil.multAlpha(ColorUtil.lerpColor(knobOff, knobOn, p), a);

        float pulse = Math.max(0.0F, Math.min(1.0F, 4.0F * p * (1.0F - p)));
        float unit = height / HEIGHT;
        float knobBase = height - 2.0F * unit;
        float knobW = knobBase * (1.0F + 0.30F * pulse);
        float knobH = knobBase * (1.0F - 0.25F * pulse);
        float knobX = x + unit + p * (width - 2.0F * unit - knobW);
        float knobY = y + height * 0.5F - knobH * 0.5F;
        float knobRadius = knobH * 0.5F + 0.25F * unit;

        renderer.enqueue(new SmoothRoundedShape(knobX, knobY, knobW, knobH, knobRadius, knobColor));
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
