package universalmod.setting;

import universalmod.identity.StableId;


public record SettingId(String value) {
    public SettingId {
        value = StableId.canonical(value);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Setting id cannot be empty");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
