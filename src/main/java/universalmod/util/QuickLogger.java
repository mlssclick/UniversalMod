package universalmod.util;

import java.util.Arrays;
import java.util.stream.Stream;

import net.minecraft.util.Formatting;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;

public interface QuickLogger {
    static Text getPrefix() {
        MutableText universal = Text.literal("Universal");
        universal.setStyle(universal.getStyle().withColor(Formatting.WHITE));
        MutableText prefix = Text.literal("");
        prefix.setStyle(universal.getStyle().withColor(Formatting.DARK_GRAY));
        prefix.append(universal);
        prefix.append(" -> ");
        return prefix;
    }

    default void logDirect(String message, Object... args) {
        this.logDirect(message.formatted(args));
    }

    default void logDirect(Text... components) {
        MutableText component = Text.literal("");
        component.append(getPrefix());
        component.append(Text.literal(" "));
        Arrays.asList(components).forEach(component::append);
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(component);
        }
    }

    default void logDirect(String message, Formatting color) {
        Stream.of(message.split("\n")).forEach(line -> {
            MutableText component = Text.literal(line.replace("\t", "    "));
            component.setStyle(component.getStyle().withColor(color));
            this.logDirect(component);
        });
    }

    default void logDirect(String message) {
        this.logDirect(message, Formatting.GRAY);
    }
}
