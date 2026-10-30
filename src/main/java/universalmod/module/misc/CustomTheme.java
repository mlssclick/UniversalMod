package universalmod.module.misc;

import java.awt.Color;

import universalmod.service.language.LanguageCode;
import universalmod.service.language.LanguageManager;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;
import universalmod.setting.ColorSetting;
import universalmod.setting.ModeSetting;
import universalmod.setting.NumberSetting;

@ModuleInformation(
        moduleName = "Custom Theme",
        moduleCategory = ModuleCategory.MISC
)
public final class CustomTheme extends Module {
    private static CustomTheme instance;

    private final ModeSetting language = new ModeSetting(
            "Language",
            LanguageCode.RU_RU.modeName(),
            LanguageCode.RU_RU.modeName(),
            LanguageCode.EN_US.modeName(),
            LanguageCode.UK_UA.modeName()
    );
    private final ColorSetting sliderColor = new ColorSetting(
            "Slider Color", new Color(255, 255, 255, 255).getRGB());
    private final ColorSetting clickGuiTitleColor = new ColorSetting(
            "ClickGUI Titles", new Color(255, 255, 255, 255).getRGB());
    private final ColorSetting clickGuiBlurColor = new ColorSetting(
            "ClickGUI Blur", new Color(13, 11, 16, 255).getRGB());
    private final ColorSetting clickGuiAccentColor = new ColorSetting(
            "ClickGUI Accent", new Color(170, 132, 255, 255).getRGB());
    private final ColorSetting clickGuiHoverColor = new ColorSetting(
            "ClickGUI Hover", new Color(122, 99, 166, 255).getRGB());
    private final ColorSetting clickGuiModuleColor = new ColorSetting(
            "ClickGUI Modules", new Color(20, 19, 22, 255).getRGB());
    private final ColorSetting clickGuiEnabledModuleColor = new ColorSetting(
            "Enabled Module", new Color(170, 132, 255, 255).getRGB());
    private final ColorSetting clickGuiPanelColor = new ColorSetting(
            "ClickGUI Panel", new Color(13, 11, 16, 255).getRGB());
    private final NumberSetting clickGuiSize = new NumberSetting(
            "ClickGUI Size", 100.0, 50.0, 150.0, 1.0);
    private final ColorSetting hudBlurColor = new ColorSetting(
            "HUD Blur", new Color(0, 0, 0, 255).getRGB());
    private final ColorSetting hudTextColor = new ColorSetting(
            "HUD Text", new Color(255, 255, 255, 255).getRGB());
    private final ColorSetting hudAccentColor = new ColorSetting(
            "HUD Accent", "Accent color for HUD highlights, bars and indicators.", new Color(126, 101, 255, 255).getRGB());

    public CustomTheme() {
        instance = this;
        language.setDescription("Interface language.");
        hudAccentColor.setDescription("Accent color for HUD highlights, bars and indicators.");
        clickGuiTitleColor.setDescription("Custom color for titles and names in ClickGUI.");
        clickGuiAccentColor.setDescription("Accent color for selected and highlighted ClickGUI elements.");
        clickGuiModuleColor.setDescription("Surface color for module cards.");
        clickGuiEnabledModuleColor.setDescription("Highlight color for enabled module cards.");
        clickGuiPanelColor.setDescription("Main ClickGUI surface color.");
        clickGuiSize.setDescription("ClickGUI scale in percent.");
        hudBlurColor.setDescription("Custom blur color for HUD panels.");
        hudTextColor.setDescription("Custom text color for HUD panels.");
        LanguageManager.setCurrent(language.getValue());
        language.addListener((ModeSetting.Listener) (setting, oldValue, newValue) -> LanguageManager.setCurrent(newValue));
    }

    public static CustomTheme getInstance() { return instance; }
public String getLanguage() { return language.getValue(); }

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

    public ModeSetting getLanguageSetting() {
        return language;
    }

    public Color getSliderColor() { return color(sliderColor); }
    public Color getClickGuiTitleColor() { return color(clickGuiTitleColor); }
    public Color getClickGuiBlurColor() { return color(clickGuiBlurColor); }
    public Color getClickGuiAccentColor() { return color(clickGuiAccentColor); }
    public Color getClickGuiHoverColor() { return color(clickGuiHoverColor); }
    public Color getClickGuiModuleColor() { return color(clickGuiModuleColor); }
    public Color getClickGuiEnabledModuleColor() { return color(clickGuiEnabledModuleColor); }
    public Color getClickGuiPanelColor() { return color(clickGuiPanelColor); }
    public Color getHudBlurColor() { return color(hudBlurColor); }
    public Color getHudTextColor() { return color(hudTextColor); }
    public ColorSetting getHudAccentColorSetting() { return hudAccentColor; }
    public Color getHudAccentColor() { return color(hudAccentColor); }
    public void setHudAccentColor(Color value) { hudAccentColor.setValue(rgb(value)); }
    public double getClickGuiSizePercent() { return clickGuiSize.getValue(); }

    public void setSliderColor(Color value) { sliderColor.setValue(rgb(value)); }
    public void setClickGuiTitleColor(Color value) { clickGuiTitleColor.setValue(rgb(value)); }
    public void setClickGuiBlurColor(Color value) { clickGuiBlurColor.setValue(rgb(value)); }
    public void setClickGuiAccentColor(Color value) { clickGuiAccentColor.setValue(rgb(value)); }
    public void setClickGuiHoverColor(Color value) { clickGuiHoverColor.setValue(rgb(value)); }
    public void setClickGuiModuleColor(Color value) { clickGuiModuleColor.setValue(rgb(value)); }
    public void setClickGuiEnabledModuleColor(Color value) { clickGuiEnabledModuleColor.setValue(rgb(value)); }
    public void setClickGuiPanelColor(Color value) { clickGuiPanelColor.setValue(rgb(value)); }
    public void setClickGuiSizePercent(double value) { clickGuiSize.setValue(value); }

    public NumberSetting getClickGuiSizeSetting() { return clickGuiSize; }
    public ColorSetting getClickGuiTitleColorSetting() { return clickGuiTitleColor; }
    public ColorSetting getClickGuiBlurColorSetting() { return clickGuiBlurColor; }
    public ColorSetting getClickGuiAccentColorSetting() { return clickGuiAccentColor; }
    public ColorSetting getClickGuiHoverColorSetting() { return clickGuiHoverColor; }
    public ColorSetting getClickGuiModuleColorSetting() { return clickGuiModuleColor; }
    public ColorSetting getClickGuiEnabledModuleColorSetting() { return clickGuiEnabledModuleColor; }
    public ColorSetting getClickGuiPanelColorSetting() { return clickGuiPanelColor; }
    public ColorSetting getSliderColorSetting() { return sliderColor; }

    private static Color color(ColorSetting setting) {
        return new Color(setting.getValue(), true);
    }

    private static int rgb(Color value) {
        return value == null ? 0xFFFFFFFF : value.getRGB();
    }
}
