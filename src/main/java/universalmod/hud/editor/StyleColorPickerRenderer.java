package universalmod.hud.editor;

import universalmod.render.Render2D;
import universalmod.gui.ui.control.HueStripRenderer;
import universalmod.render.color.ColorUtil;

import java.awt.Color;

final class StyleColorPickerRenderer {

    private StyleColorPickerRenderer() {
    }

    static void palette(float x, float y, float size, float hue, float saturation, float brightness, float alpha) {
        int hueColor = Color.HSBtoRGB(hue, 1.0F, 1.0F);
        int topLeft = ColorUtil.rgba(255, 255, 255, Math.round(255.0F * alpha));
        int topRight = ColorUtil.rgba(red(hueColor), green(hueColor), blue(hueColor), Math.round(255.0F * alpha));
        int bottom = ColorUtil.rgba(0, 0, 0, Math.round(255.0F * alpha));
        Render2D.rect(x, y, size, size, 4.0F, topLeft, topRight, bottom, bottom);
        float selectorX = x + saturation * size;
        float selectorY = y + (1.0F - brightness) * size;
        Render2D.outline(selectorX - 2.5F, selectorY - 2.5F, 5.0F, 5.0F, 1.5F, 1.0F, ColorUtil.rgba(255, 255, 255, Math.round(245.0F * alpha)));
        Render2D.rect(selectorX - 1.0F, selectorY - 1.0F, 2.0F, 2.0F, 0.5F, ColorUtil.rgba(20, 24, 32, Math.round(190.0F * alpha)));
    }

    static void hue(float x, float y, float width, float height, float hue, float alpha) {
        HueStripRenderer.render(x, y, width, height, 2.0F, alpha, true);
        float selectorY = y + hue * height;
        Render2D.rect(x - 1.0F, selectorY - 1.0F, width + 2.0F, 2.0F, ColorUtil.rgba(255, 255, 255, Math.round(255.0F * alpha)));
    }

    static void alpha(float x, float y, float width, float height, float hue, float saturation, float brightness, float value, float alpha) {
        int rgb = Color.HSBtoRGB(hue, saturation, brightness);
        int a = Math.round(255.0F * alpha);
        float radius = 2.5F;




        Render2D.rect(x, y, width, height, radius,
                ColorUtil.rgba(22, 21, 26, a),
                ColorUtil.rgba(red(rgb), green(rgb), blue(rgb), a),
                ColorUtil.rgba(red(rgb), green(rgb), blue(rgb), a),
                ColorUtil.rgba(22, 21, 26, a));
        Render2D.outline(x, y, width, height, radius, 0.45F,
                ColorUtil.rgba(255, 255, 255, Math.round(66.0F * alpha)));

        float markerWidth = Math.min(4.0F, Math.max(2.5F, width * 0.035F));
        float half = markerWidth * 0.5F;
        float selectorX = clamp(x + width * value, x + half, x + width - half);
        Render2D.rect(selectorX - half, y - 1.0F, markerWidth, height + 2.0F,
                Math.min(1.8F, markerWidth * 0.5F),
                ColorUtil.rgba(248, 247, 250, a));
        Render2D.outline(selectorX - half, y - 1.0F, markerWidth, height + 2.0F,
                Math.min(1.8F, markerWidth * 0.5F), 0.45F,
                ColorUtil.rgba(18, 16, 22, Math.round(220.0F * alpha)));
    }


    static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
    static float clamp01(float value) { return clamp(value, 0.0F, 1.0F); }
    static boolean inside(float px, float py, float x, float y, float width, float height) { return px >= x && px <= x + width && py >= y && py <= y + height; }
    static int alpha(int color) { return (color >>> 24) & 0xFF; }
    static int red(int color) { return (color >>> 16) & 0xFF; }
    static int green(int color) { return (color >>> 8) & 0xFF; }
    static int blue(int color) { return color & 0xFF; }
}
