package universalmod.event.type;

import net.minecraft.client.gui.DrawContext;
import universalmod.event.Event;

public record DrawEvent(DrawContext graphics, float partialTicks, Layer layer) implements Event {
    public enum Layer {
        GAME, SCREEN_BACKGROUND, CHAT_OVERLAY
    }
}
