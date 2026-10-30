package universalmod.gui.ui.control;

import universalmod.render.animation.Easing;
import universalmod.render.animation.Easings;
import universalmod.render.animation.ReversibleAnimationTimeline;
public final class SwitchControl {
    private final ReversibleAnimationTimeline animation = new ReversibleAnimationTimeline();
    private boolean initialized;

    public void reset(boolean value) {
        animation.set(value ? 1.0D : 0.0D);
        initialized = true;
    }

    public float update(boolean value) {
        return update(value, 0.50D, Easings.EXPO_OUT);
    }

    public float update(boolean value, double durationSeconds, Easing easing) {
        if (!initialized) {
            reset(value);
        }
        animation.update();
        animation.run(value ? 1.0D : 0.0D, durationSeconds, easing, false);
        return progress();
    }

    public void render(float x, float y, float scale, float alpha, boolean value) {
        SmoothSwitchRenderer.render(x, y, scale, alpha, update(value));
    }

    public void renderSized(float x, float y, float width, float height, float alpha, boolean value) {
        SmoothSwitchRenderer.renderSized(x, y, width, height, alpha, update(value));
    }

    public float progress() {
        return clamp01(animation.get());
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
