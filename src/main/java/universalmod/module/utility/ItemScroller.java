package universalmod.module.utility;

import universalmod.event.SubscribeEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;
import universalmod.event.type.HandledScreenEvent;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;
import universalmod.setting.NumberSetting;

@ModuleInformation(
        moduleName = "ItemScroller",
        moduleCategory = ModuleCategory.UTILS
)
public final class ItemScroller extends Module {
    private static ItemScroller instance;
    public final NumberSetting scrollDelay = new NumberSetting(
            "Scroll Delay",
            50.0,
            0.0,
            100.0,
            1.0
    );

    private long lastActionTime = System.currentTimeMillis();

    public ItemScroller() {
        super();
        scrollDelay.setDescription("Delay between item scroll clicks.");
        instance = this;
    }

    public static ItemScroller getInstance() {
        return instance;
    }

    public static void handleScroll(Slot hoveredSlot) {
        ItemScroller module = getInstance();
        MinecraftClient client = MinecraftClient.getInstance();
        if (module == null || !module.isEnabled() || hoveredSlot == null || !hoveredSlot.hasStack()
                || client.player == null || client.interactionManager == null
                || !shiftPressed(client.getWindow().getHandle()) || !module.elapsed(module.scrollDelay.getIntValue())) return;
        int slotId = module.getMenuSlotId(hoveredSlot);
        if (slotId < 0) return;
        client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, slotId, 0,
                SlotActionType.QUICK_MOVE, client.player);
        module.resetTimer();
    }

    @SubscribeEvent
    private void onHandledScreen(HandledScreenEvent event) {
        if (mc.player == null || mc.interactionManager == null) {
            return;
        }

        Slot hoveredSlot = event.getSlotHover();
        if (hoveredSlot == null || !hoveredSlot.hasStack()) {
            return;
        }

        long window = mc.getWindow().getHandle();
        if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS
                || !shiftPressed(window)
                || !elapsed(scrollDelay.getIntValue())) {
            return;
        }

        int slotId = getMenuSlotId(hoveredSlot);
        if (slotId < 0) {
            return;
        }

        mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                slotId,
                0,
                SlotActionType.QUICK_MOVE,
                mc.player
        );
        resetTimer();
    }

    private int getMenuSlotId(Slot target) {
        if (mc.player == null || mc.player.currentScreenHandler == null || target == null) {
            return -1;
        }

        for (int index = 0; index < mc.player.currentScreenHandler.slots.size(); index++) {
            if (mc.player.currentScreenHandler.slots.get(index) == target) {
                return index;
            }
        }
        return target.id;
    }

    private boolean elapsed(long delayMs) {
        return System.currentTimeMillis() - lastActionTime >= delayMs;
    }

    private static boolean shiftPressed(long window) {
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    private void resetTimer() {
        lastActionTime = System.currentTimeMillis();
    }
}
