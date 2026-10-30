package universalmod.setting;

import universalmod.setting.Setting;

import java.util.function.Supplier;

public class ColorSetting extends Setting {
    private int value;
    private final int def;

    public ColorSetting(String n, Integer v) {
        super(n);
        value = def = v == null ? 0 : v;
    }

    public ColorSetting(String n, String description, Integer v) {
        super(n, description);
        value = def = v == null ? 0 : v;
    }

    public ColorSetting(String n, String description, Integer v, String configKey) {
        super(n, description, true, configKey);
        value = def = v == null ? 0 : v;
    }

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer v) {
        int nv = v == null ? 0 : v, old = value;
        value = nv;
        changed(old, nv);
    }

    @Override
    public void reset() {
        setValue(def);
    }

    @Override
    public ColorSetting setVisible(Supplier<Boolean> v) {
        visible = v;
        return this;
    }

    @Override
    public ColorSetting visibleWhen(Supplier<Boolean> v) {
        return setVisible(v);
    }

    public String getValueAsString() {
        return Integer.toString(value);
    }

    public void setValueFromString(String v) {
        try {
            setValue(Integer.parseInt(v));
        } catch (Exception ignored) {
        }
    }
}
