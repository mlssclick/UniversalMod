package universalmod.gui.ui.control;

import java.awt.Color;
public final class ColorPickerModel {
    private float hue;
    private float saturation;
    private float brightness;
    private float alpha;
    private boolean hueInitialized;

    public ColorPickerModel() {
        this(0.0F, 0.0F, 1.0F, 1.0F);
    }

    public ColorPickerModel(float hue, float saturation, float brightness, float alpha) {
        set(hue, saturation, brightness, alpha);
        hueInitialized = true;
    }

    public void sync(Color color, float alphaOverride) {
        if (color == null) {
            return;
        }
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        if (!hueInitialized || hsb[1] > 0.001F) {
            hue = hsb[0];
            hueInitialized = true;
        }
        saturation = hsb[1];
        brightness = hsb[2];
        alpha = clamp01(alphaOverride);
    }

    public void set(float hue, float saturation, float brightness, float alpha) {
        this.hue = clamp01(hue);
        this.saturation = clamp01(saturation);
        this.brightness = clamp01(brightness);
        this.alpha = clamp01(alpha);
    }

    public void setPaletteFromPoint(double mouseX, double mouseY, Bounds palette) {
        if (palette.width() <= 0.0F || palette.height() <= 0.0F) {
            return;
        }
        saturation = clamp01((float) ((mouseX - palette.x()) / palette.width()));
        brightness = 1.0F - clamp01((float) ((mouseY - palette.y()) / palette.height()));
    }

    public void setHueFromPoint(double mouseX, Bounds hueBar) {
        if (hueBar.width() <= 0.0F) {
            return;
        }
        hue = clamp01((float) ((mouseX - hueBar.x()) / hueBar.width()));
        hueInitialized = true;
    }

    public void setHueFromVerticalPoint(double mouseY, Bounds hueBar) {
        if (hueBar.height() <= 0.0F) {
            return;
        }
        hue = clamp01((float) ((mouseY - hueBar.y()) / hueBar.height()));
        hueInitialized = true;
    }

    public void setAlphaFromPoint(double mouseX, Bounds alphaBar) {
        if (alphaBar.width() <= 0.0F) {
            return;
        }
        alpha = clamp01((float) ((mouseX - alphaBar.x()) / alphaBar.width()));
    }

    public int rgb() {
        return Color.HSBtoRGB(hue, saturation, brightness);
    }

    public Color color() {
        int rgb = rgb();
        return new Color((rgb >>> 16) & 0xFF, (rgb >>> 8) & 0xFF, rgb & 0xFF,
                Math.round(alpha * 255.0F));
    }

    public float hue() { return hue; }
    public float saturation() { return saturation; }
    public float brightness() { return brightness; }
    public float alpha() { return alpha; }

    public static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
