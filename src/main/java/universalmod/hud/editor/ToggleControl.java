package universalmod.hud.editor;

import universalmod.gui.ui.control.SmoothSwitchRenderer;
import universalmod.render.animation.Easings;
import universalmod.render.animation.ReversibleAnimationTimeline;


public final class ToggleControl {
    private final ReversibleAnimationTimeline animation = new ReversibleAnimationTimeline();
    private boolean state;

    public void reset(boolean value) {
        state = value;
        animation.set(value ? 1.0D : 0.0D);
    }

    public void sync(boolean value) {
        state = value;
    }

    public void update(float deltaSeconds) {
        animation.update();
        animation.run(state ? 1.0D : 0.0D, 0.50D, Easings.EXPO_OUT, false);
    }

    public boolean toggle() {
        state = !state;
        return state;
    }

    public boolean state() {
        return state;
    }

    public void render(float x, float y, float alpha) {
        SmoothSwitchRenderer.renderSized(x, y, SmoothSwitchRenderer.WIDTH, SmoothSwitchRenderer.HEIGHT,
                alpha, (float) animation.get());
    }
}
