package universalmod.config;

final class ConfigAutosaveState {
    private final long saveDelayMs;
    private final long retryDelayMs;
    private boolean dirty;
    private long dirtySince;
    private long nextAttemptAt;

    ConfigAutosaveState(long saveDelayMs, long retryDelayMs) {
        if (saveDelayMs < 0L || retryDelayMs < 0L) {
            throw new IllegalArgumentException("autosave delays must be non-negative");
        }
        this.saveDelayMs = saveDelayMs;
        this.retryDelayMs = retryDelayMs;
    }

    void markDirty(long nowMs) {
        dirty = true;
        dirtySince = nowMs;
    }

    boolean shouldAttempt(long nowMs) {
        if (!dirty || nowMs < nextAttemptAt) {
            return false;
        }
        return elapsedAtLeast(nowMs, dirtySince, saveDelayMs);
    }

    void recordFailure(long nowMs) {
        if (!dirty) {
            return;
        }
        nextAttemptAt = saturatedAdd(nowMs, retryDelayMs);
    }

    void markClean() {
        dirty = false;
        dirtySince = 0L;
        nextAttemptAt = 0L;
    }

    boolean isDirty() {
        return dirty;
    }

    long nextAttemptAt() {
        return nextAttemptAt;
    }

    private static boolean elapsedAtLeast(long nowMs, long sinceMs, long delayMs) {
        if (nowMs < sinceMs) {
            return false;
        }
        return nowMs - sinceMs >= delayMs;
    }

    private static long saturatedAdd(long value, long increment) {
        if (increment > 0L && value > Long.MAX_VALUE - increment) {
            return Long.MAX_VALUE;
        }
        return value + increment;
    }
}
