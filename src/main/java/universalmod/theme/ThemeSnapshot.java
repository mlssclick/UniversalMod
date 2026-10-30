package universalmod.theme;

import universalmod.module.misc.CustomTheme;
import universalmod.render.color.ColorUtil;

import java.awt.Color;


public record ThemeSnapshot(
        int clickGuiText,
        int clickGuiMuted,
        int clickGuiBlur,
        int clickGuiAccent,
        int clickGuiHover,
        int clickGuiModule,
        int clickGuiEnabledModule,
        int clickGuiPanel,
        int hudAccent,
        int hudText,
        int hudMuted,
        int hudBlur,
        int hudSliderKnob,
        float clickGuiScale
) {
    public static final ThemeSnapshot DEFAULT = from(null);

    public static ThemeSnapshot from(CustomTheme theme) {
        Color guiText = theme == null ? new Color(255, 255, 255, 255) : theme.getClickGuiTitleColor();
        Color guiAccent = theme == null ? new Color(170, 132, 255, 255) : theme.getClickGuiAccentColor();
        Color guiModule = theme == null ? new Color(20, 19, 22, 255) : theme.getClickGuiModuleColor();
        Color guiEnabled = theme == null ? new Color(170, 132, 255, 255) : theme.getClickGuiEnabledModuleColor();
        Color guiPanel = theme == null ? new Color(13, 11, 16, 255) : theme.getClickGuiPanelColor();
        Color hudAccent = theme == null ? new Color(126, 101, 255, 255) : theme.getHudAccentColor();
        Color hudText = theme == null ? new Color(255, 255, 255, 255) : theme.getHudTextColor();
        Color hudBlur = theme == null ? new Color(0, 0, 0, 255) : theme.getHudBlurColor();
        float guiScale = theme == null ? 1.0F : (float) (theme.getClickGuiSizePercent() / 100.0);

        int guiTextArgb = argb(guiText);
        int hudAccentArgb = argb(hudAccent);
        int hudTextArgb = argb(hudText);
        return new ThemeSnapshot(
                guiTextArgb,
                scaleRgb(guiTextArgb, 0.72F),
                ColorUtil.rgba(13, 11, 16, 255),
                argb(guiAccent),
                scaleRgb(argb(guiAccent), 0.72F),
                argb(guiModule),
                argb(guiEnabled),
                argb(guiPanel),
                hudAccentArgb,
                hudTextArgb,
                scaleRgb(hudTextArgb, 0.74F),
                argb(hudBlur),
                brightenRgb(hudAccentArgb, 0.18F),
                guiScale
        );
    }

    private static int argb(Color color) {
        return ColorUtil.rgba(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
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

    private static int brightenRgb(int color, float amount) {
        float value = clamp01(amount);
        return ColorUtil.rgba(
                Math.round(ColorUtil.getRed(color) + (255.0F - ColorUtil.getRed(color)) * value),
                Math.round(ColorUtil.getGreen(color) + (255.0F - ColorUtil.getGreen(color)) * value),
                Math.round(ColorUtil.getBlue(color) + (255.0F - ColorUtil.getBlue(color)) * value),
                ColorUtil.getAlpha(color)
        );
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
