package universalmod.render.animation;

import universalmod.render.optimize.UiFrameContext;
import universalmod.render.animation.Easing;

public class Animation {
    private long duration;
    private float value;
    private Easing easing;
    private long startTime;
    private float startValue;
    private float targetValue;
    private boolean done;
    private boolean direction;

    public Animation(long duration, float initialValue, Easing easing) {
        this.duration = duration;
        this.easing = easing;
        this.value = initialValue;
        this.startValue = initialValue;
        this.targetValue = initialValue;
        this.done = true;
    }

    public Animation(Easing easing, long duration) {
        this(duration, 0.0F, easing);
    }

    public void run(boolean bool) {
        this.run(bool ? 1.0F : 0.0F);
    }

    public float run(float newValue) {
        long currentTime = UiFrameContext.nowMillis();
        if (newValue != this.targetValue) {
            this.startValue = this.value;
            this.targetValue = newValue;
            this.startTime = currentTime;
            this.done = false;
        }

        long elapsed = currentTime - this.startTime;
        if (elapsed >= this.duration) {
            this.value = this.targetValue;
            this.done = true;
            return this.value;
        } else {
            float progress = (float) elapsed / (float) this.duration;
            float easedProgress = (float) this.easing.ease(progress);
            this.value = this.startValue + (this.targetValue - this.startValue) * easedProgress;
            return this.value;
        }
    }

    public void setValue(float newValue) {
        this.value = newValue;
        this.startValue = newValue;
        this.targetValue = newValue;
        this.done = true;
    }

    public void reset(float initialValue) {
        this.value = initialValue;
        this.startValue = initialValue;
        this.targetValue = initialValue;
        this.done = true;
    }

    public void reset() {
        this.reset(0.0F);
    }

    public void animateTo(float newTarget) {
        if (newTarget != this.targetValue) {
            this.startValue = this.value;
            this.targetValue = newTarget;
            this.startTime = UiFrameContext.nowMillis();
            this.done = false;
        }
    }

    public float run() {
        return this.run(this.targetValue);
    }

    public long getDuration() {
        return this.duration;
    }

    public float getValue() {
        return this.value;
    }

    public Easing getEasing() {
        return this.easing;
    }

    public long getStartTime() {
        return this.startTime;
    }

    public float getStartValue() {
        return this.startValue;
    }

    public float getTargetValue() {
        return this.targetValue;
    }

    public boolean isDone() {
        return this.done;
    }

    public boolean isDirection() {
        return this.direction;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public void setEasing(Easing easing) {
        this.easing = easing;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public void setStartValue(float startValue) {
        this.startValue = startValue;
    }

    public void setTargetValue(float targetValue) {
        this.targetValue = targetValue;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public void setDirection(boolean direction) {
        this.direction = direction;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof Animation other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else if (this.getDuration() != other.getDuration()) {
            return false;
        } else if (Float.compare(this.getValue(), other.getValue()) != 0) {
            return false;
        } else if (this.getStartTime() != other.getStartTime()) {
            return false;
        } else if (Float.compare(this.getStartValue(), other.getStartValue()) != 0) {
            return false;
        } else if (Float.compare(this.getTargetValue(), other.getTargetValue()) != 0) {
            return false;
        } else if (this.isDone() != other.isDone()) {
            return false;
        } else if (this.isDirection() != other.isDirection()) {
            return false;
        } else {
            Object this$easing = this.getEasing();
            Object other$easing = other.getEasing();
            return this$easing == null ? other$easing == null : this$easing.equals(other$easing);
        }
    }

    protected boolean canEqual(Object other) {
        return other instanceof Animation;
    }

    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        long $duration = this.getDuration();
        result = result * 59 + (int) ($duration >>> 32 ^ $duration);
        result = result * 59 + Float.floatToIntBits(this.getValue());
        long $startTime = this.getStartTime();
        result = result * 59 + (int) ($startTime >>> 32 ^ $startTime);
        result = result * 59 + Float.floatToIntBits(this.getStartValue());
        result = result * 59 + Float.floatToIntBits(this.getTargetValue());
        result = result * 59 + (this.isDone() ? 79 : 97);
        result = result * 59 + (this.isDirection() ? 79 : 97);
        Object $easing = this.getEasing();
        return result * 59 + ($easing == null ? 43 : $easing.hashCode());
    }

    @Override
    public String toString() {
        return "Animation(duration="
                + this.getDuration()
                + ", value="
                + this.getValue()
                + ", easing="
                + this.getEasing()
                + ", startTime="
                + this.getStartTime()
                + ", startValue="
                + this.getStartValue()
                + ", targetValue="
                + this.getTargetValue()
                + ", done="
                + this.isDone()
                + ", direction="
                + this.isDirection()
                + ")";
    }
}
