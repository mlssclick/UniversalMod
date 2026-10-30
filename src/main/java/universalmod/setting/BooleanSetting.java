package universalmod.setting;

import universalmod.setting.Setting;

import java.util.function.Supplier;

public class BooleanSetting extends Setting {
    private boolean value;
    private final boolean def;
    private int key = -1;

    public BooleanSetting(String n, boolean v) {
        super(n);
        value = def = v;
    }

    public BooleanSetting(String n, String description, boolean v) {
        super(n, description);
        value = def = v;
    }

    public boolean getValue() {
        return value;
    }

    public void setValue(boolean v) {
        boolean old = value;
        value = v;
        changed(old, v);
    }

    public void toggle() {
        setValue(!value);
    }

    @Override
    public void reset() {
        setValue(def);
    }

    public int getKey() {
        return key;
    }

    public void setKey(int k) {
        int old = key;
        key = k;
        changed(old, k);
    }

    @Override
    public BooleanSetting setVisible(Supplier<Boolean> v) {
        visible = v;
        return this;
    }

    @Override
    public BooleanSetting visibleWhen(Supplier<Boolean> v) {
        return setVisible(v);
    }

    public String getValueAsString() {
        return Boolean.toString(value);
    }

    public void setValueFromString(String v) {
        setValue(Boolean.parseBoolean(v));
    }
}
