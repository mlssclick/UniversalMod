package universalmod.feature.savekt;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.text.Text;

public final class SaveKtManager {
    public static final List<String> PVP_TYPES = List.of("режим боя", "пвп", "pvp");
    public static final List<String> COMMANDS_FOR_LEAVE = List.of("hub", "lobby", "рги", "дщиин");
    public static boolean openScreen;
    public static boolean bypassDisconnectHook;
    public static boolean bypassCommandHook;
    private static Consumer<String> confirmationScreenOpener;

    private SaveKtManager() { }

    public static void setConfirmationScreenOpener(Consumer<String> opener) {
        confirmationScreenOpener = opener;
    }

    public static void disconnect() {
        SaveKtLeaveService.getInstance().resetAll();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        bypassDisconnectHook = true;
        try {
            ClientPlayNetworkHandler handler = client.getNetworkHandler();
            if (handler != null) {
                ClientConnection connection = handler.getConnection();
                if (connection != null && !connection.isLocal()) {
                    connection.disconnect(Text.translatable("disconnect.quitting"));
                }
            }
            client.disconnect(new TitleScreen());
        } finally {
            bypassDisconnectHook = false;
        }
    }

    public static void disc() { disconnect(); }

    public static int getPing() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.getNetworkHandler() == null) return 150;
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        return Math.max(entry == null ? -1 : entry.getLatency(), 150);
    }

    public static boolean isPvp() { return SaveKtLeaveService.getInstance().isPvp(); }
    public static boolean isRecentAttack() { return SaveKtLeaveService.getInstance().isRecentAttack(); }
    public static void reset() { SaveKtLeaveService.getInstance().resetTransient(); }
    public static void resetAll() { SaveKtLeaveService.getInstance().resetAll(); }
    public static void resetTransientState() { openScreen = false; }

    public static void sendConfirmScreenIfNeeded(String command) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        if (isPvp()) {
            Consumer<String> opener = confirmationScreenOpener;
            if (opener == null || openScreen) return;
            openScreen = true;
            opener.accept(command);
        } else if (command == null) {
            disconnect();
        } else {
            sendFinalCommand(command);
        }
    }

    public static void checkLeaveCommand(String command) {
        if (!openScreen) sendConfirmScreenIfNeeded(command);
    }

    public static void sendFinalCommand(String command) {
        if (command == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        client.execute(() -> {
            ClientPlayNetworkHandler handler = client.getNetworkHandler();
            if (handler == null) return;
            bypassCommandHook = true;
            try {
                handler.sendChatCommand(command);
            } finally {
                bypassCommandHook = false;
            }
        });
    }
}
