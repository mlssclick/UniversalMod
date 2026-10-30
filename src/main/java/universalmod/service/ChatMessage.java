package universalmod.service;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import universalmod.util.TextHelper;

public final class ChatMessage {
    private ChatMessage() {
    }

    public static MutableComponent brandmessage() {
        return (MutableComponent) TextHelper.applyPredefinedGradient("UniversalMod", "black_light_purple", true);
    }

    public static void brandmessage(String message) {
        brandmessage(Component.literal(message == null ? "" : message));
    }

    public static void brandmessage(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            Component prefix = TextHelper.applyPredefinedGradient("UniversalMod -> ", "black_light_purple", true);
            mc.player.displayClientMessage(prefix.copy().append(message == null ? Component.empty() : message), false);
        }
    }

    public static void trackerMessage(Component message) {
        brandmessage(message);
    }
    public static MutableComponent plainBrand() {
        return Component.literal("[UniversalMod] ");
    }

    public static void plainBrand(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && message != null) {
            mc.player.displayClientMessage(plainBrand().append(message), false);
        }
    }

}
