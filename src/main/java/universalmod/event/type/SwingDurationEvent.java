package universalmod.event.type;

import universalmod.event.CancellableEvent;

public final class SwingDurationEvent extends CancellableEvent {
    private float animation = 1.0F;

    public float getAnimation() {
        return animation;
    }

    public void setAnimation(float animation) {
        this.animation = animation;
    }
}
