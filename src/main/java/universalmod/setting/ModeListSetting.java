package universalmod.setting;

import universalmod.setting.Setting;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import universalmod.service.language.LanguageManager;

public class ModeListSetting extends Setting {
    private final List<BooleanSetting> settings;

    public ModeListSetting(String name, BooleanSetting... settings) {
        super(name);
        this.settings = Arrays.asList(settings);
        for (BooleanSetting setting : this.settings) {
            setting.addChangeListener(this::fireChanged);
        }
    }

    public void reset() {
        for (BooleanSetting setting : this.settings) {
            setting.reset();
        }
    }

    public List<BooleanSetting> getSettings() {
        return this.settings;
    }

    public List<String> getEnabledModules() {
        return this.settings.stream().filter(BooleanSetting::getValue).map(Setting::getName).collect(Collectors.toList());
    }

    public boolean isEnabled(String moduleName) {
        return this.settings.stream().anyMatch(setting -> setting.getName().equalsIgnoreCase(moduleName) && setting.getValue());
    }


    public List<String> getModes() {
        return this.settings.stream().map(Setting::getName).collect(Collectors.toList());
    }

    public String getDisplayMode(String mode) {
        return LanguageManager.settingOption(getLocalizationOwner(), getName(), mode);
    }

    public List<String> getDisplayModes() {
        return getModes().stream().map(this::getDisplayMode).collect(Collectors.toList());
    }

    public boolean isSelected(String mode) {
        return isEnabled(mode);
    }

    public void setSelected(String mode, boolean selected) {
        this.settings.stream()
                .filter(setting -> setting.getName().equalsIgnoreCase(mode))
                .findFirst()
                .ifPresent(setting -> setting.setValue(selected));
    }

    public int selectedCount() {
        return (int) this.settings.stream().filter(BooleanSetting::getValue).count();
    }

    @Override
    public String getValueAsString() {
        List<String> enabled = this.getEnabledModules();
        return enabled.isEmpty() ? "None" : String.join(", ", enabled);
    }

    @Override
    public void setValueFromString(String value) {
        List<String> names = Arrays.asList(value.split(",\\s*"));

        for (BooleanSetting setting : this.settings) {
            setting.setValue(names.contains(setting.getName()));
        }
    }

    public ModeListSetting setVisible(Supplier<Boolean> visible) {
        this.visible = visible;
        return this;
    }
}
