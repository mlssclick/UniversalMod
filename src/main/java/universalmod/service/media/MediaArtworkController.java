package universalmod.service.media;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import universalmod.render.color.ColorUtil;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

final class MediaArtworkController {
    private static final Identifier[] COVER_IDS = {
            Identifier.fromNamespaceAndPath("universalmod", "music_player/art_a"),
            Identifier.fromNamespaceAndPath("universalmod", "music_player/art_b")
    };
    private static final Identifier[] BACKGROUND_IDS = {
            Identifier.fromNamespaceAndPath("universalmod", "music_player/background_a"),
            Identifier.fromNamespaceAndPath("universalmod", "music_player/background_b")
    };
    private static final long FADE_MILLIS = 600L;
    private static final long BACKGROUND_FADE_MILLIS = 1_200L;
    private static final long RETRY_MILLIS = 2_000L;
    private final Minecraft minecraft;
    private final MediaTaskRuntime tasks;
    private final LongSupplier mediaGeneration;
    private final Supplier<String> currentTrackKey;
    private final BooleanSupplier running;
    private final Runnable stateChanged;
    private final ArtworkRequestState requestState = new ArtworkRequestState();
    private final AtomicLong requestGeneration = new AtomicLong();
    private final AtomicBoolean replayRequested = new AtomicBoolean();
    private final AtomicLong retryAfterMs = new AtomicLong();
    private final DynamicTexture[] textures = new DynamicTexture[2];
    private final DynamicTexture[] backgrounds = new DynamicTexture[2];
    private volatile Identifier identifier;
    private volatile Identifier previousIdentifier;
    private volatile Identifier backgroundIdentifier;
    private volatile Identifier previousBackgroundIdentifier;
    private volatile long transitionStartedMs;
    private volatile int currentSlot = -1;
    private volatile int mediaColor = ColorUtil.WHITE;
    private volatile ArtworkPalette palette = ArtworkPalette.DEFAULT;
    private volatile ArtworkPalette previousPalette = ArtworkPalette.DEFAULT;

    MediaArtworkController(Minecraft minecraft, MediaTaskRuntime tasks,
                           LongSupplier mediaGeneration, Supplier<String> currentTrackKey,
                           BooleanSupplier running, Runnable stateChanged) {
        this.minecraft = Objects.requireNonNull(minecraft);
        this.tasks = Objects.requireNonNull(tasks);
        this.mediaGeneration = Objects.requireNonNull(mediaGeneration);
        this.currentTrackKey = Objects.requireNonNull(currentTrackKey);
        this.running = Objects.requireNonNull(running);
        this.stateChanged = Objects.requireNonNull(stateChanged);
    }

    void process(String trackKey, byte[] bytes, boolean cropSpotify) {
        ArtworkRequestState.Decision decision = requestState.observe(trackKey, bytes);
        if (decision.type() == ArtworkRequestState.DecisionType.CLEAR) {
            replayRequested.set(false);
            update(trackKey, null, cropSpotify, mediaGeneration.getAsLong());
        } else if (decision.type() == ArtworkRequestState.DecisionType.APPLY) {
            replayRequested.set(false);
            update(trackKey, decision.bytes(), cropSpotify, mediaGeneration.getAsLong());
        } else if (replayRequested.get() && System.currentTimeMillis() >= retryAfterMs.get()) {
            byte[] retained = bytes != null && bytes.length > 0 ? bytes.clone() : requestState.cachedBytesFor(trackKey);
            if (retained != null && replayRequested.compareAndSet(true, false)) {
                update(trackKey, retained, cropSpotify, mediaGeneration.getAsLong());
            }
        }
    }

    void onResourceReload() {

        previousIdentifier = null;
        previousBackgroundIdentifier = null;
        replayRequested.set(true);
        retryAfterMs.set(0L);
    }

    boolean resetTrackState() {
        replayRequested.set(false);
        boolean had = requestState.hasArtworkState() || identifier != null;
        requestState.resetTrackState();
        return had;
    }

