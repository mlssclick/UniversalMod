package universalmod.render.color;


import universalmod.lifecycle.Manager;

import java.awt.Color;

import net.minecraft.util.math.MathHelper;
import universalmod.theme.ThemeManager;
import universalmod.util.math.MathUtil;

public final class ColorProvider {
    public static int red(int c) {
        return c >> 16 & 0xFF;
    }

    public static int green(int c) {
        return c >> 8 & 0xFF;
    }

    public static int blue(int c) {
        return c & 0xFF;
    }

    public static int alpha(int c) {
        return c >> 24 & 0xFF;
    }

    public static int getThemeColor() {
        return ThemeManager.getInstance().getCurrentTheme().getColorFirst();
    }

    public static int getThemeColorTwo() {
        return ThemeManager.getInstance().getCurrentTheme().getColorSecond();
    }

    public static int getColorMain() {
        return ThemeManager.getInstance().getCurrentTheme().colorMain;
    }

    public static int getColorVisualModules() {
        return ThemeManager.getInstance().getCurrentTheme().colorVisualModules;
    }

    public static int getColorText() {
        return ThemeManager.getInstance().getCurrentTheme().colorText;
    }

    public static int getColorInactiveText() {
        return ThemeManager.getInstance().getCurrentTheme().colorInactiveText;
    }

    public static int getColorHeaderBg() {
        return ThemeManager.getInstance().getCurrentTheme().colorHeaderBg;
    }

    public static int getColorHeaderText() {
        return ThemeManager.getInstance().getCurrentTheme().colorHeaderText;
    }

    public static int getColorSlider() {
        return ThemeManager.getInstance().getCurrentTheme().colorSlider;
    }

    public static int getColorSliderCircle() {
        return ThemeManager.getInstance().getCurrentTheme().colorSliderCircle;
    }

    public static int getColorSliderWindow() {
        return ThemeManager.getInstance().getCurrentTheme().colorSliderWindow;
    }

    public static int getColorIndicator() {
        return ThemeManager.getInstance().getCurrentTheme().colorIndicator;
    }

    public static int getColorInactiveIndicator() {
        return ThemeManager.getInstance().getCurrentTheme().colorInactiveIndicator;
    }

    public static int getColorButton() {
        return ThemeManager.getInstance().getCurrentTheme().colorButton;
    }

    public static int getColorInactiveButton() {
        return ThemeManager.getInstance().getCurrentTheme().colorInactiveButton;
    }

    public static int getColorSeparator() {
        return ThemeManager.getInstance().getCurrentTheme().colorSeparator;
    }

    public static int getColorField() {
        return ThemeManager.getInstance().getCurrentTheme().colorField;
    }

    public static int getColorInactiveField() {
        return ThemeManager.getInstance().getCurrentTheme().colorInactiveField;
    }

    public static int getColorTooltipText() {
        return ThemeManager.getInstance().getCurrentTheme().colorTooltipText;
    }

    public static int getColorWindowBg() {
        return ThemeManager.getInstance().getCurrentTheme().colorWindowBg;
    }

    public static int getColorIcons() {
        return ThemeManager.getInstance().getCurrentTheme().colorIcons;
    }

    public static int getColorClient() {
        return ThemeManager.getInstance().getCurrentTheme().colorClient;
    }

    public static int[] getOrbitalRect(int c1, int c2, double speed, int alpha) {
        int[] colors = new int[4];
        double time = (double) System.currentTimeMillis() / speed;

        for (int i = 0; i < 4; i++) {
            double phase = (double) i * (Math.PI / 2);
            int color = interpolateColor(c1, c2, (float) (Math.sin(time + phase) * 0.5 + 0.5));
            colors[i] = setAlpha(color, alpha);
        }

        return colors;
    }

    public static int gradient(int speed, int index, int... colors) {
        int angle = (int) ((System.currentTimeMillis() / (long) speed + (long) index) % 360L);
        angle = (angle > 180 ? 360 - angle : angle) + 180;
        int colorIndex = (int) ((float) angle / 360.0F * (float) colors.length);
        if (colorIndex == colors.length) {
            colorIndex--;
        }

        int color1 = colors[colorIndex];
        int color2 = colors[colorIndex == colors.length - 1 ? 0 : colorIndex + 1];
        return interpolateColor(color1, color2, (float) angle / 360.0F * (float) colors.length - (float) colorIndex);
    }

