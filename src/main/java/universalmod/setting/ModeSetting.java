package universalmod.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import universalmod.setting.Setting;
import universalmod.setting.SettingType;
import universalmod.service.language.LanguageManager;

import java.util.List;

public class ModeSetting extends Setting<String> {
    private final List<String> modes;
    private final boolean renderDescription;
    private final boolean translateModes;

    public ModeSetting(String name, String description, String defaultValue, String... modes) {
        this(name, description, true, true, defaultValue, modes);
    }

    public ModeSetting(String name, String description, boolean renderDescription, String defaultValue, String... modes) {
        this(name, description, renderDescription, true, defaultValue, modes);
    }

    public ModeSetting(String name, String description, boolean renderDescription, boolean translateModes, String defaultValue, String... modes) {
        super(name, description, defaultValue, SettingType.MODE);
        this.modes = List.of(modes);
        this.renderDescription = renderDescription;
        this.translateModes = translateModes;
        setValue(defaultValue);
    }

    public List<String> getModes() {
        return modes;
    }

    public List<String> getDisplayModes() {
        return translateModes ? modes.stream().map(mode -> LanguageManager.settingOption(getLocalizationOwner(), getName(), mode)).toList() : modes;
    }

    public String getDisplayMode(String mode) {
        return translateModes ? LanguageManager.settingOption(getLocalizationOwner(), getName(), mode) : mode;
    }

    public String getDisplayValue() {
        return getDisplayMode(getValue());
    }

    public boolean shouldRenderDescription() {
        return renderDescription;
    }

    public boolean is(String mode) {
        return getValue().equals(normalize(mode));
    }

    public boolean isSelected(String mode) {
        return is(mode);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(getValue());
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive()) {
            setValue(element.getAsString());
        }
    }

    @Override
    protected String normalize(String value) {
        if (value == null || modes.isEmpty()) {
            return modes.isEmpty() ? "" : modes.getFirst();
        }
        for (String mode : modes) {
            if (mode.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return modes.getFirst();
    }
}
