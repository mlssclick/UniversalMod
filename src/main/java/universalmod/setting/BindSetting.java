package universalmod.setting;

import com.google.gson.JsonParser;
import universalmod.setting.bind.KeyBind;
import java.util.function.Supplier;

public class BindSetting extends Setting {
    private KeyBind value;
    private final KeyBind defaultValue;

    public BindSetting(String name, Integer defaultValue) {
        this(name, "", KeyBind.legacy(defaultValue == null ? -1 : defaultValue));
    }
    public BindSetting(String name, String description, KeyBind defaultValue) {
        super(name);
        this.defaultValue = defaultValue == null ? KeyBind.NONE : defaultValue;
        value = this.defaultValue;
        setDescription(description);
    }
    public KeyBind getValue() { return value; }
    public KeyBind getDefaultValue() { return defaultValue; }
    public void setValue(KeyBind next) {
        KeyBind normalized = next == null ? KeyBind.NONE : next;
        if (value.equals(normalized)) return;
        value = normalized; fireChanged();
    }
    public void setValue(Integer legacy) { setValue(KeyBind.legacy(legacy == null ? -1 : legacy)); }
    @Override public void reset() { setValue(defaultValue); }
    @Override public String getValueAsString() { return value.toJson().toString(); }
    @Override public void setValueFromString(String encoded) {
        try { setValue(KeyBind.fromJson(JsonParser.parseString(encoded))); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("Invalid binding for " + getName(), invalid); }
    }
    public BindSetting setVisible(Supplier<Boolean> predicate) { visible = predicate; return this; }
}
