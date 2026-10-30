package universalmod.service.media.optmedia;

import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaInfo;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

public final class OptMediaPlayerInfo {
    private final ExecutorService nativeExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "OptMedia-native");
        thread.setDaemon(true);
        return thread;
    });
    private final Session session = new Session(this);
    private volatile Snapshot last = Snapshot.empty();
    private volatile boolean closed;
    
    
    private volatile long pendingSeekTargetMs = -1L;
    private volatile long pendingSeekStartedNanos;
    private volatile long pendingSeekDeadlineMs;

    public List<IMediaSession> getMediaSessions() {
        IMediaSession current = getCurrentMediaSession();
        return current == null ? List.of() : List.of(current);
    }

    public IMediaSession getCurrentMediaSession() {
        Snapshot snapshot = readSnapshot();
        return snapshot.available ? session : null;
    }

    public byte[] getCurrentArtworkPng() {
        byte[] artwork = last.artwork;
        return artwork == null ? null : artwork.clone();
    }

    public TimelineSnapshot getCurrentTimelineSnapshot() {
        Snapshot snapshot = last;
        if (!snapshot.available) return TimelineSnapshot.unavailable();
        return new TimelineSnapshot(snapshot.positionMs, snapshot.durationMs, snapshot.playing, 1.0D, true);
    }

    public boolean seek(long positionMs) {
        final long requested = Math.max(0L, positionMs);
        return callNative(() -> {
            Snapshot before = last;
            long target = before.available && before.durationMs > 0L
                    ? Math.min(before.durationMs, requested)
                    : requested;

            OptMediaBridge.library().SeekToMs(target);

            long nowMs = System.currentTimeMillis();
            pendingSeekTargetMs = target;
            pendingSeekStartedNanos = System.nanoTime();
            pendingSeekDeadlineMs = nowMs + 2_000L;
            if (before.available) {
                last = new Snapshot(true, before.title, before.artist, before.source, before.artwork,
                        before.playing, before.durationMs, target);
            }
            return true;
        }, false);
    }

    public float volume() {
        return callNative(() -> OptMediaBridge.library().GetVolume(), -1.0F);
    }

    public boolean volume(float value) {
        float clamped = Math.max(0.0F, Math.min(1.0F, value));
        return callNative(() -> OptMediaBridge.library().SetVolume(clamped), false);
    }

    private void play() { callNative(() -> { OptMediaBridge.library().Play(); return true; }, false); }
    private void pause() { callNative(() -> { OptMediaBridge.library().Pause(); return true; }, false); }
    private void togglePlayPause() { callNative(() -> { OptMediaBridge.library().TogglePlayPause(); return true; }, false); }
    private void next() { callNative(() -> { OptMediaBridge.library().SkipNext(); return true; }, false); }
    private void previous() { callNative(() -> { OptMediaBridge.library().SkipPrevious(); return true; }, false); }

    public void cleanup() {
        if (closed) return;
        closed = true;
        try {
            Future<?> cleanup = nativeExecutor.submit(OptMediaBridge::cleanup);
            cleanup.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            cleanupDirectly();
        } catch (ExecutionException | RejectedExecutionException failure) {
            cleanupDirectly();
        } finally {
            nativeExecutor.shutdownNow();
            clearPendingSeek();
            last = Snapshot.empty();
        }
    }

    private static void cleanupDirectly() {
        try {
            OptMediaBridge.cleanup();
        } catch (RuntimeException | LinkageError cleanupFailure) {
            
        }
    }

    public String failure() { return OptMediaBridge.failure(); }

    public record TimelineSnapshot(long positionMillis, long durationMillis, boolean playing, double playbackRate, boolean available) {
        public TimelineSnapshot {
            positionMillis = Math.max(0L, positionMillis);
            durationMillis = Math.max(0L, durationMillis);
            playbackRate = Double.isFinite(playbackRate) && playbackRate > 0.0D ? playbackRate : 1.0D;
        }

        public static TimelineSnapshot unavailable() {
            return new TimelineSnapshot(0L, 0L, false, 1.0D, false);
        }
    }

    private Snapshot readSnapshot() {
        return callNative(this::readSnapshotNative, Snapshot.empty());
    }

    private Snapshot readSnapshotNative() {
        if (!OptMediaBridge.available()) return Snapshot.empty();
        OptMediaInfo info = new OptMediaInfo();
        OptMediaNativeLibrary nativeLibrary = OptMediaBridge.library();
        boolean acquired = false;
        try {
            acquired = nativeLibrary.GetCurrentMediaInfo(info);
            if (!acquired) {
                return Snapshot.empty();
            }
            String title = safe(info.title());
            String artist = safe(info.artist());
            String source = safe(info.source());
            boolean playing = info.playing();
            long durationMs = Math.max(0L, info.durationMs);
            long nativePositionMs = durationMs > 0L
                    ? Math.max(0L, Math.min(durationMs, info.positionMs))
                    : Math.max(0L, info.positionMs);

            Snapshot previous = last;
            long resolvedPositionMs = nativePositionMs;
            long pendingTarget = pendingSeekTargetMs;
            if (pendingTarget >= 0L) {
                boolean sameTrack = previous.available
                        && previous.durationMs == durationMs
                        && previous.title.equals(title)
                        && previous.artist.equals(artist);
                if (!sameTrack) {
                    clearPendingSeek();
                } else {
                    long elapsedMs = pendingSeekStartedNanos > 0L
                            ? Math.max(0L, (System.nanoTime() - pendingSeekStartedNanos) / 1_000_000L)
                            : 0L;
                    long expected = pendingTarget + (playing ? elapsedMs : 0L);
                    if (durationMs > 0L) expected = Math.min(durationMs, expected);

                    if (Math.abs(nativePositionMs - expected) <= 1_500L) {
                        resolvedPositionMs = expected;
                        clearPendingSeek();
                    } else if (System.currentTimeMillis() <= pendingSeekDeadlineMs) {
                        resolvedPositionMs = expected;
                    } else {
                        clearPendingSeek();
                    }
                }
            }

            Snapshot snapshot = new Snapshot(
                    true, title, artist, source, info.artwork(), playing, durationMs, resolvedPositionMs);
            last = snapshot;
            return snapshot;
        } finally {
            if (acquired) {
                try {
                    nativeLibrary.FreeMediaInfo(info);
                } catch (RuntimeException | LinkageError cleanupFailure) {
            
        }
                info.clearNativePointers();
            }
        }
    }

    private void clearPendingSeek() {
        pendingSeekTargetMs = -1L;
        pendingSeekStartedNanos = 0L;
        pendingSeekDeadlineMs = 0L;
    }

    private <T> T callNative(Callable<T> callable, T fallback) {
        if (closed) return fallback;
        try {
            Future<T> future = nativeExecutor.submit(callable);
            return future.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return fallback;
        } catch (ExecutionException | RejectedExecutionException unavailable) {
            return fallback;
        }
    }

    private static String safe(String value) { return value == null ? "" : value; }

    private record Snapshot(boolean available, String title, String artist, String source, byte[] artwork,
                            boolean playing, long durationMs, long positionMs) {
        static Snapshot empty() { return new Snapshot(false, "", "", "", null, false, 0L, 0L); }
    }

    private static final class Session implements IMediaSession {
        private final OptMediaPlayerInfo owner;

        private Session(OptMediaPlayerInfo owner) {
            this.owner = owner;
        }

        private Snapshot snapshot() { return owner.last; }
        @Override public String getOwner() { String source = snapshot().source; return source.isBlank() ? "OptMedia" : source; }
        @Override public String getSourceAppId() { return getOwner(); }
        @Override public String getSourceAppName() { return getOwner(); }
        @Override public String getSourceName() { return getOwner(); }
        @Override public String getSourceType() { return "native"; }
        @Override public String getServiceName() { return snapshot().source; }
        @Override public MediaInfo getMedia() {
            Snapshot snapshot = snapshot();
            if (!snapshot.available) return null;
            return new MediaInfo(snapshot.title, snapshot.artist, snapshot.artwork,
                    snapshot.positionMs, snapshot.durationMs, snapshot.playing, true);
        }
        @Override public void play() { owner.play(); }
        @Override public void pause() { owner.pause(); }
        @Override public void playPause() { owner.togglePlayPause(); }
        @Override public void stop() { owner.pause(); }
        @Override public void next() { owner.next(); }
        @Override public void previous() { owner.previous(); }
        @Override public void swapCycle() {}
        @Override public int getCycleType() { return -1; }
        @Override public boolean supportsSeeking() { return true; }
        @Override public boolean seekToMillis(long positionMillis) { return owner.seek(positionMillis); }
        @Override public boolean supportsVolume() { return true; }
        @Override public float getVolume() { return owner.volume(); }
        @Override public boolean setVolume(float volume) { return owner.volume(volume); }
    }
}
