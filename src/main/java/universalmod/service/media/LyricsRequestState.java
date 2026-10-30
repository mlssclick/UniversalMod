package universalmod.service.media;

import java.util.Objects;


final class LyricsRequestState {
    private volatile String trackKey = "";
    private volatile long nextRetryAt;
    private volatile int attempt;
    private static final long LONG_RETRY_MS = 90_000L;

    boolean switchTrack(String newTrackKey) {
        String safe = newTrackKey == null ? "" : newTrackKey;
        if (Objects.equals(trackKey, safe)) {
            return false;
        }
        trackKey = safe;
        attempt = 0;
        nextRetryAt = 0L;
        return true;
    }

    boolean canRequest(long now) {
        return now >= nextRetryAt;
    }

    int beginAttempt(long now) {
        attempt = Math.min(3, attempt + 1);
        nextRetryAt = Math.max(0L, now) + (attempt >= 3 ? LONG_RETRY_MS : 15_000L);
        return attempt;
    }

    boolean matches(String requestedTrack) {
        return Objects.equals(trackKey, requestedTrack == null ? "" : requestedTrack);
    }

    void stopRetrying() {
        nextRetryAt = Long.MAX_VALUE;
    }

    void reset() {
        trackKey = "";
        attempt = 0;
        nextRetryAt = 0L;
    }
}
