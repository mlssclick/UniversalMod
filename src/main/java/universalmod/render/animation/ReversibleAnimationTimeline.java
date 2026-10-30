package universalmod.render.animation;

import universalmod.render.optimize.UiFrameContext;

public final class ReversibleAnimationTimeline {
    private static final double EPSILON = 1.0E-6;

    private long startMillis;
    private boolean running;
    private double durationMillis;
    private double fromValue;
    private double toValue;
    private double value;
    private double previousValue;
    private Easing easing = Easings.LINEAR;
    private boolean safe;
    private Runnable completion;

    public ReversibleAnimationTimeline run(double target, double durationSeconds) {
        return run(target, durationSeconds, Easings.LINEAR, false);
    }

    public ReversibleAnimationTimeline run(double target, double durationSeconds, Easing easing) {
        return run(target, durationSeconds, easing, false);
    }

    public ReversibleAnimationTimeline run(double target, double durationSeconds, boolean safe) {
        return run(target, durationSeconds, Easings.LINEAR, safe);
    }

    public ReversibleAnimationTimeline run(double target, double durationSeconds, Easing easing, boolean safe) {
        if (alreadyTargets(target)) {
            return this;
        }
        if (!safeCheck(safe, target)) {
            this.easing = easing;
            this.durationMillis = durationSeconds * 1000.0;
            this.startMillis = UiFrameContext.nowMillis();
            this.fromValue = currentValue();
            this.toValue = target;
            this.safe = safe;
            this.running = true;
        }
        return this;
    }

    public boolean update() {
        this.previousValue = currentValue();
        if (!this.running) {
            return false;
        }

        boolean alive = isAlive();
        if (alive) {
            this.value = interpolate(this.fromValue, this.toValue, this.easing.ease(part()));
        } else {
            this.startMillis = 0L;
            this.running = false;
            this.value = this.toValue;
            if (this.completion != null) {
                this.completion.run();
                this.completion = null;
            }
        }
        return alive;
    }

    public boolean isAlive() {
        return !isFinished();
    }

    public boolean isFinished() {
        return !this.running || part() >= 1.0;
    }

    public double part() {
        return this.running && this.durationMillis > 0.0
                ? (UiFrameContext.nowMillis() - this.startMillis) / this.durationMillis
                : 1.0;
    }

    public boolean safeCheck(boolean safe, double target) {
        return safe && (approximately(target, this.toValue) || approximately(target, currentValue()));
    }

    public ReversibleAnimationTimeline set(double value) {
        run(value, 0.0);
        update();
        this.value = value;
        this.previousValue = value;
        return this;
    }

    public ReversibleAnimationTimeline onComplete(Runnable completion) {
        this.completion = completion;
        return this;
    }

    public float get() {
        return (float) currentValue();
    }

    public float getPrevious() {
        return (float) this.previousValue;
    }

    public double getFromValue() {
        return this.fromValue;
    }

    public double getToValue() {
        return this.toValue;
    }

    public long getStartMillis() {
        return this.startMillis;
    }

    public double getDurationMillis() {
        return this.durationMillis;
    }

    public Easing getEasing() {
        return this.easing;
    }

    public boolean isSafe() {
        return this.safe;
    }

    private boolean alreadyTargets(double target) {
        return this.running ? approximately(target, this.toValue) : approximately(target, currentValue());
    }

    private double currentValue() {
        return this.value;
    }

    private static boolean approximately(double a, double b) {
        return Math.abs(a - b) <= EPSILON;
    }

    private static double interpolate(double from, double to, double part) {
        return from + (to - from) * part;
    }
}
