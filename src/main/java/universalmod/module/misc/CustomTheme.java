package universalmod.module.misc;

import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.setting.ColorSetting;
import universalmod.setting.ModeSetting;
import universalmod.setting.NumberSetting;
import universalmod.service.language.LanguageCode;
import universalmod.service.language.LanguageManager;
import universalmod.theme.ThemeColors;
import universalmod.setting.Setting;

import java.awt.Color;

public final class CustomTheme extends Module {
    private static volatile CustomTheme instance;

    private final ModeSetting language = register(new ModeSetting(
            "Language", "Interface language.", LanguageCode.RU_RU.modeName(),
            LanguageCode.RU_RU.modeName(), LanguageCode.EN_US.modeName(), LanguageCode.UK_UA.modeName()));
    private final ColorSetting hudAccentColor = register(new ColorSetting(
            "HUD Accent", "Accent color for HUD highlights, bars and indicators.", new Color(126, 101, 255, 255)));
    private final ColorSetting clickGuiTitleColor = register(new ColorSetting(
            "ClickGUI Titles", "Custom color for titles and names in ClickGUI.", new Color(255, 255, 255, 255)));
    private final ColorSetting clickGuiAccentColor = register(new ColorSetting(
            "ClickGUI Accent", "Accent color for selected and highlighted ClickGUI elements.", new Color(170, 132, 255, 255)));
    private final ColorSetting clickGuiModuleColor = register(new ColorSetting(
            "ClickGUI Modules", "Surface color for module cards.", new Color(20, 19, 22, 255)));
    private final ColorSetting clickGuiEnabledModuleColor = register(new ColorSetting(
            "Enabled Module", "Highlight color for enabled module cards.", new Color(170, 132, 255, 255)));
    private final ColorSetting clickGuiPanelColor = register(new ColorSetting(
            "ClickGUI Panel", "Main ClickGUI surface color.", new Color(13, 11, 16, 255)));
    private final NumberSetting clickGuiSize = register(new NumberSetting(
            "ClickGUI Size", "ClickGUI scale in percent.", 100.0, 50.0, 150.0, 1.0));
    private final ColorSetting hudBlurColor = register(new ColorSetting(
            "HUD Blur", "Custom blur color for HUD panels.", new Color(0, 0, 0, 255)));
    private final ColorSetting hudTextColor = register(new ColorSetting(
            "HUD Text", "Custom text color for HUD panels.", new Color(255, 255, 255, 255)));

    public CustomTheme() {
        super("themes", "Custom Theme", ModuleCategory.MISC);
        instance = this;
        LanguageManager.setCurrent(language.getValue());
        language.addListener((changedSetting, oldValue, newValue) -> LanguageManager.setCurrent(newValue));
        for (Setting<?> setting : getSettings()) {
            setting.addListener((changedSetting, oldValue, newValue) -> ThemeColors.refresh());
        }
        ThemeColors.bind(this);
    }

    public static CustomTheme getInstance() {
        return instance;
    }

    public String getLanguage() {
        return language.getValue();
    }

    public void cycleLanguage() {
        LanguageCode current = LanguageCode.fromMode(language.getValue());
        LanguageCode[] values = LanguageCode.values();
        int nextIndex = (current.ordinal() + 1) % values.length;
        LanguageCode next = values[nextIndex];
        language.setValue(next.modeName());
        LanguageManager.setCurrent(next);
    }

    public String getLanguageDisplayName() {
        LanguageCode current = LanguageCode.fromMode(language.getValue());
        String key = "language.name." + current.fileName();
        String translated = LanguageManager.translate(key);
        return translated == null || translated.equals(key) ? current.modeName() : translated;
    }

    public ModeSetting getLanguageSetting() { return language; }

    public Color getHudAccentColor() { return hudAccentColor.getValue(); }
    public void setHudAccentColor(Color value) { hudAccentColor.setValue(value); }
    public ColorSetting getHudAccentColorSetting() { return hudAccentColor; }

    public Color getClickGuiTitleColor() { return clickGuiTitleColor.getValue(); }
    public Color getClickGuiAccentColor() { return clickGuiAccentColor.getValue(); }
    public Color getClickGuiModuleColor() { return clickGuiModuleColor.getValue(); }
    public Color getClickGuiEnabledModuleColor() { return clickGuiEnabledModuleColor.getValue(); }
    public Color getClickGuiPanelColor() { return clickGuiPanelColor.getValue(); }
    public double getClickGuiSizePercent() { return clickGuiSize.getValue(); }
    public Color getHudBlurColor() { return hudBlurColor.getValue(); }
    public Color getHudTextColor() { return hudTextColor.getValue(); }

    public void setClickGuiSizePercent(double value) { clickGuiSize.setValue(value); }
    public void setClickGuiTitleColor(Color value) { clickGuiTitleColor.setValue(value); }
    public void setClickGuiAccentColor(Color value) { clickGuiAccentColor.setValue(value); }
    public void setClickGuiModuleColor(Color value) { clickGuiModuleColor.setValue(value); }
    public void setClickGuiEnabledModuleColor(Color value) { clickGuiEnabledModuleColor.setValue(value); }
    public void setClickGuiPanelColor(Color value) { clickGuiPanelColor.setValue(value); }

    public NumberSetting getClickGuiSizeSetting() { return clickGuiSize; }
    public ColorSetting getClickGuiTitleColorSetting() { return clickGuiTitleColor; }
    public ColorSetting getClickGuiAccentColorSetting() { return clickGuiAccentColor; }
    public ColorSetting getClickGuiModuleColorSetting() { return clickGuiModuleColor; }
    public ColorSetting getClickGuiEnabledModuleColorSetting() { return clickGuiEnabledModuleColor; }
    public ColorSetting getClickGuiPanelColorSetting() { return clickGuiPanelColor; }
}
