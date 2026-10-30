package universalmod.setting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public abstract class Setting {
    private String localizationOwner = "";
    private final String name;
    private String description;
    private final List<SettingChangeListener> listeners = new ArrayList<>();
    private final boolean persistent;
    private final String configKey;
    public Supplier<Boolean> visible = () -> true;

    protected Setting(String name) {
        this(name, "", true, name);
    }

    protected Setting(String name, String description) {
        this(name, description, true, name);
    }

    protected Setting(String name, String description, boolean persistent, String configKey) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Setting name cannot be empty");
        this.name = name;
        this.description = description == null ? "" : description;
        this.persistent = persistent;
        this.configKey = configKey == null || configKey.isBlank() ? name : configKey;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() { return universalmod.service.language.LanguageManager.settingName(localizationOwner, name); }

    public void setLocalizationOwner(String moduleName) { this.localizationOwner = moduleName == null ? "" : moduleName; }
    public String getLocalizationOwner() { return localizationOwner; }

    public Setting setDescription(String description) {
        this.description = description == null ? "" : description;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public String getDisplayDescription() { return description; }

    public boolean isPersistent() {
        return persistent;
    }

    public String getConfigKey() {
        return configKey;
    }

    public boolean isVisible() {
        try {
            return visible == null || Boolean.TRUE.equals(visible.get());
        } catch (Throwable ignored) {
            return true;
        }
    }

    public Setting setVisible(Supplier<Boolean> value) {
        visible = value == null ? () -> true : value;
        return this;
    }

    public Setting visibleWhen(Supplier<Boolean> value) {
        return setVisible(value);
    }

    public Setting setVisible(boolean value) {
        visible = () -> value;
        return this;
    }

    public void addListener(SettingChangeListener listener) {
        if (listener != null) listeners.add(listener);
    }

    protected final void changed(Object oldValue, Object newValue) {
        if (java.util.Objects.equals(oldValue, newValue)) return;
        for (SettingChangeListener listener : List.copyOf(listeners)) {
            try {
                listener.onChanged(this, oldValue, newValue);
            } catch (Throwable throwable) {

            }
        }
    }

    public void reset() {
    }

    public abstract String getValueAsString();

    public abstract void setValueFromString(String value);
}
