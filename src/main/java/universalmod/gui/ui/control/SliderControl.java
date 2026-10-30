package universalmod.gui.ui.control;

import universalmod.render.animation.Easings;
import universalmod.render.animation.ReversibleAnimationTimeline;
import universalmod.setting.SliderSetting;
import java.util.function.DoubleConsumer;
public final class SliderControl {
    private final ReversibleAnimationTimeline progressAnimation = new ReversibleAnimationTimeline();
    private final ReversibleAnimationTimeline pressAnimation = new ReversibleAnimationTimeline();

    private boolean initialized;
    private boolean dragging;
    private float captureTrackX;
    private float captureTrackWidth;
    private float captureScale = 1.0F;

    public void reset(float progress) {
        float p = clamp01(progress);
        progressAnimation.set(p);
        pressAnimation.set(0.0D);
        initialized = true;
        dragging = false;
    }

    public SmoothFloatSliderRenderer.Layout render(float x, float trackY, float width, float scale,
                                                    float alpha, float targetProgress) {
        float target = clamp01(targetProgress);
        ensureInitialized(target);

        progressAnimation.update();
        if (dragging) {
            progressAnimation.set(target);
        } else {
            progressAnimation.run(target, 0.25D, Easings.EXPO_OUT, false);
        }

        pressAnimation.update();
        pressAnimation.run(dragging ? 1.0D : 0.0D, 0.15D, Easings.QUAD_OUT, false);

        return SmoothFloatSliderRenderer.render(
                x, trackY, width, scale, alpha,
                clamp01(progressAnimation.get()), clamp01(pressAnimation.get())
        );
    }

    public boolean press(double mouseX, double mouseY, float trackX, float trackY,
                         float trackWidth, float scale, DoubleConsumer normalizedSink) {
        SmoothFloatSliderRenderer.Layout layout = SmoothFloatSliderRenderer.layout(trackX, trackY, trackWidth, scale);
        if (!layout.hit().contains(mouseX, mouseY)) {
            return false;
        }
        captureTrackX = trackX;
        captureTrackWidth = trackWidth;
        captureScale = Math.max(0.0001F, scale);
        dragging = true;
        apply(mouseX, normalizedSink);
        return true;
    }

    public boolean drag(double mouseX, DoubleConsumer normalizedSink) {
        if (!dragging) {
            return false;
        }
        apply(mouseX, normalizedSink);
        return true;
    }

    public boolean release() {
        if (!dragging) {
            return false;
        }
        dragging = false;
        return true;
    }

    public void cancel() {
        dragging = false;
    }

    public boolean dragging() {
        return dragging;
    }

    public float animatedProgress() {
        return clamp01(progressAnimation.get());
    }

    public static float progress(SliderSetting setting) {
        if (setting == null) {
            return 0.0F;
        }
        double range = setting.getMax() - setting.getMin();
        if (Math.abs(range) <= 1.0E-9D) {
            return 0.0F;
        }
        return clamp01((float) ((setting.getValue() - setting.getMin()) / range));
    }

    public static void apply(SliderSetting setting, double normalized) {
        if (setting == null) {
            return;
        }
        double p = Math.max(0.0D, Math.min(1.0D, normalized));
        setting.setValue(setting.getMin() + (setting.getMax() - setting.getMin()) * p);
    }

    private void ensureInitialized(float target) {
        if (!initialized) {
            reset(target);
        }
    }

    private void apply(double mouseX, DoubleConsumer normalizedSink) {
        float knob = SmoothFloatSliderRenderer.BASE_KNOB_SIZE * captureScale;
        float usable = Math.max(captureTrackWidth - knob, 1.0E-4F);
        float p = clamp01((float) ((mouseX - captureTrackX - knob * 0.5F) / usable));
        progressAnimation.set(p);
        if (normalizedSink != null) {
            normalizedSink.accept(p);
        }
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
