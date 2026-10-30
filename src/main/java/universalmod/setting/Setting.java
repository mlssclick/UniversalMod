package universalmod.setting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public abstract class Setting implements ISetting {
    private String localizationOwner = "";
    private final String name;
    private String description = "";
    private String configKey;
    public Supplier<Boolean> visible = () -> true;
    private final List<Runnable> changeListeners = new ArrayList<>();

    public Setting(String name) {
        this.name = name;
        this.configKey = name;
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() { return description; }
    public Setting setDescription(String description) {
        this.description = description == null ? "" : description;
        return this;
    }

    public String getDisplayName() { return universalmod.service.language.LanguageManager.settingName(localizationOwner, name); }

    public void setLocalizationOwner(String moduleName) { this.localizationOwner = moduleName == null ? "" : moduleName; }
    public String getLocalizationOwner() { return localizationOwner; }

    public String getConfigKey() {
        return this.configKey;
    }

    protected final void setConfigKey(String configKey) {
        if (configKey != null && !configKey.isBlank()) this.configKey = configKey;
    }

    public boolean isVisible() {
        try {
            return visible == null || Boolean.TRUE.equals(visible.get());
        } catch (Throwable ignored) {
            return true;
        }
    }

    public Setting visibleWhen(Supplier<Boolean> predicate) {
        visible = predicate;
        return this;
    }

    public Setting addChangeListener(Runnable listener) {
        if (listener != null) {
            changeListeners.add(listener);
        }
        return this;
    }

    protected final void fireChanged() {
        for (Runnable listener : List.copyOf(changeListeners)) {
            listener.run();
        }
    }

    public void reset() {
    }

    public boolean isPersistent() {
        return true;
    }

    public abstract String getValueAsString();

    public abstract void setValueFromString(String value);
}