    void clear(long generation) { update("", null, false, generation); }
    Identifier identifier() { return identifier; }
    Identifier previousIdentifier() {
        return System.currentTimeMillis() - transitionStartedMs < FADE_MILLIS ? previousIdentifier : null;
    }
    Identifier backgroundIdentifier() { return backgroundIdentifier; }
    Identifier previousBackgroundIdentifier() {
        return System.currentTimeMillis() - transitionStartedMs < BACKGROUND_FADE_MILLIS
                ? previousBackgroundIdentifier : null;
    }
    float backgroundTransitionProgress() {
        if (previousBackgroundIdentifier() == null) return 1F;
        return Math.max(0F, Math.min(1F,
                (System.currentTimeMillis() - transitionStartedMs) / (float)BACKGROUND_FADE_MILLIS));
    }
    float transitionProgress() {
        if (previousIdentifier() == null) return 1F;
        return Math.max(0F, Math.min(1F, (System.currentTimeMillis() - transitionStartedMs) / (float) FADE_MILLIS));
    }

    ArtworkPalette palette() {
        float t = Math.max(0F, Math.min(1F, (System.currentTimeMillis() - transitionStartedMs) / 700F));
        if (t >= 1F) return palette;
        ArtworkPalette from = previousPalette;
        ArtworkPalette to = palette;
        return new ArtworkPalette(mix(from.primary(), to.primary(), t),
                mix(from.secondary(), to.secondary(), t),
                mix(from.highlight(), to.highlight(), t),
                mix(from.foreground(), to.foreground(), t),
                mix(from.fourth(), to.fourth(), t));
    }
    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >>> 16) & 255) * (1F-t) + ((b >>> 16) & 255) * t);
        int g = Math.round(((a >>> 8) & 255) * (1F-t) + ((b >>> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1F-t) + (b & 255) * t);
        return 0xFF000000 | r<<16 | g<<8 | bl;
    }
    int mediaColor() { return mediaColor; }

    Identifier displayIdentifier() {
        Identifier id = identifier;
        int slot = currentSlot;
        if (id != null && slot >= 0) {
            DynamicTexture current = textures[slot];
            if (current == null || current.getTextureView() == null
                    || minecraft.getTextureManager().getTexture(id) != current) {
                replayRequested.set(true);
                retryAfterMs.set(0L);
                return null;
            }
        }
        return id;
    }

    void shutdown() {
        requestGeneration.incrementAndGet();
        replayRequested.set(false);
        requestState.resetTrackState();
        identifier = null;
        previousIdentifier = null;
        backgroundIdentifier = null;
        previousBackgroundIdentifier = null;
        currentSlot = -1;
        palette = ArtworkPalette.DEFAULT;
        previousPalette = ArtworkPalette.DEFAULT;
        mediaColor = ColorUtil.WHITE;
        minecraft.execute(this::destroyTextures);
    }

    private void scheduleRetry(String track, long generation, long request) {
        if (isCurrent(track, generation, request)) {
            retryAfterMs.set(System.currentTimeMillis() + RETRY_MILLIS);
            replayRequested.set(true);
        }
    }

    private void update(String track, byte[] bytes, boolean cropSpotify, long generation) {
        long request = requestGeneration.incrementAndGet();
        if (bytes == null) {
            minecraft.execute(() -> {
                if (!isCurrent(track, generation, request)) return;
                identifier = null;
                previousIdentifier = null;
                backgroundIdentifier = null;
                previousBackgroundIdentifier = null;
                currentSlot = -1;
                mediaColor = ColorUtil.WHITE;
                previousPalette = palette();
                palette = ArtworkPalette.DEFAULT;
                transitionStartedMs = System.currentTimeMillis();
                destroyTextures();
                stateChanged.run();
            });
            return;
        }
        byte[] copy = bytes.clone();
        try {
            tasks.executeArtwork(() -> prepare(copy, cropSpotify, track, generation, request));
        } catch (RejectedExecutionException failure) {
            scheduleRetry(track, generation, request);
        }
    }

    private void prepare(byte[] bytes, boolean cropSpotify, String track, long generation, long request) {
        if (!isCurrent(track, generation, request)) return;
        NativeImage image = null;
        try {
            ArtworkProcessor.PreparedArtwork prepared = ArtworkProcessor.prepare(bytes, 384, cropSpotify);
            if (prepared == null) return;
            image = prepared.image();
            if (!isCurrent(track, generation, request)) {
                image.close(); prepared.background().close(); return;
            }
            NativeImage ready = image;
            NativeImage background = prepared.background();
            image = null;
            minecraft.execute(() -> apply(ready, background, prepared.averageColor(),
                    prepared.palette(), track, generation, request));
        } catch (IOException | RuntimeException | LinkageError failure) {
            if (image != null) try { image.close(); } catch (Exception ignored) { }
            scheduleRetry(track, generation, request);
        }
    }

    private void apply(NativeImage image, NativeImage bg, int average, ArtworkPalette nextPalette,
                       String track, long generation, long request) {
        if (!isCurrent(track, generation, request)) { image.close(); bg.close(); return; }
        DynamicTexture replacement = null;
        DynamicTexture backgroundTexture = null;
        int slot = currentSlot == 0 ? 1 : 0;
        Identifier target = COVER_IDS[slot];
        try {

            if (textures[slot] != null) {
                minecraft.getTextureManager().release(target);
                textures[slot] = null;
            }
            if (backgrounds[slot] != null) {
                minecraft.getTextureManager().release(BACKGROUND_IDS[slot]);
                backgrounds[slot] = null;
            }
            replacement = new DynamicTexture(() -> "universalmod:media_artwork", image);
            backgroundTexture = new DynamicTexture(() -> "universalmod:media_background", bg);
            minecraft.getTextureManager().register(target, replacement);
            minecraft.getTextureManager().register(BACKGROUND_IDS[slot], backgroundTexture);
            backgrounds[slot] = backgroundTexture;
            textures[slot] = replacement;
            previousIdentifier = identifier;
            previousBackgroundIdentifier = backgroundIdentifier;
            currentSlot = slot;
            identifier = target;
            backgroundIdentifier = BACKGROUND_IDS[slot];
            previousPalette = palette();
            transitionStartedMs = System.currentTimeMillis();
            mediaColor = average;
            palette = nextPalette == null ? ArtworkPalette.DEFAULT : nextPalette;
            stateChanged.run();
        } catch (RuntimeException | LinkageError failure) {
            try { minecraft.getTextureManager().release(target); }
            catch (RuntimeException | LinkageError ignored) { }
            try { minecraft.getTextureManager().release(BACKGROUND_IDS[slot]); }
            catch (RuntimeException | LinkageError ignored) { }
            if (replacement != null) try { replacement.close(); } catch (RuntimeException ignored) { }
            else try { image.close(); } catch (RuntimeException ignored) { }
            if (backgroundTexture != null) try { backgroundTexture.close(); } catch (RuntimeException ignored) { }
            else try { bg.close(); } catch (RuntimeException ignored) { }
            scheduleRetry(track, generation, request);
        }
    }

    private void destroyTextures() {
        for (int i = 0; i < textures.length; i++) {
            if (textures[i] != null) {
                try { minecraft.getTextureManager().release(COVER_IDS[i]); }
                catch (RuntimeException | LinkageError ignored) { }
                textures[i] = null;
            }
            if (backgrounds[i] != null) {
                try { minecraft.getTextureManager().release(BACKGROUND_IDS[i]); }
                catch (RuntimeException | LinkageError ignored) { }
                backgrounds[i] = null;
            }
        }
    }

    private boolean isCurrent(String track, long generation, long request) {
        return running.getAsBoolean() && request == requestGeneration.get()
                && generation == mediaGeneration.getAsLong()
                && (track == null || track.isBlank() || Objects.equals(track, currentTrackKey.get()));
    }
}
