package universalmod.module.utility;

import universalmod.event.SubscribeEvent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import universalmod.event.type.ClickSlotEvent;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;
import universalmod.setting.BooleanSetting;
import universalmod.setting.MultiModeSetting;
import universalmod.service.language.LanguageManager;
import universalmod.service.chat.ChatMessenger;

@ModuleInformation(
        moduleName = "LockSlot",
        moduleCategory = ModuleCategory.UTILS
)
public final class LockSlot extends Module {
    private static LockSlot instance;

    private static final String[] SLOT_MODES = {"1", "2", "3", "4", "5", "6", "7", "8", "9"};
    private final MultiModeSetting slots = new MultiModeSetting(
            "Slots", "Hotbar slots protected from item dropping.", SLOT_MODES);

    public LockSlot() {
        instance = this;
    }

    public static boolean shouldCancelCurrentSlotDrop() {
        LockSlot module = instance;
        if (module == null || !module.isEnabled() || module.mc.player == null || module.mc.currentScreen != null) {
            return false;
        }

        int selectedSlot = module.mc.player.getInventory().selectedSlot;
        ItemStack stack = module.mc.player.getInventory().getMainHandStack();
        if (stack.isEmpty() || !module.isHotbarSlotLocked(selectedSlot)) {
            return false;
        }

        if (module.mc.isOnThread()) {
            module.sendLockedMessage(selectedSlot);
        }
        return true;
    }

    @SubscribeEvent
    private void onClickSlot(ClickSlotEvent event) {
        if (!isEnabled() || event.getActionType() != SlotActionType.THROW || mc.player == null || mc.player.currentScreenHandler == null) {
            return;
        }

        int hotbarSlot = getHotbarSlotFromClick(event.getSlotId());
        if (hotbarSlot >= 0 && isHotbarSlotLocked(hotbarSlot)) {
            event.setCancelled(true);
            if (mc.isOnThread()) {
                sendLockedMessage(hotbarSlot);
            }
        }
    }

    private boolean isHotbarSlotLocked(int slot) {
        return slot >= 0 && slot < 9 && slots.isSelected(SLOT_MODES[slot]);
    }

    private int getHotbarSlotFromClick(int slotId) {
        if (mc.player == null
                || slotId < 0
                || slotId >= mc.player.currentScreenHandler.slots.size()) {
            return -1;
        }

        Slot slot = mc.player.currentScreenHandler.getSlot(slotId);
        if (slot.inventory != mc.player.getInventory()) {
            return -1;
        }

        int inventoryIndex = slot.getIndex();
        return inventoryIndex >= 0 && inventoryIndex <= 8 ? inventoryIndex : -1;
    }

    private void sendLockedMessage(int slot) {
        ChatMessenger.sendBracketed(
                Text.literal(LanguageManager.translateFormat("ui.lock_slot.blocked", slot + 1))
                        .formatted(Formatting.RED)
        );
    }
}
