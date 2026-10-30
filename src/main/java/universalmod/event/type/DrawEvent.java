package universalmod.event.type;

import net.minecraft.client.gui.DrawContext;
import universalmod.event.Event;

public record DrawEvent(DrawContext graphics, float partialTicks, Layer layer) implements Event {
    public DrawEvent(DrawContext graphics, float partialTicks) {
        this(graphics, partialTicks, Layer.GAME);
    }

    public DrawContext getGraphics() {
        return graphics;
    }

    public float getPartialTicks() {
        return partialTicks;
    }

    public Layer getLayer() {
        return layer;
    }

    public boolean isScreenBackgroundLayer() {
        return layer == Layer.SCREEN_BACKGROUND;
    }

    public enum Layer {
        GAME,
        SCREEN_BACKGROUND,
        CHAT_OVERLAY
    }
}
