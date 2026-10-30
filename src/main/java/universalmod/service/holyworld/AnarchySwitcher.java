package universalmod.service.holyworld;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.slot.SlotActionType;




public final class AnarchySwitcher {
    private static final String TYPE_KEY = "advancedserverselecter:server-type";
    private static final String SERVER_KEY = "advancedserverselecter:server";
    private static final int TIMEOUT_TICKS = 200;
    private static final List<String> RANGE_KEYS = List.of("Solo", "Duo", "Trio", "Clan");
    private static final AnarchySwitcher INSTANCE = new AnarchySwitcher();

    private State state = State.IDLE;
    private String categoryKey;
    private String serverKey;
    private ClientWorld previousWorld;
    private int elapsedTicks;

    private AnarchySwitcher() {
    }

    public static AnarchySwitcher getInstance() {
        return INSTANCE;
    }

    public boolean isBusy() {
        return state != State.IDLE;
    }

    public void start(int number) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (isBusy()) {
            return;
        }

        String resolvedCategory = categoryFor(number);
        if (resolvedCategory == null || client.player == null || client.getNetworkHandler() == null) {
            return;
        }

        categoryKey = resolvedCategory;
        serverKey = number == 1 ? "lanarchy" : "lanarchy" + number;
        previousWorld = client.world;
        elapsedTicks = 0;
        state = State.WAITING_HUB_WORLD;
        client.getNetworkHandler().sendChatCommand("hub");
    }

    public void handleServerMessage(String text) {
        if (state != State.WAITING_HUB_WORLD || text == null
                || !text.toLowerCase(Locale.ROOT).contains("уже подключен")) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendChatCommand("lite");
            state = State.WAITING_TYPE_MENU;
            elapsedTicks = 0;
        }
    }

    public void tick(MinecraftClient client) {
        if (state == State.IDLE || client == null) {
            return;
        }
        if (client.player == null || client.getNetworkHandler() == null) {
            if (state != State.WAITING_HUB_WORLD) {
                reset();
            }
            return;
        }

        elapsedTicks++;
        if (elapsedTicks > TIMEOUT_TICKS) {
            reset();
            return;
        }

        switch (state) {
            case WAITING_HUB_WORLD -> waitForHub(client);
            case WAITING_TYPE_MENU -> scanMenu(client, TYPE_KEY, categoryKey, true);
            case WAITING_SERVER_MENU -> scanMenu(client, SERVER_KEY, serverKey, false);
            case IDLE -> {
            }
        }
    }

    private void waitForHub(MinecraftClient client) {
        if (client.world != null && client.world != previousWorld && client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendChatCommand("lite");
            state = State.WAITING_TYPE_MENU;
            elapsedTicks = 0;
        }
    }

    private void scanMenu(MinecraftClient client, String nbtKey, String expectedValue, boolean firstMenu) {
        if (!(client.currentScreen instanceof GenericContainerScreen screen)
                || client.interactionManager == null || client.player == null) {
            return;
        }

        var menu = screen.getScreenHandler();
        int containerSlots = Math.max(0, menu.slots.size() - 36);
        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = menu.getSlot(i).getStack();
            if (stack.isEmpty() || !expectedValue.equals(readPublicBukkitValue(stack, nbtKey))) {
                continue;
            }

            client.interactionManager.clickSlot(menu.syncId, i, 0, SlotActionType.PICKUP, client.player);
            state = firstMenu ? State.WAITING_SERVER_MENU : State.IDLE;
            elapsedTicks = 0;
            if (state == State.IDLE) {
                previousWorld = null;
            }
            return;
        }
    }

    private static String readPublicBukkitValue(ItemStack stack, String key) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            return null;
        }
        NbtCompound root = customData.copyNbt();
        if (!root.contains("PublicBukkitValues")) {
            return null;
        }
        NbtCompound values = root.getCompound("PublicBukkitValues");
        return values.getString(key);
    }

    private static String categoryFor(int number) {
        HolyWorldEventsService events = HolyWorldEventsService.getInstance();
        for (String rangeKey : RANGE_KEYS) {
            if (events.matchesAnarchyRange(rangeKey, number)) {
                return switch (rangeKey) {
                    case "Solo" -> "solo";
                    case "Duo" -> "duo";
                    case "Trio" -> "trio";
                    case "Clan" -> "clans";
                    default -> null;
                };
            }
        }
        return null;
    }

    private void reset() {
        state = State.IDLE;
        elapsedTicks = 0;
        previousWorld = null;
        categoryKey = null;
        serverKey = null;
    }

    private enum State {
        IDLE,
        WAITING_HUB_WORLD,
        WAITING_TYPE_MENU,
        WAITING_SERVER_MENU
    }
}
