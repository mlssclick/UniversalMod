package universalmod.setting;

import universalmod.setting.Setting;

import java.util.function.Supplier;

public final class ButtonSetting extends Setting {
    private final Runnable action;

    public ButtonSetting(String name, Runnable action) {
        super(name);
        this.action = action;
    }

    public ButtonSetting(String name, String description, Runnable action) {
        this(name, action);
        setDescription(description);
    }

    public void press() {
        if (action != null) action.run();
    }

    public ButtonSetting setVisible(Supplier<Boolean> visible) {
        this.visible = visible;
        return this;
    }

    @Override
    public boolean isPersistent() {
        return false;
    }

    @Override
    public String getValueAsString() {
        return "";
    }

    @Override
    public void setValueFromString(String value) {
    }
}
