package universalmod.gui.ui.control;

import universalmod.render.Render2D;
import universalmod.render.color.ColorUtil;
import universalmod.theme.ThemeColors;
import universalmod.render.smooth.SmoothRoundedRenderer;
import universalmod.render.smooth.SmoothRoundedShape;
public final class SmoothFloatSliderRenderer {
    public static final float BASE_TRACK_HEIGHT = 3.0F;
    public static final float BASE_KNOB_SIZE = 7.0F;
    public static final float BASE_HIT_PAD_X = 4.0F;
    public static final float BASE_HIT_Y_OFFSET = 4.0F;
    public static final float BASE_HIT_HEIGHT = 12.0F;

    private SmoothFloatSliderRenderer() {
    }

    public static Layout render(float x, float trackY, float width, float scale,
                                float alpha, float progress, float press) {
        Layout layout = layout(x, trackY, width, scale);
        float p = clamp01(progress);
        float pressed = clamp01(press);
        float trackHeight = BASE_TRACK_HEIGHT * scale;
        float effectiveAlpha = clamp01(alpha);

        SmoothRoundedRenderer renderer = SmoothRoundedRenderer.getInstance();
        renderer.enqueue(new SmoothRoundedShape(x, trackY, width, trackHeight, trackHeight * 0.5F,
                controlBackground(effectiveAlpha)));

        float fillWidth = width * p;
        if (fillWidth > 0.0F) {
            renderer.enqueue(new SmoothRoundedShape(x - 0.1F * scale, trackY, fillWidth, trackHeight, trackHeight * 0.5F,
                    ThemeColors.clickGuiAccentColor(255, effectiveAlpha)));
        }

        float knob = BASE_KNOB_SIZE * scale;
        float knobWidth = knob * (1.0F + 0.30F * pressed);
        float knobHeight = knob * (1.0F - 0.25F * pressed);
        float knobX = x + (width - knobWidth) * p;
        float knobY = trackY + trackHeight * 0.5F - knobHeight * 0.5F;
        renderer.enqueue(new SmoothRoundedShape(knobX, knobY, knobWidth, knobHeight, knobHeight * 0.5F + 0.25F * scale,
                knobColor(p, effectiveAlpha)));
        return layout;
    }

    public static Layout layout(float x, float trackY, float width, float scale) {
        float safeScale = Math.max(0.0F, scale);
        float trackHeight = BASE_TRACK_HEIGHT * safeScale;
        float knob = BASE_KNOB_SIZE * safeScale;
        float padX = BASE_HIT_PAD_X * safeScale;
        float hitYOffset = BASE_HIT_Y_OFFSET * safeScale;
        float hitHeight = BASE_HIT_HEIGHT * safeScale;
        return new Layout(
                new Bounds(x, trackY, Math.max(0.0F, width), trackHeight),
                new Bounds(x - padX, trackY - hitYOffset,
                        Math.max(0.0F, width) + padX * 2.0F, hitHeight),
                knob
        );
    }

    
    public static float progressFromMouse(double mouseX, float trackX, float trackWidth, float scale) {
        float knob = BASE_KNOB_SIZE * Math.max(0.0F, scale);
        float usable = Math.max(trackWidth - knob, 1.0E-4F);
        return clamp01((float) ((mouseX - trackX - knob * 0.5F) / usable));
    }

    private static int controlBackground(float alpha) {
        float a = clamp01(alpha);
        int effective = Math.round(255.0F * (0.16F + a * a * 0.24F));
        return ColorUtil.rgba(51, 51, 51, effective);
    }

    private static int knobColor(float progress, float alpha) {
        int accent = ThemeColors.clickGuiAccentColor(255, 1.0F);
        float whiteMix = 0.38F + (0.96F - 0.38F) * clamp01(progress);
        int r = Math.round(ColorUtil.getRed(accent) + (255.0F - ColorUtil.getRed(accent)) * whiteMix);
        int g = Math.round(ColorUtil.getGreen(accent) + (255.0F - ColorUtil.getGreen(accent)) * whiteMix);
        int b = Math.round(ColorUtil.getBlue(accent) + (255.0F - ColorUtil.getBlue(accent)) * whiteMix);
        return ColorUtil.rgba(r, g, b, Math.round(255.0F * clamp01(alpha)));
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    public record Layout(Bounds track, Bounds hit, float knobSize) {
    }
}
