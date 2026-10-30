package universalmod.hud.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import universalmod.setting.MultiModeSetting;

import java.util.LinkedHashSet;
import java.util.Set;

public final class HudElementsSetting extends MultiModeSetting {
    public HudElementsSetting(String name, String description) {
        super(name, description, HudElementId.optionNames(), HudElementId.defaultOptionNames());
    }

    @Override
    public JsonElement toJson() {
        JsonArray array = new JsonArray();
        for (String optionName : getValue()) {
            HudElementId.fromPersistentOrLegacy(optionName)
                    .ifPresent(id -> array.add(id.value()));
        }
        return array;
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return;
        }
        Set<String> optionNames = new LinkedHashSet<>();
        for (JsonElement item : element.getAsJsonArray()) {
            if (!item.isJsonPrimitive()) {
                continue;
            }
            HudElementId.fromPersistentOrLegacy(item.getAsString())
                    .ifPresent(id -> optionNames.add(id.optionName()));
        }
        setValue(optionNames);
    }

    public boolean isSelected(HudElementId id) {
        return id != null && super.isSelected(id.optionName());
    }

    public void setSelected(HudElementId id, boolean selected) {
        if (id != null) {
            super.setSelected(id.optionName(), selected);
        }
    }
}
