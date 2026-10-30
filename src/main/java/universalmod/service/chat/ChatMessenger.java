package universalmod.service.chat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public final class ChatMessenger {
    private ChatMessenger() {
    }

    public static void sendArrow(String message) {
        sendArrow(Text.literal(message == null ? "" : message));
    }

    public static void sendArrow(Text message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("UniversalMod -> ").append(message == null ? Text.empty() : message), false);
        }
    }

    public static void sendBracketed(String message) {
        sendBracketed(Text.literal(message == null ? "" : message));
    }

    public static void sendBracketed(Text message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && message != null) {
            client.player.sendMessage(Text.literal("[UniversalMod] ").append(message), false);
        }
    }
}
