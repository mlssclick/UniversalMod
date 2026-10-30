package universalmod.theme;

import java.awt.Color;

import universalmod.render.color.ColorUtil;
import universalmod.module.misc.CustomTheme;




public final class ThemeColors {
    private ThemeColors() {
    }

    public static int clickGuiTextColor(int alpha, float multiplier) {
        return themed(clickGuiTheme(), 255, 255, 255, alpha, multiplier);
    }

    public static int clickGuiMutedColor(int alpha, float multiplier) {
        return themed(scale(clickGuiTheme(), 0.72f), 175, 175, 175, alpha, multiplier);
    }

    public static int clickGuiBlurColor(int alpha, float multiplier) {
        return themed(clickGuiBlurTheme(), 0, 0, 0, alpha, multiplier);
    }

    public static int clickGuiSliderFillColor(float multiplier) {
        return themed(sliderTheme(), 255, 255, 255, 230, multiplier);
    }

    public static int clickGuiSliderKnobColor(float multiplier) {
        return themed(brighten(sliderTheme(), 0.18f), 215, 215, 215, 245, multiplier);
    }

    public static int clickGuiModuleTextColor(float multiplier, float enabledProgress, float bindProgress) {
        float shade = 0.73f + 0.27f * clamp01(enabledProgress);
        return themed(
                scale(clickGuiTheme(), shade),
                255,
                255,
                255,
                Math.round(255.0f * (1.0f - bindProgress)),
                multiplier
        );
    }

    public static int clickGuiModuleBarColor(float multiplier, float enabledProgress, float bindProgress) {
        float shade = 0.73f + 0.27f * clamp01(enabledProgress);
        return themed(
                scale(clickGuiTheme(), shade),
                255,
                255,
                255,
                Math.round(50.0f * (1.0f - bindProgress)),
                multiplier
        );
    }

    public static int hudBlurColor(int fallbackColor) {
        Color color = hudBlurTheme();
        int alpha = Math.round(ColorUtil.getAlpha(fallbackColor) * (color.getAlpha() / 255.0f));
        return ColorUtil.rgba(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    public static int hudTextColor(int alpha) {
        return themed(hudTextTheme(), 255, 255, 255, alpha, 1.0f);
    }

    public static int hudMutedColor(int alpha) {
        return themed(scale(hudTextTheme(), 0.74f), 185, 190, 196, alpha, 1.0f);
    }

    public static int hudAccentColor(int alpha) {
        CustomTheme theme = CustomTheme.getInstance();
        Color accent = theme == null ? null : theme.getHudAccentColor();
        return themed(accent, 126, 101, 255, alpha, 1.0f);
    }


    public static float clickGuiScale() {
        CustomTheme theme = CustomTheme.getInstance();
        return theme == null ? 1.0f : (float) (theme.getClickGuiSizePercent() / 100.0);
    }

    public static int clickGuiAccentColor(int alpha, float multiplier) {
        CustomTheme theme = CustomTheme.getInstance();
        Color c = theme == null ? new Color(170, 132, 255, 255) : theme.getClickGuiAccentColor();
        return themed(c, 170, 132, 255, alpha, multiplier);
    }

    public static int clickGuiHoverColor(int alpha, float multiplier) {
        CustomTheme theme = CustomTheme.getInstance();
        Color c = theme == null ? new Color(122, 99, 166, 255) : theme.getClickGuiHoverColor();
        return themed(c, 122, 99, 166, alpha, multiplier);
    }

    public static int clickGuiModuleColor(int alpha, float multiplier) {
        CustomTheme theme = CustomTheme.getInstance();
        Color c = theme == null ? new Color(20, 19, 22, 255) : theme.getClickGuiModuleColor();
        return themed(c, 20, 19, 22, alpha, multiplier);
    }

    public static int clickGuiEnabledModuleColor(int alpha, float multiplier) {
        CustomTheme theme = CustomTheme.getInstance();
        Color c = theme == null ? new Color(170, 132, 255, 255) : theme.getClickGuiEnabledModuleColor();
        return themed(c, 170, 132, 255, alpha, multiplier);
    }

    public static int clickGuiPanelColor(int alpha, float multiplier) {
        CustomTheme theme = CustomTheme.getInstance();
        Color c = theme == null ? new Color(13, 11, 16, 255) : theme.getClickGuiPanelColor();
        return themed(c, 13, 11, 16, alpha, multiplier);
    }

    private static Color sliderTheme() {
        CustomTheme theme = CustomTheme.getInstance();
        return theme == null ? new Color(255, 255, 255, 255) : theme.getSliderColor();
    }

    private static Color clickGuiTheme() {
        CustomTheme theme = CustomTheme.getInstance();
        return theme == null ? new Color(255, 255, 255, 255) : theme.getClickGuiTitleColor();
    }

    private static Color clickGuiBlurTheme() {
        CustomTheme theme = CustomTheme.getInstance();
        return theme == null ? new Color(0, 0, 0, 255) : theme.getClickGuiBlurColor();
    }

    private static Color hudBlurTheme() {
        CustomTheme theme = CustomTheme.getInstance();
        return theme == null ? new Color(0, 0, 0, 255) : theme.getHudBlurColor();
    }

    private static Color hudTextTheme() {
        CustomTheme theme = CustomTheme.getInstance();
        return theme == null ? new Color(255, 255, 255, 255) : theme.getHudTextColor();
    }

    private static int themed(Color color, int red, int green, int blue, int alpha, float multiplier) {
        if (color == null) {
            return ColorUtil.rgba(red, green, blue, Math.round(alpha * multiplier));
        }
        int scaledAlpha = Math.round(alpha * (color.getAlpha() / 255.0f) * multiplier);
        return ColorUtil.rgba(color.getRed(), color.getGreen(), color.getBlue(), scaledAlpha);
    }

    private static Color scale(Color color, float multiplier) {
        float value = clamp01(multiplier);
        return new Color(
                Math.round(color.getRed() * value),
                Math.round(color.getGreen() * value),
                Math.round(color.getBlue() * value),
                color.getAlpha()
        );
    }

    private static Color brighten(Color color, float amount) {
        float value = clamp01(amount);
        return new Color(
                Math.round(color.getRed() + (255.0f - color.getRed()) * value),
                Math.round(color.getGreen() + (255.0f - color.getGreen()) * value),
                Math.round(color.getBlue() + (255.0f - color.getBlue()) * value),
                color.getAlpha()
        );
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
