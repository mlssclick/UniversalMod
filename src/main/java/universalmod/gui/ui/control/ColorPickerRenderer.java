package universalmod.gui.ui.control;

import universalmod.render.Render2D;
import universalmod.render.color.ColorUtil;

import java.awt.Color;


public final class ColorPickerRenderer {

    private ColorPickerRenderer() {
    }

    public static void render(ColorPickerGeometry geometry, ColorPickerModel model, float opacity, float scale) {
        palette(geometry.palette(), model.hue(), model.saturation(), model.brightness(), opacity, scale);
        hue(geometry.hue(), model.hue(), opacity, scale);
        alpha(geometry.alpha(), model.hue(), model.saturation(), model.brightness(), model.alpha(), opacity, scale);
    }

    public static void palette(Bounds bounds, float hue, float saturation, float brightness,
                               float opacity, float scale) {
        int hueColor = Color.HSBtoRGB(hue, 1.0F, 1.0F);
        int topLeft = color(255, 255, 255, 255, opacity);
        int topRight = color(red(hueColor), green(hueColor), blue(hueColor), 255, opacity);
        int bottom = color(0, 0, 0, 255, opacity);
        Render2D.rect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 1.8F * scale,
                topLeft, topRight, bottom, bottom);

        float selectorX = bounds.x() + saturation * bounds.width();
        float selectorY = bounds.y() + (1.0F - brightness) * bounds.height();
        float selector = 8.0F * scale;
        selectionRing(selectorX, selectorY, selector, opacity, scale);
    }

    public static void hue(Bounds bounds, float hue, float opacity, float scale) {
        hue(bounds, hue, opacity, scale, ColorPickerInteraction.HueAxis.HORIZONTAL);
    }

    public static void hue(Bounds bounds, float hue, float opacity, float scale, ColorPickerInteraction.HueAxis axis) {
        if (axis == ColorPickerInteraction.HueAxis.VERTICAL) {
            hueVertical(bounds, hue, opacity, scale);
            return;
        }
        hueHorizontal(bounds, hue, opacity, scale);
    }

    public static void hue(Bounds bounds, float hue, float opacity, float scale, HueAxis axis) {
        hue(bounds, hue, opacity, scale, axis == HueAxis.VERTICAL
                ? ColorPickerInteraction.HueAxis.VERTICAL
                : ColorPickerInteraction.HueAxis.HORIZONTAL);
    }

    private static void hueHorizontal(Bounds bounds, float hue, float opacity, float scale) {
        HueStripRenderer.render(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                1.8F * scale, opacity, false);

        float selectorX = bounds.x() + ColorPickerModel.clamp01(hue) * bounds.width();
        float knob = 5.6F * scale;
        selectionRing(selectorX, bounds.centerY(), knob + 1.3F * scale, opacity, scale);
    }

    private static void hueVertical(Bounds bounds, float hue, float opacity, float scale) {
        HueStripRenderer.render(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                1.8F * scale, opacity, true);

        float selectorY = bounds.y() + ColorPickerModel.clamp01(hue) * bounds.height();
        float knob = 5.6F * scale;
        selectionRing(bounds.centerX(), selectorY, knob + 1.3F * scale, opacity, scale);
    }

    public enum HueAxis {
        HORIZONTAL,
        VERTICAL
    }

    public static void alpha(Bounds bounds, float hue, float saturation, float brightness,
                             float value, float opacity, float scale) {
        float radius = 1.8F * scale;
        int rgb = Color.HSBtoRGB(hue, saturation, brightness);
        Render2D.rect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), radius,
                color(255, 255, 255, 255, opacity),
                color(red(rgb), green(rgb), blue(rgb), 255, opacity),
                color(red(rgb), green(rgb), blue(rgb), 255, opacity),
                color(255, 255, 255, 255, opacity));
        Render2D.rect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), radius,
                color(255, 255, 255, 10, opacity),
                color(255, 255, 255, 10, opacity),
                color(255, 255, 255, 0, opacity),
                color(255, 255, 255, 0, opacity));
        Render2D.outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), radius, 0.32F * scale,
                color(255, 255, 255, 66, opacity));

        float knob = 5.4F * scale;
        float half = knob * 0.5F;
        float selectorX = clamp(bounds.x() + ColorPickerModel.clamp01(value) * bounds.width(),
                bounds.x() + half, bounds.right() - half);
        selectionRing(selectorX, bounds.centerY(), knob + 1.5F * scale, opacity, scale);
    }

    private static void selectionRing(float centerX, float centerY, float diameter, float opacity, float scale) {
        if (diameter <= 0.0F || opacity <= 0.0F) {
            return;
        }
        Render2D.circleOutline(centerX, centerY, diameter, 1.55F * scale,
                color(10, 10, 16, 212, opacity));
        Render2D.circleOutline(centerX, centerY, diameter - 0.65F * scale, 0.85F * scale,
                color(255, 255, 255, 255, opacity));
    }

    private static int color(int r, int g, int b, int a, float opacity) {
        return ColorUtil.rgba(r, g, b, Math.max(0, Math.min(255, Math.round(a * opacity))));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int red(int color) { return (color >>> 16) & 0xFF; }
    private static int green(int color) { return (color >>> 8) & 0xFF; }
    private static int blue(int color) { return color & 0xFF; }
}
