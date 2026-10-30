package universalmod.setting;

import universalmod.setting.Setting;

import java.util.function.Supplier;

public class StringSetting extends Setting {
    private final String def;
    private final int max;
    private String value;

    public StringSetting(String n, String d, int m) {
        super(n);
        max = Math.max(1, m);
        def = norm(d);
        value = def;
    }

    public StringSetting(String n, String description, String d, int m) {
        super(n, description);
        max = Math.max(1, m);
        def = norm(d);
        value = def;
    }

    private String norm(String s) {
        s = s == null ? "" : s;
        return s.length() > max ? s.substring(0, max) : s;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String v) {
        String nv = norm(v), old = value;
        value = nv;
        changed(old, nv);
    }

    public int getMaxLength() {
        return max;
    }

    @Override
    public void reset() {
        setValue(def);
    }

    @Override
    public StringSetting setVisible(Supplier<Boolean> v) {
        visible = v;
        return this;
    }

    @Override
    public StringSetting visibleWhen(Supplier<Boolean> v) {
        return setVisible(v);
    }

    public String getValueAsString() {
        return value;
    }

    public void setValueFromString(String v) {
        setValue(v);
    }
}
