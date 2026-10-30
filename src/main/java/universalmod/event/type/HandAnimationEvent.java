package universalmod.event.type;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;
import universalmod.event.CancellableEvent;

public final class HandAnimationEvent extends CancellableEvent {
    private final MatrixStack matrices;
    private final Hand hand;
    private final float swingProgress;

    public HandAnimationEvent(MatrixStack matrices, Hand hand, float swingProgress) {
        this.matrices = matrices;
        this.hand = hand;
        this.swingProgress = swingProgress;
    }

    public MatrixStack getMatrices() {
        return matrices;
    }

    public Hand getHand() {
        return hand;
    }

    public float getSwingProgress() {
        return swingProgress;
    }
}
