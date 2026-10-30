package universalmod.setting;

import universalmod.setting.Setting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class ModeSetting extends Setting {
    @FunctionalInterface
    public interface Listener {
        void onChanged(ModeSetting setting, String oldValue, String newValue);
    }

    private String value;
    private final String def;
    private final List<String> modes;
    private final List<Listener> listeners = new ArrayList<>();

    public ModeSetting(String name, String defaultValue, String... modes) {
        super(name);
        this.value = this.def = defaultValue;
        this.modes = Collections.unmodifiableList(Arrays.asList(modes));
    }

    public ModeSetting(String name, String description, boolean renderDescription, String defaultValue, String... modes) {
        this(name, defaultValue, modes);
    }

    public ModeSetting(String name, String description, boolean renderDescription, boolean translateModes, String defaultValue, String... modes) {
        this(name, defaultValue, modes);
    }

    public String getValue() {
        return value;
    }

    public boolean is(String mode) {
        return value != null && mode != null && value.equalsIgnoreCase(mode);
    }

    public boolean isSelected(String mode) {
        return is(mode);
    }

    public String getDisplayValue() {
        return universalmod.service.language.LanguageManager.settingOption(getLocalizationOwner(), getName(), value);
    }

    public List<String> getDisplayModes() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String mode : modes) out.add(universalmod.service.language.LanguageManager.settingOption(getLocalizationOwner(), getName(), mode));
        return java.util.Collections.unmodifiableList(out);
    }

    public String getDisplayMode(String mode) {
        return universalmod.service.language.LanguageManager.settingOption(getLocalizationOwner(), getName(), mode);
    }

    public boolean shouldRenderDescription() {
        return true;
    }

    public List<String> getModes() {
        return modes;
    }

    public int getIndex() {
        return modes.indexOf(value);
    }

    public void setIndex(int index) {
        if (index >= 0 && index < modes.size()) setValueInternal(modes.get(index));
    }

    public void setValue(String newValue) {
        if (newValue != null && modes.contains(newValue)) setValueInternal(newValue);
    }

    public void cycle() {
        if (!modes.isEmpty()) setIndex((Math.max(0, getIndex()) + 1) % modes.size());
    }

    public void reset() {
        setValueInternal(def);
    }

    public ModeSetting addListener(Listener listener) {
        if (listener != null) listeners.add(listener);
        return this;
    }

    private void setValueInternal(String newValue) {
        if (newValue == null || newValue.equals(value)) return;
        String old = value;
        value = newValue;
        changed(old, newValue);
        for (Listener listener : List.copyOf(listeners)) {
            try {
                listener.onChanged(this, old, newValue);
            } catch (Throwable ignored) {
            }
        }
    }

    public ModeSetting setVisible(Supplier<Boolean> supplier) {
        visible = supplier;
        return this;
    }

    public ModeSetting visibleWhen(Supplier<Boolean> supplier) {
        return setVisible(supplier);
    }

    public String getValueAsString() {
        return value;
    }

    public void setValueFromString(String value) {
        setValue(value);
    }
}
