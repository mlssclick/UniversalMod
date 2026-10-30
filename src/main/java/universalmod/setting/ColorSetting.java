package universalmod.setting;

import universalmod.setting.Setting;

import java.util.function.Supplier;

public class ColorSetting extends Setting {
    private int value;
    private final int defaultValue;

    public ColorSetting(String name, Integer defaultValue) {
        super(name);
        this.defaultValue = defaultValue == null ? 0xFFFFFFFF : defaultValue;
        this.value = this.defaultValue;
    }

    public ColorSetting(String name, String description, java.awt.Color defaultValue) {
        this(name, defaultValue == null ? 0xFFFFFFFF : defaultValue.getRGB());
        setDescription(description);
    }


    public ColorSetting(String name, Integer defaultValue, String configKey) {
        this(name, defaultValue);
        setConfigKey(configKey);
    }

    public Integer getValue() {
        return this.value;
    }

    public int getDefaultValue() {
        return defaultValue;
    }

    public void setValue(Integer value) {
        int normalized = value == null ? defaultValue : value;
        if (this.value == normalized) return;
        this.value = normalized;
        fireChanged();
    }

    @Override
    public void reset() {
        setValue(defaultValue);
    }

    @Override
    public String getValueAsString() {
        return Integer.toString(this.value);
    }

    @Override
    public void setValueFromString(String value) {
        try {
            setValue(Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
        }
    }

    public ColorSetting setVisible(Supplier<Boolean> visible) {
        this.visible = visible;
        return this;
    }
}
