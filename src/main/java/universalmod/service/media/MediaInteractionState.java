package universalmod.service.media;

final class MediaInteractionState {
    private volatile long lastInteractionAt;
    private volatile boolean repeatOneRequested;
    private volatile long lastRepeatRestartAt;

    long lastInteractionAt() {
        return lastInteractionAt;
    }

    void markInteraction(long now) {
        lastInteractionAt = Math.max(0L, now);
    }

    void resetForTrack() {
        lastInteractionAt = 0L;
    }

    boolean repeatOneRequested() {
        return repeatOneRequested;
    }

    void setRepeatOneRequested(boolean requested) {
        repeatOneRequested = requested;
        lastRepeatRestartAt = 0L;
    }

    boolean canAutoRestart(long now) {
        return now - lastInteractionAt >= 350L && now - lastRepeatRestartAt >= 1_500L;
    }

    void markAutoRestart(long now) {
        lastRepeatRestartAt = now;
        lastInteractionAt = now;
    }
}
