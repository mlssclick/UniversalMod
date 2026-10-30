package universalmod.render.animation;

import universalmod.render.optimize.UiFrameContext;




public final class SmoothAnimation {
    private long start;
    private double duration;
    private double fromValue;
    private double toValue;
    private double value;
    private double prevValue;
    private Easing easing = Easings.EXPO_OUT;

    public SmoothAnimation run(double valueTo, double durationSeconds) {
        return run(valueTo, durationSeconds, Easings.EXPO_OUT, false);
    }

    public SmoothAnimation run(double valueTo, double durationSeconds, Easing easing) {
        return run(valueTo, durationSeconds, easing, false);
    }

    public SmoothAnimation run(double valueTo, double durationSeconds, Easing easing, boolean safe) {
        if (check(safe, valueTo)) return this;
        this.easing = easing;
        this.start = UiFrameContext.nowMillis();
        this.duration = durationSeconds * 1000.0;
        this.fromValue = this.value;
        this.toValue = valueTo;
        return this;
    }

    public boolean update() {
        this.prevValue = this.value;
        boolean alive = isAlive();
        if (alive) {
            double part = Math.min(1.0, Math.max(0.0, calculatePart()));
            this.value = fromValue + (toValue - fromValue) * easing.ease((float) part);
        } else {
            this.start = 0L;
            this.value = this.toValue;
        }
        return alive;
    }

    public boolean isAlive() {
        return !isFinished();
    }

    public boolean isFinished() {
        return calculatePart() >= 1.0;
    }

    public double calculatePart() {
        return duration <= 0.0 ? 1.0 : (UiFrameContext.nowMillis() - start) / duration;
    }

    private boolean check(boolean safe, double valueTo) {
        return safe && isAlive() && (valueTo == fromValue || valueTo == toValue || valueTo == value);
    }

    public float get() {
        return (float) value;
    }

    public float getPrev() {
        return (float) prevValue;
    }

    public double getToValue() {
        return toValue;
    }

    public void set(double value) {
        this.start = 0L;
        this.duration = 0.0;
        this.fromValue = value;
        this.toValue = value;
        this.value = value;
        this.prevValue = value;
    }
}
