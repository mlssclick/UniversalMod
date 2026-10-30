package universalmod.setting;

import universalmod.setting.Setting;

import java.util.function.Supplier;

public class StringSetting extends Setting {
    private final String defaultValue;
    private final int maxLength;
    private String value;

    public StringSetting(String name, String defaultValue, int maxLength) {
        super(name);
        this.maxLength = Math.max(1, maxLength);
        this.defaultValue = normalize(defaultValue);
        this.value = this.defaultValue;
    }

    public String getValue() {
        return value;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setValue(String value) {
        String normalized = normalize(value);
        if (this.value.equals(normalized)) return;
        this.value = normalized;
        fireChanged();
    }

    public int getMaxLength() {
        return maxLength;
    }

    @Override
    public void reset() {
        setValue(defaultValue);
    }

    @Override
    public String getValueAsString() {
        return value;
    }

    @Override
    public void setValueFromString(String value) {
        setValue(value);
    }

    public StringSetting setVisible(Supplier<Boolean> visible) {
        this.visible = visible;
        return this;
    }

    private String normalize(String value) {
        String safe = value == null ? "" : value;
        return safe.length() > maxLength ? safe.substring(0, maxLength) : safe;
    }
}
