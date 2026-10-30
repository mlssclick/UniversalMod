package universalmod.setting;

import universalmod.setting.Setting;

import java.util.function.Supplier;

public final class ButtonSetting extends Setting {
    private Runnable action;

    public ButtonSetting(String n) {
        super(n);
    }

    public ButtonSetting(String n, String description) {
        super(n, description);
    }

    public ButtonSetting(String n, Runnable r) {
        super(n);
        action = r;
    }

    public ButtonSetting(String n, String description, Runnable r) {
        super(n, description);
        action = r;
    }

    public void press() {
        if (action != null) action.run();
    }

    public ButtonSetting setAction(Runnable r) {
        action = r;
        return this;
    }

    @Override
    public ButtonSetting setVisible(Supplier<Boolean> v) {
        visible = v;
        return this;
    }

    public String getValueAsString() {
        return "";
    }

    public void setValueFromString(String v) {
    }
}
