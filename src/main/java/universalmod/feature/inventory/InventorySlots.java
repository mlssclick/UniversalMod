package universalmod.feature.inventory;

import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.slot.Slot;

import java.util.List;
import java.util.stream.Stream;

public final class InventorySlots {
    private InventorySlots() {
    }

    public static Stream<Slot> slots() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player == null ? Stream.empty() : mc.player.currentScreenHandler.slots.stream();
    }

    public static int getMenuSlotId(Slot slot) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (slot == null || mc.player == null || mc.player.currentScreenHandler == null) return -1;
        List<Slot> slots = mc.player.currentScreenHandler.slots;
        for (int i = 0; i < slots.size(); i++) if (slots.get(i) == slot) return i;
        return slot.id;
    }
}
