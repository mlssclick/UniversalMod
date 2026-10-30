package universalmod.feature.cooldown;

import net.minecraft.item.Item;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CooldownStateStorage {
    private static final Map<Item, Integer> DURATIONS = new ConcurrentHashMap<>();

    private CooldownStateStorage() {
    }

    public static void setDuration(Item item, int duration) {
        if (item != null && duration > 0) DURATIONS.put(item, duration);
    }

    public static void remove(Item item) {
        if (item != null) DURATIONS.remove(item);
    }

    public static int getDuration(Item item) {
        Integer v = item == null ? null : DURATIONS.get(item);
        return v == null ? 0 : v;
    }
}
