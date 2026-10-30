package universalmod.hud.registry;

import universalmod.setting.BooleanSetting;
import universalmod.setting.ModeListSetting;
import java.util.ArrayList;
import java.util.List;

public final class HudElementsSetting extends ModeListSetting {
    public HudElementsSetting(String name) {
        super(name, settingsForIds());
    }

    private static BooleanSetting[] settingsForIds() {
        HudElementId[] ids = HudElementId.values();
        BooleanSetting[] options = new BooleanSetting[ids.length];
        for (int i = 0; i < ids.length; i++) {
            options[i] = new BooleanSetting(ids[i].optionName(), ids[i].selectedByDefault());
        }
        return options;
    }

    public boolean isSelected(HudElementId id) {
        return id != null && isEnabled(id.optionName());
    }

    public void setSelected(HudElementId id, boolean value) {
        if (id != null) super.setSelected(id.optionName(), value);
    }

    public List<String> persistentIds() {
        List<String> selected = new ArrayList<>();
        for (HudElementId id : HudElementId.values()) {
            if (isSelected(id)) selected.add(id.persistentId());
        }
        return List.copyOf(selected);
    }

    @Override
    public String getValueAsString() {
        return String.join(", ", persistentIds());
    }

    @Override
    public void setValueFromString(String encoded) {
        if (encoded == null) return;
        java.util.EnumSet<HudElementId> selected = java.util.EnumSet.noneOf(HudElementId.class);
        for (String token : encoded.split(",\\s*")) {
            HudElementId.fromPersistentOrLegacy(token).ifPresent(selected::add);
        }
        for (HudElementId id : HudElementId.values()) {
            setSelected(id, selected.contains(id));
        }
    }
}
