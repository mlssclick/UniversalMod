package universalmod.hud.element;

public final class EffectPresentationPolicy {
    public static final int BENEFICIAL_WARNING_SECONDS = 15;
    public static final int HARMFUL_RECOVERY_SECONDS = 10;
    public static final int TICKS_PER_SECOND = 20;
    public static final int INFINITE_DURATION_THRESHOLD = 999_999_999;

    private EffectPresentationPolicy() {
    }

    public static NameState nameState(boolean harmful) {
        return harmful ? NameState.DANGER : NameState.NORMAL;
    }

    public static TimerState timerState(boolean harmful, int durationTicks) {
        if (durationTicks < 0 || durationTicks >= INFINITE_DURATION_THRESHOLD) {
            return TimerState.NORMAL;
        }
        if (harmful && durationTicks <= HARMFUL_RECOVERY_SECONDS * TICKS_PER_SECOND) {
            return TimerState.SUCCESS;
        }
        if (!harmful && durationTicks <= BENEFICIAL_WARNING_SECONDS * TICKS_PER_SECOND) {
            return TimerState.DANGER;
        }
        return TimerState.NORMAL;
    }

    public enum NameState {
        NORMAL,
        DANGER
    }

    public enum TimerState {
        NORMAL,
        DANGER,
        SUCCESS
    }
}
