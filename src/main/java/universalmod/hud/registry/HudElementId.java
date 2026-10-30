package universalmod.hud.registry;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum HudElementId {
    MUSIC_PLAYER("music_player", "Music Player", true),
    TARGET_HUD("target_hud", "Target HUD", true),
    POTIONS("potions", "Potions", true),
    INVENTORY("inventory", "Inventory", true),
    NOTIFICATIONS("notifications", "Notifications", true),
    ARMOR("armor", "Armor", true),
    COOLDOWNS("cooldowns", "Cooldowns", true),
    KEYSTROKES("keystrokes", "Keystrokes", false),
    TNT_TIMER("tnt_timer", "TNT Timer", false),
    EVENT("event", "Event", false),
    BOSSBAR("bossbar", "Bossbar", false);

    private final String persistentId;
    private final String optionName;
    private final boolean selectedByDefault;

    HudElementId(String persistentId, String optionName, boolean selectedByDefault) {
        this.persistentId = persistentId;
        this.optionName = optionName;
        this.selectedByDefault = selectedByDefault;
    }

    public String value() {
        return persistentId;
    }

    public String persistentId() {
        return persistentId;
    }

    public String optionName() {
        return optionName;
    }

    public String translationKey() {
        return "hud.element." + persistentId;
    }

    public boolean selectedByDefault() {
        return selectedByDefault;
    }

    public static Optional<HudElementId> fromPersistentOrLegacy(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (HudElementId id : values()) {
            if (id.persistentId.equalsIgnoreCase(normalized)
                    || id.optionName.equalsIgnoreCase(raw.trim())
                    || id.name().equalsIgnoreCase(normalized.replace('-', '_').replace(' ', '_'))) {
                return Optional.of(id);
            }
        }
        return Optional.empty();
    }

    public static String[] optionNames() {
        return Arrays.stream(values()).map(HudElementId::optionName).toArray(String[]::new);
    }

    public static String[] defaultOptionNames() {
        return Arrays.stream(values()).filter(HudElementId::selectedByDefault)
                .map(HudElementId::optionName).toArray(String[]::new);
    }
}
