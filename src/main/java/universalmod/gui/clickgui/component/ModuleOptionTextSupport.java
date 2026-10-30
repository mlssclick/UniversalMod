package universalmod.gui.clickgui.component;


import universalmod.render.Render2D;
import universalmod.render.font.FontType;
import universalmod.service.language.LanguageManager;
import universalmod.setting.ModeSetting;
import universalmod.setting.MultiModeSetting;
import universalmod.setting.Setting;


final class ModuleOptionTextSupport {
    private ModuleOptionTextSupport() {
    }

    static String fitDescription(String text, float size, float maxWidth) {
        if (text == null || text.isBlank() || maxWidth <= 1.0f) return "";
        if (Render2D.textWidth(FontType.SEMIBOLD, text, size) <= maxWidth) return text;
        String ellipsis = "...";
        float ellipsisWidth = Render2D.textWidth(FontType.SEMIBOLD, ellipsis, size);
        int end = text.length();
        while (end > 0
                && Render2D.textWidth(FontType.SEMIBOLD, text.substring(0, end), size) + ellipsisWidth > maxWidth) {
            end--;
        }
        return end <= 0 ? ellipsis : text.substring(0, end).stripTrailing() + ellipsis;
    }

    static void warmupSettingValue(Setting setting) {
        if (setting instanceof ModeSetting modeSetting) {
            Render2D.warmupText(FontType.SEMIBOLD, modeSetting.getDisplayValue(), 9.0f);
            for (String mode : modeSetting.getDisplayModes()) {
                Render2D.warmupText(FontType.SEMIBOLD, mode, 9.0f);
            }
            return;
        }
        if (setting instanceof MultiModeSetting multiModeSetting) {
            Render2D.warmupText(FontType.SEMIBOLD,
                    LanguageManager.translateFormat("selected.count", multiModeSetting.selectedCount()), 9.0f);
            for (String mode : multiModeSetting.getDisplayModes()) {
                Render2D.warmupText(FontType.SEMIBOLD, mode, 9.0f);
            }
            return;
        }
        String value = setting.getValueAsString();
        if (value != null) {
            Render2D.warmupText(FontType.SEMIBOLD, value, 9.0f);
        }
    }
}
