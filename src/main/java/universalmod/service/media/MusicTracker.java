package universalmod.service.media;

import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaInfo;
import universalmod.service.media.optmedia.OptMediaPlayerInfo;
import net.minecraft.util.Identifier;
import universalmod.lifecycle.service.ClientService;
import universalmod.render.color.ColorUtil;
import universalmod.service.network.HttpClientService;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class MusicTracker implements ClientService {
    private static final MusicTracker INSTANCE = new MusicTracker();

    public static MusicTracker getInstance() { return INSTANCE; }

    private final OptMediaPlayerInfo mediaBackend = new OptMediaPlayerInfo();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final MediaTaskRuntime tasks = new MediaTaskRuntime();
    private final Map<String, Float> bpmCache = new ConcurrentHashMap<>();
    private final AtomicLong mediaGeneration = new AtomicLong();
    private final AtomicReference<MediaSnapshot> snapshot = new AtomicReference<>(MediaSnapshot.empty(ColorUtil.WHITE));
    private final MediaInteractionState interaction = new MediaInteractionState();
    private final MediaSourceState sourceState = new MediaSourceState();
    private final MediaTimelineState timeline = new MediaTimelineState();
    private final MediaVolumeState volumeState = new MediaVolumeState();
    private final WindowsMediaHelper windowsHelper = new WindowsMediaHelper();
    private final MediaArtworkController artwork;
    private final MediaLyricsController lyrics;

    private volatile boolean running = true;
    private volatile IMediaSession session;
    private volatile String timingTrackKey = "";
    private volatile String currentTitle = "";
    private volatile String currentAuthor = "";
    private volatile String mediaRuntimeError = "";
    private final Object pollSignal = new Object();
    private final AtomicBoolean immediatePollRequested = new AtomicBoolean(false);
    private static final long SESSION_MISSING_GRACE_MS = 6_000L;

    private volatile float bpm;

    private volatile long lyricsOffsetMillis = MediaPreferences.readLyricsOffset();

    public MusicTracker() {
        artwork = new MediaArtworkController(
                net.minecraft.client.MinecraftClient.getInstance(),
                tasks,
                mediaGeneration::get,
                () -> timingTrackKey,
                () -> running,
                this::publishSnapshot
        );
        lyrics = new MediaLyricsController(tasks, () -> running, this::publishSnapshot);
    }

    public void onResourceReload() {
        universalmod.render.media.MediaBackgroundRenderer.reload();
        if (running) {
            artwork.onResourceReload();
            requestImmediatePoll();
        }
    }

    public void bindHttpClient(HttpClientService httpClient) {
        if (httpClient == null) {
            throw new IllegalArgumentException("httpClient must not be null");
        }
        lyrics.bindHttpClient(httpClient);
    }

    public void ensureStarted() {
        if (!running || !started.compareAndSet(false, true)) {
            return;
        }
        tasks.startPoller("OptMedia-Poller", this::pollSessions);
    }

    private void pollSessions() {
        int misses = 0;
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                IMediaSession current = selectMediaSession();
                MediaInfo info = current == null ? null : current.getMedia();
                if (info != null) {

                    misses = 0;
                    mediaRuntimeError = "";
                    if (!safe(info.getTitle()).isBlank()) {
                        session = current;
                        updateFromMediaInfo(current, info);
                        long now = System.currentTimeMillis();
                        if (volumeState.shouldPoll(now, 1_000L)) {
                            volumeState.observe(mediaBackend.volume());
                        }
                    }
                } else if (++misses >= 4) {
                    misses = 0;
                    if (session != null || !timingTrackKey.isBlank() || !currentTitle.isBlank()) {
                        clearSessionState();
                    }
                }
            } catch (RuntimeException | LinkageError throwable) {
                String message = throwable.getMessage();
                mediaRuntimeError = throwable.getClass().getSimpleName()
                        + (message == null || message.isBlank() ? "" : ": " + message);
                if (++misses >= 4) {
                    misses = 0;
                    clearSessionState();
                    lyrics.reportRuntimeFailure(mediaRuntimeError);
                }
            }

            if (immediatePollRequested.getAndSet(false)) {
                continue;
            }
            try {
                synchronized (pollSignal) {
                    if (!immediatePollRequested.get()) {
                        pollSignal.wait(250L);
                    }
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
            immediatePollRequested.set(false);
        }
    }

    private IMediaSession selectMediaSession() {
        try {
            return mediaBackend.getCurrentMediaSession();
        } catch (RuntimeException | LinkageError failure) {
            return null;
        }
    }

    private void requestImmediatePoll() {
        immediatePollRequested.set(true);
        synchronized (pollSignal) {
            pollSignal.notifyAll();
        }
    }

    private void updateFromMediaInfo(IMediaSession current, MediaInfo info) {
        sourceState.observe(current);
        windowsHelper.observe(sourceState.snapshot().appId());

        String rawTitle = safe(info.getTitle()).trim();
        String rawAuthor = safe(info.getArtist()).trim();
        LyricsFetcher.TrackIdentity identity = LyricsFetcher.identify(rawAuthor, rawTitle);

        long nativePositionMs = Math.max(0L, info.getPositionMillis());
        long nativeDurationMs = Math.max(0L, info.getDurationMillis());
        if (nativeDurationMs <= 0L && info.getDuration() > 0L) {
            nativeDurationMs = info.getDuration() * 1000L;
        }

        String newTrackKey = rawTitle + "|" + rawAuthor + "|" + nativeDurationMs;
        boolean trackChanged = !Objects.equals(timingTrackKey, newTrackKey);
        long now = System.currentTimeMillis();
        currentTitle = rawTitle;
        currentAuthor = rawAuthor;

        MediaTimelineState.Observation timelineObservation = timeline.observe(
                nativePositionMs,
                nativeDurationMs,
                info.isPlaying(),
                info.hasMillisecondTimeline(),
                trackChanged,
                now,
                System.nanoTime()
        );
        long reconciledPositionMs = timelineObservation.positionMillis();

        if (trackChanged) {
            mediaGeneration.incrementAndGet();
            timingTrackKey = newTrackKey;
            interaction.resetForTrack();
            bpm = 0.0F;
            queueBpmLookup(newTrackKey, rawAuthor, rawTitle, Math.max(0L, info.getDuration()));
        }

        maybeRestartRepeatOne(current, timelineObservation.wasPlaying(), info.isPlaying(),
                reconciledPositionMs, nativeDurationMs, now);

        byte[] artwork = null;
        try {
            artwork = info.getArtworkPng();
        } catch (RuntimeException | LinkageError failure) {

        }
        processArtwork(newTrackKey, artwork, isSpotify(current));
        lyrics.update(identity, newTrackKey, info.getDuration());
        publishSnapshot();
    }

    private void queueBpmLookup(String requestedTrack, String artist, String title, long durationSeconds) {
        try {
            tasks.executeMetadata(() -> {
                if (!Objects.equals(requestedTrack, timingTrackKey)) {
                    return;
                }

                Float value = bpmCache.get(requestedTrack);
                if (value == null) {
                    float resolved = DeezerBpmLookup.findBpmNear(artist, title, durationSeconds);
                    value = resolved;
                    if (bpmCache.size() > 64) {
                        bpmCache.clear();
                    }
                    bpmCache.put(requestedTrack, value);
                }

                if (value > 0.0F && Objects.equals(requestedTrack, timingTrackKey)) {
                    bpm = value;
                    publishSnapshot();
                }
            });
        } catch (RejectedExecutionException failure) {

        }
    }

    private boolean isSpotify(IMediaSession current) {
        if (current == null) {
            return false;
        }
        String owner = "";
        try {
            owner = safe(current.getOwner());
        } catch (RuntimeException | LinkageError failure) {

        }
        MediaSourceState.View source = sourceState.snapshot();
        return owner.toLowerCase(Locale.ROOT).contains("spotify")
                || source.appId().toLowerCase(Locale.ROOT).contains("spotify")
                || source.appName().toLowerCase(Locale.ROOT).contains("spotify");
    }

    private void processArtwork(String trackKey, byte[] bytes, boolean cropSpotify) {
        artwork.process(trackKey, bytes, cropSpotify);
    }

    private void clearSessionState() {
        mediaGeneration.incrementAndGet();
        session = null;
        currentTitle = "";
        currentAuthor = "";
        timingTrackKey = "";
        timeline.clear();
        sourceState.clear();
        volumeState.clear();
        boolean hadArtwork = artwork.resetTrackState();
        bpm = 0.0F;

        lyrics.reset();

        if (hadArtwork) {
            artwork.clear(mediaGeneration.get());
        }
        publishSnapshot();
    }

    public void togglePlay() {
        IMediaSession current = session;
        if (current == null) {
            return;
        }
        boolean fallbackPlay = !timeline.snapshot().playing();
        runAction(() -> {
            try {
                current.playPause();
            } catch (RuntimeException | LinkageError first) {
                try {
                    if (fallbackPlay) {
                        current.play();
                    } else {
                        current.pause();
                    }
                } catch (RuntimeException | LinkageError failure) {

        }
            } finally {
                requestImmediatePoll();
            }
        });
    }

    public void previousTrack() {
        IMediaSession current = session;
        if (current == null) {
            return;
        }
        runAction(() -> {
            try {
                current.previous();
            } catch (RuntimeException | LinkageError failure) {

        } finally {
                requestImmediatePoll();
            }
        });
    }

    public void nextTrack() {
        IMediaSession current = session;
        if (current == null) {
            return;
        }
        runAction(() -> {
            try {
                current.next();
            } catch (RuntimeException | LinkageError failure) {

        } finally {
                requestImmediatePoll();
            }
        });
    }

    private void maybeRestartRepeatOne(
            IMediaSession current,
            boolean wasPlaying,
            boolean observedPlaying,
            long observedPositionMs,
            long observedDurationMs,
            long now
    ) {
        if (!interaction.repeatOneRequested()
                || sourceState.snapshot().repeatSupported() || windowsHelper.repeatSupported() || current == null) {
            return;
        }
        if (!wasPlaying || observedPlaying || observedDurationMs <= 1000L) {
            return;
        }
        long endWindow = Math.max(280L, Math.min(850L, observedDurationMs / 250L));
        if (observedPositionMs < observedDurationMs - endWindow) {
            return;
        }
        if (!interaction.canAutoRestart(now)) {
            return;
        }

        interaction.markAutoRestart(now);
        timeline.restart(now, System.nanoTime());
        runAction(() -> {
            try {
                current.previous();
                try {
                    Thread.sleep(45L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                current.play();
            } catch (RuntimeException | LinkageError failure) {

        }
        });
    }

    private void runAction(Runnable action) {
        if (!running || !tasks.acceptsActions()) {
            return;
        }
        try {
            tasks.executeAction(action);
        } catch (RejectedExecutionException failure) {

        }
    }

    public MediaSnapshot snapshot() {
        ensureStarted();
        return snapshot.get();
    }

    private void publishSnapshot() {
        MediaTimelineState.View clock = timeline.snapshot();
        MediaSourceState.View source = sourceState.snapshot();
        MediaLyricsController.View lyricState = lyrics.snapshot();
        snapshot.set(new MediaSnapshot(
                mediaGeneration.get(), session, clock.playing(), clock.positionMillis(), clock.durationMillis(), clock.precise(),
                clock.syncMillis(), clock.syncNanos(), clock.playbackRate(), timingTrackKey, currentTitle, currentAuthor,
                artwork.identifier(), artwork.mediaColor(), source.appId(), source.appName(), source.sourceName(),
                source.sourceType(), source.serviceName(), source.repeatSupported(), source.repeatMode(), interaction.repeatOneRequested(),
                bpm, lyricState.lyrics(), lyricState.document(), lyricState.status(), lyricState.loading(), mediaRuntimeError
        ));
    }

    public boolean haveActiveSession() {
        ensureStarted();
        return running && snapshot.get().hasSession();
    }

    public IMediaSession getSession() {
        ensureStarted();
        return snapshot.get().session();
    }

    public boolean isPlaying() {
        ensureStarted();
        return snapshot.get().playing();
    }

    public String title() {
        ensureStarted();
        return snapshot.get().title();
    }

    public String author() {
        ensureStarted();
        return snapshot.get().artist();
    }

    public double durationSeconds() {
        ensureStarted();
        return snapshot.get().durationMillis() / 1000.0D;
    }

    public double positionSeconds() {
        ensureStarted();
        MediaSnapshot current = snapshot.get();
        return current.interpolatedPositionMillis(System.currentTimeMillis(), System.nanoTime()) / 1000.0D;
    }

    public float getBpm() {
        ensureStarted();
        return snapshot.get().bpm();
    }

    public float progress() {
        double duration = durationSeconds();
        if (duration <= 0.0D) {
            return 0.0F;
        }
        return (float) Math.max(0.0D, Math.min(1.0D, positionSeconds() / duration));
    }

    public boolean seekToMillis(long requestedPositionMs) {
        ensureStarted();
        IMediaSession current = session;
        if (current == null || !current.supportsSeeking()) {
            return false;
        }
        long now = System.currentTimeMillis();
        long target = timeline.seek(requestedPositionMs, now, System.nanoTime());
        publishSnapshot();
        runAction(() -> {
            try {
                current.seekToMillis(target);
            } catch (RuntimeException | LinkageError failure) {

        } finally {
                requestImmediatePoll();
            }
        });
        return true;
    }

    public float volume() {
        ensureStarted();
        return volumeState.current();
    }

    public boolean setVolume(float requestedVolume) {
        ensureStarted();
        IMediaSession current = session;
        if (current == null || !current.supportsVolume()) {
            return false;
        }
        float target = volumeState.setRequested(requestedVolume);
        interaction.markInteraction(System.currentTimeMillis());
        runAction(() -> {
            try {
                if (!current.setVolume(target)) {
                    volumeState.observe(current.getVolume());
                }
            } catch (RuntimeException | LinkageError failure) {

        }
        });
        return true;
    }

    public float adjustVolume(float delta) {
        ensureStarted();
        float current = volumeState.current();
        if (current < 0.0F) {
            IMediaSession active = session;
            if (active != null && active.supportsVolume()) {
                runAction(() -> {
                    try {
                        float observed = active.getVolume();
                        if (observed < 0.0F) return;
                        float target = MediaVolumeState.normalize(observed + delta);
                        if (active.setVolume(target)) volumeState.observe(target);
                    } catch (RuntimeException | LinkageError failure) {

        }
                });
            }
            return -1.0F;
        }
        float target = MediaVolumeState.normalize(current + delta);
        setVolume(target);
        return target;
    }

    public Identifier getImage() {
        ensureStarted();
        return artwork.displayIdentifier();
    }

    public Identifier getPreviousImage() {
        ensureStarted();
        return artwork.previousIdentifier();
    }

    public float getArtworkTransition() { return artwork.transitionProgress(); }
    public float getBackgroundTransition() { return artwork.backgroundTransitionProgress(); }

    public Identifier getBackgroundImage() { return artwork.backgroundIdentifier(); }

    public Identifier getPreviousBackgroundImage() { return artwork.previousBackgroundIdentifier(); }

    public ArtworkPalette getArtworkPalette() { return artwork.palette(); }

    public long getLyricsOffsetMillis() { return lyricsOffsetMillis; }

    public void setLyricsOffsetMillis(long offset) {
        long adjusted = Math.max(-3_000L, Math.min(3_000L, offset));
        if (adjusted == lyricsOffsetMillis) return;
        lyricsOffsetMillis = adjusted;
        runAction(() -> MediaPreferences.writeLyricsOffset(adjusted));
    }

    public long getLyricsPositionMillis() {
        return Math.max(0L, getSyncedPositionMillis() + lyricsOffsetMillis);
    }

    public int getMediaColor() {
        return snapshot.get().mediaColor();
    }

    public String getSourceAppId() {
        ensureStarted();
        return snapshot.get().sourceAppId();
    }

    public String getSourceAppName() {
        ensureStarted();
        return snapshot.get().sourceAppName();
    }

    public String getSourceName() {
        ensureStarted();
        return snapshot.get().sourceName();
    }

    public String getSourceType() {
        ensureStarted();
        return snapshot.get().sourceType();
    }

    public String getServiceName() {
        ensureStarted();
        return snapshot.get().serviceName();
    }

    public boolean isRepeatOneEnabled() {
        ensureStarted();
        MediaSnapshot current = snapshot.get();
        return current.repeatOneRequested() || (isRepeatSupported() && getRepeatMode() == 1);
    }

    public void setRepeatOneEnabled(boolean enabled) {
        interaction.setRepeatOneRequested(enabled);
        publishSnapshot();
        IMediaSession current = session;
        if (current == null || !sourceState.snapshot().repeatSupported()) {
            return;
        }
        runAction(() -> {
            try {
                int requestedMode = enabled ? 1 : 0;
                if (current.setCycleType(requestedMode)) {
                    sourceState.updateRepeatMode(requestedMode);
                } else {
                    sourceState.updateRepeatMode(current.getCycleType());
                }
                publishSnapshot();
            } catch (RuntimeException | LinkageError failure) {

        }
        });
    }

    public boolean isRepeatSupported() {
        ensureStarted();
        return snapshot.get().repeatSupported() || windowsHelper.repeatSupported();
    }

    public int getRepeatMode() {
        ensureStarted();
        int nativeMode = snapshot.get().repeatMode();
        return nativeMode >= 0 ? nativeMode : windowsHelper.repeatMode();
    }

    public boolean isShuffleSupported() {
        ensureStarted();
        return windowsHelper.shuffleSupported();
    }

    public boolean isShuffleEnabled() { return windowsHelper.shuffleActive(); }

    public float getAudioPeak() { return windowsHelper.peak(); }

    public void toggleShuffle() { windowsHelper.shuffle(); }

    public void cycleRepeat() {
        IMediaSession current = session;
        if (current == null) return;
        if (!sourceState.snapshot().repeatSupported()) {
            if (windowsHelper.repeatSupported()) windowsHelper.repeat();
            else setRepeatOneEnabled(!isRepeatOneEnabled());
            return;
        }
        runAction(() -> {
            try {
                current.swapCycle();
                sourceState.updateRepeatMode(current.getCycleType());
                publishSnapshot();
            } catch (RuntimeException | LinkageError failure) {

        }
        });
    }

    public void setRepeatMode(int mode) {
        IMediaSession current = session;
        if (current == null || !sourceState.snapshot().repeatSupported() || mode < 0 || mode > 2) {
            return;
        }
        runAction(() -> {
            try {
                if (current.setCycleType(mode)) {
                    sourceState.updateRepeatMode(mode);
                } else {
                    sourceState.updateRepeatMode(current.getCycleType());
                }
                publishSnapshot();
            } catch (RuntimeException | LinkageError failure) {

        }
        });
    }

    public String getLyrics() {
        ensureStarted();
        return snapshot.get().lyrics();
    }

    public LyricsTimeline.Document getLyricsDocument() {
        ensureStarted();
        return snapshot.get().lyricsDocument();
    }

    public String getLyricsStatus() {
        ensureStarted();
        String status = snapshot.get().lyricsStatus();
        return status.isBlank() ? "No timed lyrics" : status;
    }

    public boolean isLyricsLoading() {
        ensureStarted();
        return snapshot.get().lyricsLoading();
    }

    public String getMediaRuntimeError() {
        ensureStarted();
        return snapshot.get().runtimeError();
    }

    public long getSyncedPositionMillis() {
        ensureStarted();
        MediaSnapshot current = snapshot.get();
        return current.interpolatedPositionMillis(System.currentTimeMillis(), System.nanoTime());
    }

    public double getSyncedPositionSeconds() {
        return getSyncedPositionMillis() / 1000.0D;
    }

    public boolean hasPreciseTimeline() {
        ensureStarted();
        return snapshot.get().preciseTimeline();
    }

    @Override
    public void close() {
        shutdown();
    }

    public void shutdown() {
        running = false;
        tasks.beginShutdown();
        tasks.awaitTermination();

        try {
            mediaBackend.cleanup();
        } catch (RuntimeException | LinkageError failure) {

        }

        artwork.shutdown();
        windowsHelper.close();
        session = null;
        publishSnapshot();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

}
