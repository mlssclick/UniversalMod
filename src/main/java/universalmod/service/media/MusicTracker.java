package universalmod.service.media;

import net.minecraft.client.texture.NativeImage;
import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaInfo;
import dev.redstones.mediaplayerinfo.MediaPlayerInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import universalmod.render.color.ColorUtil;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class MusicTracker {
    private static final MusicTracker INSTANCE = new MusicTracker();
    private static final Identifier[] COVER_IDS = {
            new Identifier("universalmod", "music_player/art_a"),
            new Identifier("universalmod", "music_player/art_b")
    };
    private static final Identifier[] BACKGROUND_IDS = {
            new Identifier("universalmod", "music_player/background_a"),
            new Identifier("universalmod", "music_player/background_b")
    };
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final WindowsMediaHelper windowsHelper = new WindowsMediaHelper();
    private volatile long lyricsOffsetMillis = MediaPreferences.readLyricsOffset();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final ExecutorService actionExecutor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "MusicService-Action");
                thread.setDaemon(true);
                return thread;
            }
    );
    private final ExecutorService artworkExecutor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "MusicService-Artwork");
                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            }
    );
    private final AtomicLong artworkGeneration = new AtomicLong();
    private volatile boolean running = true;
    private volatile Thread worker;
    private volatile Thread timelineWorker;
    private volatile Thread artworkWorker;
    private volatile IMediaSession session;
    private volatile boolean playing;
    private volatile long positionMs;
    private volatile double durationSeconds;
    private volatile boolean preciseTimeline;
    private volatile boolean fastTimelineActive;
    private volatile long lastKnownSessionPosition = -1L;
    private volatile long lastSyncTime;
    private volatile long lastSyncNanos;
    private volatile double playbackRate = 1.0D;
    private volatile long lastInteractionTime;
    private volatile String timingTrackKey = "";
    private volatile String currentTitle = "No Session";
    private volatile String currentAuthor = "";
    private volatile Identifier coverIdentifier;
    private volatile Identifier previousCoverIdentifier;
    private volatile Identifier backgroundIdentifier;
    private volatile Identifier previousBackgroundIdentifier;
    private final NativeImageBackedTexture[] coverTextures = new NativeImageBackedTexture[2];
    private final NativeImageBackedTexture[] backgroundTextures = new NativeImageBackedTexture[2];
    private volatile int coverSlot = -1;
    private volatile long coverTransitionAt;
    private volatile ArtworkPalette coverPalette = ArtworkPalette.DEFAULT;
    private volatile ArtworkPalette previousCoverPalette = ArtworkPalette.DEFAULT;
    private volatile byte[] lastArtworkBytes;
    private volatile int mediaColor = ColorUtil.WHITE;
    private volatile String currentSourceAppId = "";
    private volatile String currentSourceAppName = "";
    private volatile String currentSourceName = "";
    private volatile String currentSourceType = "";
    private volatile String currentServiceName = "";
    private volatile boolean currentRepeatSupported;
    private volatile int currentRepeatMode = -1;
    private volatile String mediaRuntimeError = "";
    private volatile String lyrics = "";
    private volatile LyricsTimeline.Document lyricsDocument = LyricsTimeline.empty();
    private volatile String lyricsStatus = "Waiting for media";
    private volatile boolean lyricsLoading;
    private volatile String lastTrack = "";
    private volatile long nextLyricsRetryAt;
    private final AtomicLong lyricsGeneration = new AtomicLong();

    private MusicTracker() {
    }

    public static MusicTracker getInstance() {
        return INSTANCE;
    }

    public void ensureStarted() {
        if (!running || !started.compareAndSet(false, true)) {
            return;
        }
        Thread metadata = new Thread(this::pollSessions, "SMTC-JNI-Poller");
        metadata.setDaemon(true);
        worker = metadata;
        metadata.start();

        Thread timeline = new Thread(this::pollTimeline, "SMTC-Timeline-Poller");
        timeline.setDaemon(true);
        timelineWorker = timeline;
        timeline.start();

        Thread artwork = new Thread(this::pollArtwork, "SMTC-Artwork-Poller");
        artwork.setDaemon(true);
        artworkWorker = artwork;
        artwork.start();
    }

    private void pollSessions() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                IMediaSession current = MediaPlayerInfo.INSTANCE.getCurrentMediaSession();
                MediaInfo info = current == null ? null : current.getMedia();
                session = current;
                if (info != null) {
                    mediaRuntimeError = "";
                    updateFromMediaInfo(current, info);
                } else {
                    mediaRuntimeError = "";
                    clearSessionState();
                }
            } catch (Throwable throwable) {
                String message = throwable.getMessage();
                clearSessionState();
                mediaRuntimeError = throwable.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
                lyricsStatus = mediaRuntimeError;
            }
            try {
                Thread.sleep(250L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void pollArtwork() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                byte[] artwork = MediaPlayerInfo.INSTANCE.getCurrentArtworkPng();
                if (artwork != null && artwork.length > 0) {
                    if (lastArtworkBytes == null || !Arrays.equals(lastArtworkBytes, artwork)) {
                        lastArtworkBytes = artwork.clone();
                        updateArtwork(lastArtworkBytes);
                    }
                } else if (session != null && lastArtworkBytes != null) {
                    lastArtworkBytes = null;
                    updateArtwork(null);
                }
            } catch (Throwable ignored) {
            }
            try {
                Thread.sleep(1500L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void pollTimeline() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                MediaPlayerInfo.TimelineSnapshot snapshot = MediaPlayerInfo.INSTANCE.getCurrentTimelineSnapshot();
                if (snapshot.available()) {
                    long now = System.currentTimeMillis();
                    long nowNanos = System.nanoTime();
                    long maxPosition = snapshot.durationMillis() > 0L
                            ? snapshot.durationMillis()
                            : durationSeconds > 0.0D ? Math.round(durationSeconds * 1000.0D) : Long.MAX_VALUE;
                    positionMs = Math.max(0L, Math.min(snapshot.positionMillis(), maxPosition));
                    if (snapshot.durationMillis() > 0L) {
                        durationSeconds = snapshot.durationMillis() / 1000.0D;
                    }
                    playbackRate = snapshot.playbackRate();
                    lastSyncTime = now;
                    lastSyncNanos = nowNanos;
                    if (now - lastInteractionTime > 250L) {
                        playing = snapshot.playing();
                    }
                    fastTimelineActive = true;
                    preciseTimeline = true;
                }
            } catch (UnsatisfiedLinkError | NoSuchMethodError unavailable) {
                fastTimelineActive = false;
                return;
            } catch (Throwable ignored) {
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void updateFromMediaInfo(IMediaSession current, MediaInfo info) {
        updateSourceState(current);

        String newTitle = safe(info.getTitle());
        String newAuthor = safe(info.getArtist());
        String newTrackKey = newAuthor + "\u0000" + newTitle;
        boolean trackChanged = !Objects.equals(timingTrackKey, newTrackKey);

        currentTitle = newTitle;
        currentAuthor = newAuthor;
        long nativePositionMs = Math.max(0L, info.getPositionMillis());
        long nativeDurationMs = Math.max(0L, info.getDurationMillis());
        long now = System.currentTimeMillis();

        durationSeconds = nativeDurationMs / 1000.0D;
        preciseTimeline = info.hasMillisecondTimeline() || fastTimelineActive;

        if (trackChanged) {
            timingTrackKey = newTrackKey;
            lastKnownSessionPosition = nativePositionMs;
            positionMs = nativePositionMs;
            lastSyncTime = now;
            lastSyncNanos = System.nanoTime();
            playbackRate = 1.0D;
            playing = info.isPlaying();
            lastInteractionTime = 0L;
        } else if (!fastTimelineActive) {
            boolean interactionPending = now - lastInteractionTime <= 2500L;
            if (!interactionPending) {
                playing = info.isPlaying();
            }
            lastKnownSessionPosition = nativePositionMs;
            positionMs = nativePositionMs;
            lastSyncTime = now;
            lastSyncNanos = System.nanoTime();
            playbackRate = 1.0D;
        }

        byte[] artwork = info.getArtworkPng();
        if (artwork != null && artwork.length > 0 && (lastArtworkBytes == null || !Arrays.equals(lastArtworkBytes, artwork))) {
            lastArtworkBytes = artwork.clone();
            updateArtwork(lastArtworkBytes);
        }

        updateLyrics(current, info);
    }

    private void updateLyrics(IMediaSession current, MediaInfo info) {
        LyricsFetcher.TrackIdentity identity = LyricsFetcher.identify(info.getArtist(), info.getTitle());
        String track = identity.artist() + "\u0000" + identity.title();
        if (!Objects.equals(track, lastTrack)) {
            lastTrack = track;
            lyricsGeneration.incrementAndGet();
            lyrics = "";
            lyricsDocument = LyricsTimeline.empty();
            lyricsLoading = false;
            lyricsStatus = identity.title().isBlank() ? "Waiting for track metadata" : "Loading timed lyrics";
            nextLyricsRetryAt = 0L;
        }
        fetchLyricsIfNeeded(identity, info.getDuration());
    }

    private void fetchLyricsIfNeeded(LyricsFetcher.TrackIdentity identity, long durationSeconds) {
        if (!running || identity == null || identity.title().isBlank() || lyricsLoading || !lyricsDocument.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextLyricsRetryAt) {
            return;
        }
        lyricsLoading = true;
        lyricsStatus = "Loading timed lyrics";
        long generation = lyricsGeneration.get();
        String requestedTrack = lastTrack;
        Thread thread = new Thread(() -> {
            LyricsTimeline.Document fetched = LyricsFetcher.fetch(identity.artist(), identity.title(), durationSeconds);
            if (!running || generation != lyricsGeneration.get() || !Objects.equals(requestedTrack, lastTrack)) {
                return;
            }
            if (fetched != null && !fetched.isEmpty() && fetched.hasLineTiming()) {
                lyricsDocument = fetched;
                lyrics = fetched.plainText();
                lyricsStatus = fetched.hasRealWordTiming() ? "Word-synced lyrics"
                        : fetched.hasEstimatedWordTiming() ? "Estimated karaoke"
                        : "Line-synced lyrics";
                nextLyricsRetryAt = Long.MAX_VALUE;
            } else {
                lyrics = "";
                lyricsDocument = LyricsTimeline.empty();
                lyricsStatus = "No timed lyrics in LRCLIB";
                nextLyricsRetryAt = System.currentTimeMillis() + 10_000L;
            }
            lyricsLoading = false;
        }, "UniversalMod-LyricsFetcher");
        thread.setDaemon(true);
        thread.start();
    }

    private void updateSourceState(IMediaSession current) {
        if (current == null) {
            return;
        }
        try {
            currentSourceAppId = safe(current.getSourceAppId());
        } catch (Throwable ignored) {
            currentSourceAppId = safe(current.getOwner());
        }
        try {
            currentSourceAppName = safe(current.getSourceAppName());
        } catch (Throwable ignored) {
            currentSourceAppName = currentSourceAppId;
        }
        try {
            currentSourceName = safe(current.getSourceName());
        } catch (Throwable ignored) {
            currentSourceName = currentSourceAppName;
        }
        try {
            currentSourceType = safe(current.getSourceType());
        } catch (Throwable ignored) {
            currentSourceType = "app";
        }
        windowsHelper.observe(currentSourceAppId);
        try {
            currentServiceName = safe(current.getServiceName());
        } catch (Throwable ignored) {
            currentServiceName = "";
        }
        try {
            currentRepeatSupported = current.isRepeatSupported();
            currentRepeatMode = currentRepeatSupported ? current.getCycleType() : -1;
        } catch (Throwable ignored) {
            currentRepeatSupported = false;
            currentRepeatMode = -1;
        lyricsGeneration.incrementAndGet();
        lyricsLoading = false;
        lyrics = "";
        lyricsDocument = LyricsTimeline.empty();
        lyricsStatus = "Waiting for media";
        lastTrack = "";
        nextLyricsRetryAt = 0L;
        }
    }

    private void clearSessionState() {
        session = null;
        playing = false;
        currentTitle = "No Session";
        currentAuthor = "";
        lastKnownSessionPosition = -1L;
        positionMs = 0L;
        durationSeconds = 0.0D;
        preciseTimeline = false;
        fastTimelineActive = false;
        timingTrackKey = "";
        lastSyncTime = 0L;
        lastSyncNanos = 0L;
        playbackRate = 1.0D;
        currentSourceAppId = "";
        currentSourceAppName = "";
        currentSourceName = "";
        currentSourceType = "";
        currentServiceName = "";
        currentRepeatSupported = false;
        currentRepeatMode = -1;
        boolean hadArtwork = lastArtworkBytes != null || coverIdentifier != null || coverSlot >= 0;
        lastArtworkBytes = null;
        if (hadArtwork) {
            updateArtwork(null);
        }
    }

    private void updateArtwork(byte[] imageBytes) {
        long generation = artworkGeneration.incrementAndGet();
        if (imageBytes == null) {
            mc.execute(() -> {
                if (generation != artworkGeneration.get()) return;
                previousCoverIdentifier = null;
                previousBackgroundIdentifier = null;
                coverIdentifier = null;
                backgroundIdentifier = null;
                coverSlot = -1;
                previousCoverPalette = coverPalette;
                coverPalette = ArtworkPalette.DEFAULT;
                coverTransitionAt = System.currentTimeMillis();
                mediaColor = ColorUtil.WHITE;
                disposeArtworkTextures();
            });
            return;
        }
        byte[] data = imageBytes.clone();
        try { artworkExecutor.execute(() -> prepareArtwork(data, generation)); }
        catch (RuntimeException ignored) { }
    }

    private void prepareArtwork(byte[] bytes, long generation) {
        if (generation != artworkGeneration.get() || !running) return;
        try {
            ArtworkProcessor.PreparedArtwork prepared = ArtworkProcessor.prepare(bytes, 384);
            if (prepared == null) return;
            if (generation != artworkGeneration.get() || !running) {
                prepared.image().close();
                prepared.background().close();
                return;
            }
            mc.execute(() -> applyPreparedArtwork(prepared, generation));
        } catch (Throwable ignored) { }
    }

    private void applyPreparedArtwork(ArtworkProcessor.PreparedArtwork prepared, long generation) {
        NativeImage image = prepared.image(), bg = prepared.background();
        if (generation != artworkGeneration.get() || !running) {
            image.close(); bg.close(); return;
        }
        int nextSlot = coverSlot == 0 ? 1 : 0;
        NativeImageBackedTexture foreground = null, background = null;
        try {
            
            disposeArtworkTexture(nextSlot);
            foreground = new NativeImageBackedTexture(image);
            background = new NativeImageBackedTexture(bg);
            mc.getTextureManager().registerTexture(COVER_IDS[nextSlot], foreground);
            mc.getTextureManager().registerTexture(BACKGROUND_IDS[nextSlot], background);
            coverTextures[nextSlot] = foreground;
            backgroundTextures[nextSlot] = background;
            previousCoverIdentifier = coverIdentifier;
            previousBackgroundIdentifier = backgroundIdentifier;
            previousCoverPalette = getArtworkPalette();
            coverSlot = nextSlot;
            coverIdentifier = COVER_IDS[nextSlot];
            backgroundIdentifier = BACKGROUND_IDS[nextSlot];
            coverPalette = prepared.palette();
            coverTransitionAt = System.currentTimeMillis();
            mediaColor = prepared.averageColor();
        } catch (Throwable failure) {
            if (foreground != null) try { foreground.close(); } catch (Exception ignored) { }
            else try { image.close(); } catch (Exception ignored) { }
            if (background != null) try { background.close(); } catch (Exception ignored) { }
            else try { bg.close(); } catch (Exception ignored) { }
        }
    }

    private void disposeArtworkTexture(int slot) {
        if (coverTextures[slot] != null) {
            try { mc.getTextureManager().destroyTexture(COVER_IDS[slot]); } catch (Throwable ignored) { }
            try { coverTextures[slot].close(); } catch (Throwable ignored) { }
            coverTextures[slot] = null;
        }
        if (backgroundTextures[slot] != null) {
            try { mc.getTextureManager().destroyTexture(BACKGROUND_IDS[slot]); } catch (Throwable ignored) { }
            try { backgroundTextures[slot].close(); } catch (Throwable ignored) { }
            backgroundTextures[slot] = null;
        }
    }

    private void disposeArtworkTextures() {
        disposeArtworkTexture(0);
        disposeArtworkTexture(1);
    }

    public Identifier getPreviousImage() {
        return System.currentTimeMillis() - coverTransitionAt < 600L ? previousCoverIdentifier : null;
    }

    public Identifier getBackgroundImage() { return backgroundIdentifier; }
    public Identifier getPreviousBackgroundImage() {
        return System.currentTimeMillis() - coverTransitionAt < 1_200L ? previousBackgroundIdentifier : null;
    }
    public float getArtworkTransition() {
        if (getPreviousImage() == null) return 1.0F;
        return Math.min(1.0F, (System.currentTimeMillis() - coverTransitionAt) / 600.0F);
    }
    public float getBackgroundTransition() {
        if (getPreviousBackgroundImage() == null) return 1.0F;
        return Math.min(1.0F, (System.currentTimeMillis() - coverTransitionAt) / 1_200.0F);
    }
    public ArtworkPalette getArtworkPalette() {
        float amount = Math.min(1.0F, Math.max(0.0F, (System.currentTimeMillis() - coverTransitionAt) / 700.0F));
        if (amount >= 1.0F) return coverPalette;
        ArtworkPalette a = previousCoverPalette, b = coverPalette;
        return new ArtworkPalette(lerpColor(a.primary(), b.primary(), amount),
                lerpColor(a.secondary(), b.secondary(), amount),
                lerpColor(a.highlight(), b.highlight(), amount),
                lerpColor(a.foreground(), b.foreground(), amount),
                lerpColor(a.fourth(), b.fourth(), amount));
    }
    private static int lerpColor(int a, int b, float t) {
        int r = Math.round(((a >>> 16) & 255) * (1.0F-t) + ((b >>> 16) & 255) * t);
        int g = Math.round(((a >>> 8) & 255) * (1.0F-t) + ((b >>> 8) & 255) * t);
        int blue = Math.round((a & 255) * (1.0F-t) + (b & 255) * t);
        return 0xFF000000 | r << 16 | g << 8 | blue;
    }

    public void togglePlay() {
        IMediaSession current = session;
        if (current == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long visiblePositionMs = interpolatedPositionMs(now);
        boolean newState = !playing;
        positionMs = visiblePositionMs;
        lastSyncTime = now;
        lastSyncNanos = System.nanoTime();
        playing = newState;
        lastInteractionTime = now;
        runAction(() -> {
                    try {
                        current.playPause();
                    } catch (Throwable first) {
                        try {
                            if (newState) {
                                current.play();
                            } else {
                                current.pause();
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
        );
    }

    public void previousTrack() {
        IMediaSession current = session;
        if (current == null) {
            return;
        }
        lastInteractionTime = System.currentTimeMillis();
        runAction(() -> {
                    try {
                        current.previous();
                    } catch (Throwable ignored) {
                    }
                }
        );
    }

    public void nextTrack() {
        IMediaSession current = session;
        if (current == null) {
            return;
        }
        lastInteractionTime = System.currentTimeMillis();
        runAction(() -> {
                    try {
                        current.next();
                    } catch (Throwable ignored) {
                    }
                }
        );
    }

    private void runAction(Runnable action) {
        if (!running || actionExecutor.isShutdown()) {
            return;
        }
        try {
            actionExecutor.execute(action);
        } catch (RuntimeException ignored) {
        }
    }

    public boolean haveActiveSession() {
        ensureStarted();
        return running && session != null && session.getMedia() != null;
    }

    public IMediaSession getSession() {
        ensureStarted();
        return session;
    }

    public boolean isPlaying() {
        ensureStarted();
        return playing;
    }

    public String title() {
        ensureStarted();
        return currentTitle;
    }

    public String author() {
        ensureStarted();
        return currentAuthor;
    }

    public double durationSeconds() {
        ensureStarted();
        return durationSeconds;
    }

    public double positionSeconds() {
        ensureStarted();
        return interpolatedPositionMs(System.currentTimeMillis()) / 1000.0D;
    }

    private long interpolatedPositionMs(long now) {
        double current = Math.max(0L, positionMs);
        long anchorNanos = lastSyncNanos;
        if (playing && anchorNanos > 0L) {
            long elapsedNanos = Math.max(0L, System.nanoTime() - anchorNanos);
            current += elapsedNanos / 1_000_000.0D * playbackRate;
        } else if (playing && lastSyncTime > 0L && now > lastSyncTime) {
            current += (now - lastSyncTime) * playbackRate;
        }
        if (durationSeconds > 0.0D) {
            current = Math.min(current, Math.max(0L, Math.round(durationSeconds * 1000.0D)));
        }
        return Math.max(0L, Math.round(current));
    }

    public float progress() {
        double duration = durationSeconds();
        if (duration <= 0.0D) {
            return 0.0F;
        }
        return (float) Math.max(0.0D, Math.min(1.0D, positionSeconds() / duration));
    }

    public Identifier getImage() {
        ensureStarted();
        return coverIdentifier;
    }

    public int getMediaColor() {
        return mediaColor;
    }

    public String getSourceAppId() {
        ensureStarted();
        return currentSourceAppId;
    }

    public String getSourceAppName() {
        ensureStarted();
        return currentSourceAppName;
    }

    public String getSourceName() {
        ensureStarted();
        return currentSourceName;
    }

    public String getSourceType() {
        ensureStarted();
        return currentSourceType;
    }

    public String getServiceName() {
        ensureStarted();
        return currentServiceName;
    }

    public boolean isRepeatSupported() {
        ensureStarted();
        return currentRepeatSupported;
    }

    public int getRepeatMode() {
        ensureStarted();
        return currentRepeatMode;
    }

    public void cycleRepeat() {
        IMediaSession current = session;
        if (current == null || !currentRepeatSupported) {
            return;
        }
        runAction(() -> {
                    try {
                        current.swapCycle();
                        currentRepeatMode = current.getCycleType();
                    } catch (Throwable ignored) {
                    }
                }
        );
    }

    public void setRepeatMode(int mode) {
        IMediaSession current = session;
        if (current == null || !currentRepeatSupported || mode < 0 || mode > 2) {
            return;
        }
        runAction(() -> {
                    try {
                        if (current.setCycleType(mode)) {
                            currentRepeatMode = mode;
                        } else {
                            currentRepeatMode = current.getCycleType();
                        }
                    } catch (Throwable ignored) {
                    }
                }
        );
    }

    public String getLyrics() {
        ensureStarted();
        return lyrics == null ? "" : lyrics;
    }

    public LyricsTimeline.Document getLyricsDocument() {
        ensureStarted();
        return lyricsDocument == null ? LyricsTimeline.empty() : lyricsDocument;
    }

    public String getLyricsStatus() {
        ensureStarted();
        return lyricsStatus == null || lyricsStatus.isBlank() ? "No timed lyrics" : lyricsStatus;
    }

    public boolean isLyricsLoading() {
        ensureStarted();
        return lyricsLoading;
    }

    public String getMediaRuntimeError() {
        ensureStarted();
        return mediaRuntimeError == null ? "" : mediaRuntimeError;
    }

    public long getSyncedPositionMillis() {
        ensureStarted();
        return interpolatedPositionMs(System.currentTimeMillis());
    }

    public double getSyncedPositionSeconds() {
        return getSyncedPositionMillis() / 1000.0D;
    }

    public boolean hasPreciseTimeline() {
        ensureStarted();
        return preciseTimeline;
    }

    public boolean seekToMillis(long position) {
        ensureStarted();
        if (!haveActiveSession()) return false;
        long clamped = Math.max(0L, Math.min(Math.round(durationSeconds * 1000D), position));
        if (!windowsHelper.seekToMillis(clamped)) return false;
        positionMs = clamped;
        lastKnownSessionPosition = clamped;
        lastSyncTime = System.currentTimeMillis();
        lastSyncNanos = System.nanoTime();
        lastInteractionTime = lastSyncTime;
        return true;
    }
    public float volume() { ensureStarted(); return windowsHelper.volume(); }
    public boolean setVolume(float volume) { ensureStarted(); return windowsHelper.setVolume(volume); }
    public float adjustVolume(float amount) {
        float now = volume();
        if (now < 0F) return -1F;
        float result = Math.max(0F, Math.min(1F, now + amount));
        return setVolume(result) ? result : -1F;
    }
    public boolean isShuffleSupported() { return windowsHelper.shuffleSupported(); }
    public boolean isShuffleEnabled() { return windowsHelper.shuffleActive(); }
    public void toggleShuffle() { windowsHelper.shuffle(); }
    public float getAudioPeak() { return windowsHelper.peak(); }
    public long getLyricsOffsetMillis() { return lyricsOffsetMillis; }
    public void setLyricsOffsetMillis(long offsetMs) {
        lyricsOffsetMillis = Math.max(-3000L, Math.min(3000L, offsetMs));
        MediaPreferences.writeLyricsOffset(lyricsOffsetMillis);
    }
    public long getLyricsPositionMillis() { return Math.max(0L, getSyncedPositionMillis() + lyricsOffsetMillis); }

    public void shutdown() {
        running = false;
        windowsHelper.close();
        artworkGeneration.incrementAndGet();
        actionExecutor.shutdownNow();
        artworkExecutor.shutdownNow();
        Thread currentWorker = worker;
        Thread currentTimelineWorker = timelineWorker;
        Thread currentArtworkWorker = artworkWorker;
        if (currentWorker != null) {
            currentWorker.interrupt();
        }
        if (currentTimelineWorker != null) {
            currentTimelineWorker.interrupt();
        }
        if (currentArtworkWorker != null) {
            currentArtworkWorker.interrupt();
        }
        try {
            MediaPlayerInfo.INSTANCE.cleanup();
        } catch (Throwable ignored) {
        }
        if (currentWorker != null && currentWorker.isAlive()) {
            try {
                currentWorker.join(250L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        if (currentTimelineWorker != null && currentTimelineWorker.isAlive()) {
            try {
                currentTimelineWorker.join(250L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        if (currentArtworkWorker != null && currentArtworkWorker.isAlive()) {
            try {
                currentArtworkWorker.join(250L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        try {
            actionExecutor.awaitTermination(100L, TimeUnit.MILLISECONDS);
            artworkExecutor.awaitTermination(100L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        coverIdentifier = null;
        previousCoverIdentifier = null;
        backgroundIdentifier = null;
        previousBackgroundIdentifier = null;
        mc.execute(this::disposeArtworkTextures);
        session = null;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
