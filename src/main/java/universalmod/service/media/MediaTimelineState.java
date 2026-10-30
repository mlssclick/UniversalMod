package universalmod.service.media;


final class MediaTimelineState {
    private volatile View view = View.empty();

    View snapshot() {
        return view;
    }

    synchronized Observation observe(
            long nativePositionMs,
            long nativeDurationMs,
            boolean observedPlaying,
            boolean preciseTimeline,
            boolean trackChanged,
            long nowMillis,
            long nowNanos
    ) {
        View previous = view;
        long durationMs = Math.max(0L, nativeDurationMs);
        long positionMs = clampPosition(nativePositionMs, durationMs);

        if (!trackChanged && observedPlaying && durationMs > 0L) {
            long interpolated = previous.interpolatedPositionMillis(nowMillis, nowNanos);
            if (Math.abs(positionMs - interpolated) <= 1_500L) {
                positionMs = interpolated;
            }
        }

        View next = new View(
                observedPlaying,
                positionMs,
                durationMs,
                preciseTimeline,
                nowMillis,
                nowNanos,
                1.0D
        );
        view = next;
        return new Observation(previous.playing(), next.positionMillis());
    }

    synchronized long seek(long requestedPositionMs, long nowMillis, long nowNanos) {
        View current = view;
        long target = clampPosition(requestedPositionMs, current.durationMillis());
        view = new View(
                current.playing(),
                target,
                current.durationMillis(),
                current.precise(),
                nowMillis,
                nowNanos,
                current.playbackRate()
        );
        return target;
    }

    synchronized void restart(long nowMillis, long nowNanos) {
        View current = view;
        view = new View(
                true,
                0L,
                current.durationMillis(),
                current.precise(),
                nowMillis,
                nowNanos,
                current.playbackRate()
        );
    }

    synchronized void clear() {
        view = View.empty();
    }

    private static long clampPosition(long positionMs, long durationMs) {
        long safe = Math.max(0L, positionMs);
        return durationMs > 0L ? Math.min(durationMs, safe) : safe;
    }

    record Observation(boolean wasPlaying, long positionMillis) {
    }

    record View(
            boolean playing,
            long positionMillis,
            long durationMillis,
            boolean precise,
            long syncMillis,
            long syncNanos,
            double playbackRate
    ) {
        View {
            positionMillis = Math.max(0L, positionMillis);
            durationMillis = Math.max(0L, durationMillis);
            if (durationMillis > 0L) {
                positionMillis = Math.min(positionMillis, durationMillis);
            }
            playbackRate = Double.isFinite(playbackRate) && playbackRate > 0.0D ? playbackRate : 1.0D;
        }

        static View empty() {
            return new View(false, 0L, 0L, false, 0L, 0L, 1.0D);
        }

        long interpolatedPositionMillis(long nowMillis, long nowNanos) {
            double current = positionMillis;
            if (playing && syncNanos > 0L && nowNanos >= syncNanos) {
                current += (nowNanos - syncNanos) / 1_000_000.0D * playbackRate;
            } else if (playing && syncMillis > 0L && nowMillis > syncMillis) {
                current += (nowMillis - syncMillis) * playbackRate;
            }
            if (durationMillis > 0L) {
                current = Math.min(current, durationMillis);
            }
            return Math.max(0L, Math.round(current));
        }
    }
}
