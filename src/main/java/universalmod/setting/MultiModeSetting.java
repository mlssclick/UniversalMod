package universalmod.setting;

import universalmod.setting.Setting;

import java.util.*;
import java.util.function.Supplier;

public class MultiModeSetting extends Setting {
    private final List<BooleanSetting> settings;

    public MultiModeSetting(String name, BooleanSetting... settings) {
        super(name);
        this.settings = new ArrayList<>(Arrays.asList(settings == null ? new BooleanSetting[0] : settings));
        hookChildren();
    }

    public MultiModeSetting(String name, String description, String[] modes, String... selected) {
        this(name, description, true, modes, selected);
    }

    public MultiModeSetting(String name, String description, boolean renderDescription, String[] modes, String... selected) {
        super(name);
        Set<String> enabled = new LinkedHashSet<>();
        if (selected != null) Collections.addAll(enabled, selected);
        this.settings = new ArrayList<>();
        if (modes != null) {
            for (String mode : modes) this.settings.add(new BooleanSetting(mode, containsIgnoreCase(enabled, mode)));
        }
        hookChildren();
    }

    private void hookChildren() {
        for (BooleanSetting child : settings)
            child.addListener((setting, oldValue, newValue) -> changed(oldValue, newValue));
    }

    @Override
    public void reset() {
        for (BooleanSetting setting : settings) setting.reset();
    }

    private static boolean containsIgnoreCase(Collection<String> values, String value) {
        if (value == null || values == null) return false;
        for (String entry : values) if (entry != null && entry.equalsIgnoreCase(value)) return true;
        return false;
    }

    public List<BooleanSetting> getSettings() {
        return Collections.unmodifiableList(settings);
    }

    public List<String> getModes() {
        return settings.stream().map(BooleanSetting::getName).toList();
    }

    public List<String> getDisplayModes() {
        return getModes().stream().map(this::getDisplayMode).toList();
    }

    public String getDisplayMode(String mode) {
        return universalmod.service.language.LanguageManager.settingOption(getLocalizationOwner(), getName(), mode);
    }

    public boolean shouldRenderDescription() {
        return true;
    }

    public boolean isEnabled(String name) {
        return isSelected(name);
    }

    public boolean isSelected(String name) {
        for (BooleanSetting s : settings) if (s.getName().equalsIgnoreCase(name)) return s.getValue();
        return false;
    }

    public void setSelected(String name, boolean selected) {
        for (BooleanSetting s : settings)
            if (s.getName().equalsIgnoreCase(name)) {
                s.setValue(selected);
                return;
            }
    }

    public int selectedCount() {
        int count = 0;
        for (BooleanSetting s : settings) if (s.getValue()) count++;
        return count;
    }

    public MultiModeSetting setVisible(Supplier<Boolean> v) {
        visible = v;
        return this;
    }

    public MultiModeSetting visibleWhen(Supplier<Boolean> v) {
        return setVisible(v);
    }

    @Override
    public String getValueAsString() {
        return settings.stream().filter(BooleanSetting::getValue).map(Setting::getName).reduce((a, b) -> a + ", " + b).orElse("None");
    }

    @Override
    public void setValueFromString(String value) {
        Set<String> selected = new LinkedHashSet<>();
        if (value != null && !value.equalsIgnoreCase("None")) {
            for (String part : value.split(",")) selected.add(part.trim());
        }
        for (BooleanSetting setting : settings) setting.setValue(containsIgnoreCase(selected, setting.getName()));
    }
}
