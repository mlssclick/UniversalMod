package universalmod.feature.inventory;

import net.minecraft.screen.slot.Slot;
import universalmod.integration.minecraft.IMinecraft;

import java.util.List;
import java.util.stream.Stream;

public final class InventoryTask implements IMinecraft {
    private InventoryTask() {
    }

    public static Stream<Slot> slots() {
        if (mc.player == null) {
            return Stream.empty();
        }
        return mc.player.currentScreenHandler.slots.stream();
    }

    public static int getMenuSlotId(Slot slot) {
        if (slot == null || mc.player == null || mc.player.currentScreenHandler == null) {
            return -1;
        }

        List<Slot> slots = mc.player.currentScreenHandler.slots;
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i) == slot) {
                return i;
            }
        }

        return slot.id;
    }

}
