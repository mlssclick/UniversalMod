package universalmod.hud;

import universalmod.setting.ColorSetting;

import java.awt.Color;

public interface HudConfiguration {
    boolean isEnabled();

    boolean isBossbarSelected();

    boolean shouldRenderBossbar();

    boolean keystrokesShowKeys();

    boolean keystrokesShowMouseButtons();

    boolean keystrokesShowSpace();

    float keystrokesOpacity();

    Color keystrokesNormalColor();

    Color keystrokesPressedColor();

    Color keystrokesLetterColor();

    ColorSetting keystrokesNormalColorSetting();

    ColorSetting keystrokesPressedColorSetting();

    ColorSetting keystrokesLetterColorSetting();

    float keystrokesPressedAlpha();

    Color potionsColor();

    Color cooldownsColor();

    boolean useCustomBossbarSettings();

    Color bossbarColor();

    Color bossbarTextColor();
}
