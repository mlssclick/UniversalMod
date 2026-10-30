package universalmod.setting;

import universalmod.setting.Setting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import universalmod.service.language.LanguageManager;

public class ModeSetting extends Setting {
    @FunctionalInterface
    public interface ChangeListener {
        void onChanged(ModeSetting setting, String oldValue, String newValue);
    }

    private String value;
    private final String defaultValue;
    private final List<String> modes;
    private final List<ChangeListener> listeners = new ArrayList<>();

    public ModeSetting(String name, String defaultValue, String... modes) {
        super(name);
        this.value = defaultValue;
        this.defaultValue = defaultValue;
        this.modes = Arrays.asList(modes);
    }

    public void reset() {
        setValue(defaultValue);
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        if (!modes.contains(value) || Objects.equals(this.value, value)) {
            return;
        }

        String oldValue = this.value;
        this.value = value;
        for (ChangeListener listener : List.copyOf(listeners)) {
            listener.onChanged(this, oldValue, value);
        }
        fireChanged();
    }

    public boolean is(String equalsIgnoreCase) {
        return value.equalsIgnoreCase(equalsIgnoreCase);
    }

    public List<String> getModes() {
        return modes;
    }


    public String getDisplayMode(String mode) {
        return LanguageManager.settingOption(getLocalizationOwner(), getName(), mode);
    }

    public String getDisplayValue() {
        return getDisplayMode(getValue());
    }

    public List<String> getDisplayModes() {
        return modes.stream().map(this::getDisplayMode).toList();
    }

    public boolean isSelected(String mode) {
        return is(mode);
    }

    public int getIndex() {
        return modes.indexOf(value);
    }

    public void setIndex(int index) {
        if (index >= 0 && index < modes.size()) {
            setValue(modes.get(index));
        }
    }

    public void cycle() {
        int nextIndex = (getIndex() + 1) % modes.size();
        setValue(modes.get(nextIndex));
    }

    public ModeSetting addListener(ChangeListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
        return this;
    }

    @Override
    public String getValueAsString() {
        return value;
    }

    @Override
    public void setValueFromString(String value) {
        setValue(value);
    }

    public ModeSetting setVisible(Supplier<Boolean> visible) {
        this.visible = visible;
        return this;
    }
}
