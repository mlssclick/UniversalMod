package universalmod.event.type;

import net.minecraft.client.util.math.MatrixStack;
import universalmod.event.Event;

public record WorldRenderEvent(MatrixStack poseStack, float tickDelta) implements Event {
    public MatrixStack getStack() {
        return poseStack;
    }

    public float getTickDelta() {
        return tickDelta;
    }

    public float getPartialTicks() {
        return tickDelta;
    }
}
