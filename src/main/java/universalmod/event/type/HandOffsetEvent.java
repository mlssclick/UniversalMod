package universalmod.event.type;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import universalmod.event.CancellableEvent;

public final class HandOffsetEvent extends CancellableEvent {
    private final MatrixStack matrices;
    private final ItemStack stack;
    private final Hand hand;
    private float scale = 1.0F;

    public HandOffsetEvent(MatrixStack matrices, ItemStack stack, Hand hand) {
        this.matrices = matrices;
        this.stack = stack;
        this.hand = hand;
    }

    public MatrixStack getMatrices() {
        return matrices;
    }

    public ItemStack getStack() {
        return stack;
    }

    public Hand getHand() {
        return hand;
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }
}
