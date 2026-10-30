package universalmod.service.media;


final class MediaVolumeState {
    private volatile float current = -1.0F;
    private volatile long lastPollAt = -1L;

    boolean shouldPoll(long now, long intervalMillis) {
        long safeNow = Math.max(0L, now);
        if (lastPollAt >= 0L && safeNow - lastPollAt < Math.max(0L, intervalMillis)) {
            return false;
        }
        lastPollAt = safeNow;
        return true;
    }

    float current() {
        return current;
    }

    float setRequested(float requested) {
        current = normalize(requested);
        return current;
    }

    void observe(float observed) {
        if (observed >= 0.0F) {
            current = normalize(observed);
        }
    }

    void clear() {
        current = -1.0F;
        lastPollAt = -1L;
    }

    static float normalize(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