    public static int interpolateColor(int from, int to, float amount) {
        amount = Math.min(1.0F, Math.max(0.0F, amount));
        int red1 = red(to);
        int green1 = green(to);
        int blue1 = blue(to);
        int alpha1 = alpha(to);
        int red2 = red(from);
        int green2 = green(from);
        int blue2 = blue(from);
        int alpha2 = alpha(from);
        int interpolatedRed = interpolateInt(red1, red2, (double) amount);
        int interpolatedGreen = interpolateInt(green1, green2, (double) amount);
        int interpolatedBlue = interpolateInt(blue1, blue2, (double) amount);
        int interpolatedAlpha = interpolateInt(alpha1, alpha2, (double) amount);
        return interpolatedAlpha << 24 | interpolatedRed << 16 | interpolatedGreen << 8 | interpolatedBlue;
    }

    private static int interpolateInt(int oldValue, int newValue, double interpolationValue) {
        return interpolate(oldValue, newValue, (float) interpolationValue);
    }

    public static int interpolate(int start, int end, float value) {
        float[] startColor = rgba(start);
        float[] endColor = rgba(end);
        return rgba(
                (int) MathUtil.interpolate((double) (startColor[0] * 255.0F), (double) (endColor[0] * 255.0F), (double) value),
                (int) MathUtil.interpolate((double) (startColor[1] * 255.0F), (double) (endColor[1] * 255.0F), (double) value),
                (int) MathUtil.interpolate((double) (startColor[2] * 255.0F), (double) (endColor[2] * 255.0F), (double) value),
                (float) ((int) MathUtil.interpolate((double) (startColor[3] * 255.0F), (double) (endColor[3] * 255.0F), (double) value))
        );
    }

    public static float[] rgba(int color) {
        return new float[]{
                (float) (color >> 16 & 0xFF) / 255.0F, (float) (color >> 8 & 0xFF) / 255.0F, (float) (color & 0xFF) / 255.0F, (float) (color >> 24 & 0xFF) / 255.0F
        };
    }

    public static int rgba(int r, int g, int b, double a) {
        return (int) a << 24 | r << 16 | g << 8 | b;
    }

    public static int rgba(int r, int g, int b, float a) {
        return (int) a << 24 | r << 16 | g << 8 | b;
    }

    public static int rgb(int r, int g, int b) {
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    public static int setAlpha(int color, int alpha) {
        return MathHelper.clamp(alpha, 0, 255) << 24 | color & 16777215;
    }

    public static int setAlpha(int color, double alpha) {
        return MathHelper.clamp((int) alpha, 0, 255) << 24 | color & 16777215;
    }

    public static int brighter(int color, float factor) {
        int r = Math.min(255, (int) ((float) red(color) * factor));
        int g = Math.min(255, (int) ((float) green(color) * factor));
        int b = Math.min(255, (int) ((float) blue(color) * factor));
        int a = alpha(color);
        return rgba(r, g, b, (float) a);
    }

    public static int pack(int red, int green, int blue, int alpha) {
        return (alpha & 0xFF) << 24 | (red & 0xFF) << 16 | (green & 0xFF) << 8 | (blue & 0xFF) << 0;
    }

    public static int[] unpack(int color) {
        return new int[]{color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, color >> 24 & 0xFF};
    }

    public static float[] normalize(Color color) {
        return new float[]{(float) color.getRed() / 255.0F, (float) color.getGreen() / 255.0F, (float) color.getBlue() / 255.0F, (float) color.getAlpha() / 255.0F};
    }

    public static float[] normalize(int color) {
        int[] components = unpack(color);
        return new float[]{(float) components[0] / 255.0F, (float) components[1] / 255.0F, (float) components[2] / 255.0F, (float) components[3] / 255.0F};
    }
}
