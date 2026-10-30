package universalmod.hud.editor;

import universalmod.module.render.Scoreboard;
import universalmod.setting.SliderSetting;

import java.util.Locale;


final class ScoreboardStyleMenuMetrics {
    private ScoreboardStyleMenuMetrics() {
    }

    static float controlX(float menuX, float menuWidth, float padding, float controlWidth) {
        return menuX + menuWidth - padding - controlWidth;
    }

    static float numberSliderX(
            SliderSetting setting,
            Scoreboard scoreboard,
            float menuX,
            float menuWidth,
            float padding,
            float controlWidth,
            float sizeControlWidth
    ) {
        return setting == scoreboard.editorScaleSetting()
                ? menuX + menuWidth - padding - sizeControlWidth
                : controlX(menuX, menuWidth, padding, controlWidth);
    }

    static float numberSliderWidth(
            SliderSetting setting,
            Scoreboard scoreboard,
            float controlWidth,
            float sizeControlWidth
    ) {
        return setting == scoreboard.editorScaleSetting() ? sizeControlWidth : controlWidth;
    }

    static float numberProgress(SliderSetting setting) {
        double range = setting.getMax() - setting.getMin();
        if (Math.abs(range) <= 0.000001D) {
            return 0.0F;
        }
        return clamp01((float) ((setting.getValue() - setting.getMin()) / range));
    }

    static String formatNumber(SliderSetting setting, boolean size) {
        if (size) {
            return Math.round(setting.getFloat() * 100.0F) + "%";
        }
        double value = setting.getValue();
        double step = setting.getStep();
        if (step >= 1.0D) {
            return Long.toString(Math.round(value));
        }
        if (step >= 0.1D) {
            return String.format(Locale.ROOT, "%.1f", value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
