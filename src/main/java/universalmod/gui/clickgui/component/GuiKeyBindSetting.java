package universalmod.gui.clickgui.component;

import universalmod.setting.Setting;
import universalmod.setting.bind.KeyBind;

 
public final class GuiKeyBindSetting extends Setting {
    private KeyBind value;
    private final KeyBind initial;
    public GuiKeyBindSetting(String name, KeyBind value) {
        super(name);
        this.initial = value == null ? KeyBind.NONE : value;
        this.value = this.initial;
    }
    public KeyBind getValue() { return value; }
    public void setValue(KeyBind next) {
        KeyBind normalized = next == null ? KeyBind.NONE : next;
        KeyBind old = value;
        value = normalized;
        changed(old, normalized);
    }
    @Override public void reset() { setValue(initial); }
    @Override public String getValueAsString() { return value.toJson().toString(); }
    @Override public void setValueFromString(String encoded) {
        try { setValue(KeyBind.fromJson(com.google.gson.JsonParser.parseString(encoded))); }
        catch (RuntimeException ignored) { }
    }
}
