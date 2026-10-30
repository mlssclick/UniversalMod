package universalmod.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import universalmod.setting.Setting;
import universalmod.setting.SettingType;

public class ButtonSetting extends Setting<Runnable> {
    public ButtonSetting(String name, String description, Runnable action) {
        super(name, description, action, SettingType.BUTTON, false);
    }

    public void press() {
        if (getValue() != null) {
            getValue().run();
        }
    }

    @Override
    public JsonElement toJson() {
        return JsonNull.INSTANCE;
    }

    @Override
    public void fromJson(JsonElement element) {
    }
}
