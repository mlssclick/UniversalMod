package universalmod.event.type;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.screen.slot.Slot;
import universalmod.event.Event;

public final class HandledScreenEvent implements Event {
    private final DrawContext graphics;
    private final Slot slotHover;
    private final int imageWidth;
    private final int imageHeight;

    public HandledScreenEvent(Slot slotHover, int imageWidth, int imageHeight) {
        this(null, slotHover, imageWidth, imageHeight);
    }

    public HandledScreenEvent(DrawContext graphics, Slot slotHover, int imageWidth, int imageHeight) {
        this.graphics = graphics;
        this.slotHover = slotHover;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
    }

    public DrawContext getGraphics() {
        return graphics;
    }

    public Slot getSlotHover() {
        return slotHover;
    }

    public int getImageWidth() {
        return imageWidth;
    }

    public int getImageHeight() {
        return imageHeight;
    }
}
