package universalmod.module.utility;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;
import universalmod.feature.totem.TotemCounterTracker;

@ModuleInformation(
        moduleName = "Totem Counter",
        moduleCategory = ModuleCategory.UTILS
)
public final class TotemCounter extends Module {
    private static final TotemCounterTracker TRACKER = new TotemCounterTracker();
    private static TotemCounter instance;

    public TotemCounter() {
        instance = this;
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }

    public static void recordPop(PlayerEntity player) {
        if (isActive() && player != null) {
            TRACKER.recordPop(player);
        }
    }

    public static int getPops(PlayerEntity player) {
        return isActive() && player != null ? TRACKER.getPops(player) : 0;
    }

    public static boolean hasPops(PlayerEntity player) {
        return isActive() && player != null && TRACKER.hasPops(player);
    }

    public static void remove(PlayerEntity player) {
        TRACKER.remove(player);
    }

    public static void clearAll() {
        TRACKER.clear();
    }

    public static int getPopColor(int pops) {
        return switch (pops) {
            case 1, 2 -> 0xFF55FF55;
            case 3, 4 -> 0xFF00AA00;
            case 5, 6 -> 0xFFFFFF55;
            case 7, 8 -> 0xFFFFAA00;
            default -> 0xFFFF5555;
        };
    }

    public static Text appendCounter(PlayerEntity player, Text text) {
        if (!hasPops(player) || text == null) {
            return text;
        }

        int pops = TRACKER.getPops(player);
        MutableText label = text.copy().append(" ");
        label.append(Text.literal("| ").setStyle(Style.EMPTY.withColor(0xFFAAAAAA)));
        label.append(Text.literal("-" + pops).setStyle(Style.EMPTY.withColor(getPopColor(pops))));
        return label;
    }

    @Override
    public void onTick(net.minecraft.client.MinecraftClient client) {
        TRACKER.clearInvalid(client);
    }

    @Override
    public void onDisable() {
        TRACKER.clear();
        super.onDisable();
    }
}
