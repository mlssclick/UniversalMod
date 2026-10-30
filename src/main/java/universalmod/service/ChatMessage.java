package universalmod.service;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import universalmod.util.TextHelper;

public final class ChatMessage {
    private ChatMessage() {
    }

    public static MutableText brandmessage() {
        return (MutableText) TextHelper.applyPredefinedGradient("UniversalMod", "black_light_purple", true);
    }

    public static void brandmessage(String message) {
        brandmessage(Text.literal(message == null ? "" : message));
    }

    public static void brandmessage(Text message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            Text prefix = TextHelper.applyPredefinedGradient("UniversalMod -> ", "black_light_purple", true);
            mc.player.sendMessage(prefix.copy().append(message == null ? Text.empty() : message), false);
        }
    }

    public static void trackerMessage(Text message) {
        brandmessage(message);
    }
    public static MutableText plainBrand() {
        return Text.literal("[UniversalMod] ");
    }

    public static void plainBrand(Text message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && message != null) {
            mc.player.sendMessage(plainBrand().append(message), false);
        }
    }

}
