package universalmod.hud.editor;

import universalmod.render.Render2D;
import universalmod.render.font.FontType;
import universalmod.service.language.LanguageManager;
import universalmod.setting.ColorSetting;
import universalmod.setting.ModeSetting;
import universalmod.setting.NumberSetting;
import universalmod.setting.Setting;


final class HudStyleMenuMetrics {
    private static final float BOOLEAN_HEIGHT = 16.0F;
    private static final float COLOR_HEIGHT = 32.0F;

    private HudStyleMenuMetrics() {
    }

    static HudEditorControls.SliderBounds sizeBounds(float panelX, float rowY) {
        return HudEditorControls.sliderBounds(
                LanguageManager.ui("hud_editor.size"),
                panelX + u(16.0F),
                rowY,
                HudEditorControls.panelWidth() - u(32.0F)
        );
    }

    static HudEditorControls.SliderBounds numberBounds(NumberSetting setting, float panelX, float rowY) {
        return HudEditorControls.sliderBounds(
                setting.getDisplayName(),
                panelX + u(16.0F),
                rowY,
                HudEditorControls.panelWidth() - u(32.0F)
        );
    }

    static HudEditorControls.SegmentBounds modeBounds(float panelX, float rowY) {
        return genericModeBounds(panelX, rowY, LanguageManager.ui("hud_editor.mode"));
    }

    static HudEditorControls.SegmentBounds genericModeBounds(float panelX, float rowY, String label) {
        float labelHeight = Render2D.textHeight(FontType.BOLD, label, u(14.0F));
        return new HudEditorControls.SegmentBounds(
                panelX + u(16.0F),
                rowY + labelHeight + u(8.0F),
                HudEditorControls.panelWidth() - u(32.0F),
                u(32.0F)
        );
    }

    static float sizeRowHeight() {
        return HudEditorControls.sliderHeight(LanguageManager.ui("hud_editor.size"));
    }

    static float modeRowHeight() {
        return HudEditorControls.segmentedHeight(LanguageManager.ui("hud_editor.mode"));
    }

    static float settingHeight(Setting<?> setting) {
        if (setting instanceof NumberSetting) {
            return HudEditorControls.sliderHeight(setting.getDisplayName());
        }
        if (setting instanceof ColorSetting) {
            return u(COLOR_HEIGHT);
        }
        if (setting instanceof ModeSetting) {
            return HudEditorControls.segmentedHeight(setting.getDisplayName());
        }
        return u(BOOLEAN_HEIGHT);
    }

    private static float u(float value) {
        return HudEditorControls.u(value);
    }
}
