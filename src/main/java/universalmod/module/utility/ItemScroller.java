package universalmod.module.utility;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;
import universalmod.event.SubscribeEvent;
import universalmod.event.type.HandledScreenEvent;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.setting.NumberSetting;
import universalmod.feature.inventory.InventoryTask;

public final class ItemScroller extends Module {
    private static ItemScroller instance;
    private final NumberSetting scrollDelay = register(new NumberSetting(
            "Scroll Delay",
            "Delay between item scroll clicks.",
            50.0,
            0.0,
            100.0,
            1.0
    ));
    private long lastActionTime = System.currentTimeMillis();

    public static void handleScroll(Slot hoveredSlot) {
        ItemScroller module = getInstance();
        Minecraft client = Minecraft.getInstance();
        if (module == null || !module.isEnabled() || hoveredSlot == null || !hoveredSlot.hasItem()
                || client.player == null || client.gameMode == null
                || !shiftPressed(client.getWindow().handle()) || !module.elapsed(module.scrollDelay.getValue().intValue())) return;
        int slotId = InventoryTask.getMenuSlotId(hoveredSlot);
        if (slotId < 0) return;
        client.gameMode.handleInventoryMouseClick(client.player.containerMenu.containerId, slotId, 0,
                ClickType.QUICK_MOVE, client.player);
        module.resetTimer();
    }

    public ItemScroller() {
        super("item_scroller", "ItemScroller", ModuleCategory.UTILS);
        instance = this;
    }

    public static ItemScroller getInstance() {
        return instance;
    }

    @SubscribeEvent
    private void onHandledScreen(HandledScreenEvent event) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gameMode == null) {
            return;
        }

        Slot hoverSlot = event.getSlotHover();
        if (hoverSlot == null || !hoverSlot.hasItem()) {
            return;
        }

        long window = client.getWindow().handle();
        if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS
                || !shiftPressed(window)
                || !elapsed(scrollDelay.getValue().intValue())) {
            return;
        }

        int slotId = InventoryTask.getMenuSlotId(hoverSlot);
        if (slotId != -1) {
            client.gameMode.handleInventoryMouseClick(
                    client.player.containerMenu.containerId,
                    slotId,
                    0,
                    ClickType.QUICK_MOVE,
                    client.player
            );
            resetTimer();
        }
    }

    private boolean elapsed(long delayMs) {
        return System.currentTimeMillis() - delayMs >= lastActionTime;
    }

    private static boolean shiftPressed(long window) {
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    private void resetTimer() {
        lastActionTime = System.currentTimeMillis();
    }
}
