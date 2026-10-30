package universalmod.theme;

import universalmod.module.misc.CustomTheme;
import universalmod.render.color.ColorUtil;

public final class ThemeColors {
    private static volatile CustomTheme source;
    private static volatile ThemeSnapshot snapshot = ThemeSnapshot.DEFAULT;

    private ThemeColors() {
    }

    public static void bind(CustomTheme theme) {
        source = theme;
        refresh();
    }

    public static void refresh() {
        snapshot = ThemeSnapshot.from(source);
    }

    public static ThemeSnapshot snapshot() {
        return snapshot;
    }

    public static int clickGuiTextColor(int alpha, float multiplier) {
        return themed(snapshot.clickGuiText(), alpha, multiplier);
    }

    public static int clickGuiMutedColor(int alpha, float multiplier) {
        return themed(snapshot.clickGuiMuted(), alpha, multiplier);
    }

    
    public static int clickGuiBlurColor(int alpha, float multiplier) {
        return themed(snapshot.clickGuiBlur(), alpha, multiplier);
    }

    
    public static int clickGuiSliderFillColor(float multiplier) {
        return themed(snapshot.hudAccent(), 255, multiplier);
    }

    public static int clickGuiSliderKnobColor(float multiplier) {
        return themed(snapshot.hudSliderKnob(), 245, multiplier);
    }

    public static int clickGuiModuleTextColor(float multiplier, float enabledProgress, float bindProgress) {
        float shade = 0.73F + 0.27F * clamp01(enabledProgress);
        return themed(scaleRgb(snapshot.clickGuiText(), shade),
                Math.round(255.0F * (1.0F - bindProgress)), multiplier);
    }

    public static int clickGuiModuleBarColor(float multiplier, float enabledProgress, float bindProgress) {
        float shade = 0.73F + 0.27F * clamp01(enabledProgress);
        return themed(scaleRgb(snapshot.clickGuiText(), shade),
                Math.round(50.0F * (1.0F - bindProgress)), multiplier);
    }

    public static int hudBlurColor(int fallbackColor) {
        int color = snapshot.hudBlur();
        int alpha = Math.round(ColorUtil.getAlpha(fallbackColor) * (ColorUtil.getAlpha(color) / 255.0F));
        return ColorUtil.rgba(ColorUtil.getRed(color), ColorUtil.getGreen(color), ColorUtil.getBlue(color), alpha);
    }

    public static int hudTextColor(int alpha) {
        return themed(snapshot.hudText(), alpha, 1.0F);
    }

    public static int hudMutedColor(int alpha) {
        return themed(snapshot.hudMuted(), alpha, 1.0F);
    }

    public static int hudAccentColor(int alpha) {
        return themed(snapshot.hudAccent(), alpha, 1.0F);
    }

    public static float clickGuiScale() {
        return snapshot.clickGuiScale();
    }

    public static int clickGuiAccentColor(int alpha, float multiplier) {
        return themed(snapshot.clickGuiAccent(), alpha, multiplier);
    }

    public static int clickGuiHoverColor(int alpha, float multiplier) {
        return themed(snapshot.clickGuiHover(), alpha, multiplier);
    }

    public static int clickGuiModuleColor(int alpha, float multiplier) {
        return themed(snapshot.clickGuiModule(), alpha, multiplier);
    }

    public static int clickGuiEnabledModuleColor(int alpha, float multiplier) {
        return themed(snapshot.clickGuiEnabledModule(), alpha, multiplier);
    }

    public static int clickGuiPanelColor(int alpha, float multiplier) {
        return themed(snapshot.clickGuiPanel(), alpha, multiplier);
    }

    private static int themed(int color, int alpha, float multiplier) {
        int scaledAlpha = Math.round(alpha * (ColorUtil.getAlpha(color) / 255.0F) * multiplier);
        return ColorUtil.rgba(ColorUtil.getRed(color), ColorUtil.getGreen(color), ColorUtil.getBlue(color), scaledAlpha);
    }

    private static int scaleRgb(int color, float multiplier) {
        float value = clamp01(multiplier);
        return ColorUtil.rgba(
                Math.round(ColorUtil.getRed(color) * value),
                Math.round(ColorUtil.getGreen(color) * value),
                Math.round(ColorUtil.getBlue(color) * value),
                ColorUtil.getAlpha(color)
        );
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
