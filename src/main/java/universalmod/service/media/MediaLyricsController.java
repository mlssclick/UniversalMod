package universalmod.service.media;

import universalmod.service.network.HttpClientService;

import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

final class MediaLyricsController {
    private final MediaTaskRuntime tasks;
    private final BooleanSupplier running;
    private final Runnable snapshotPublisher;
    private final LyricsRequestState requests = new LyricsRequestState();
    private final AtomicLong generation = new AtomicLong();
    private final AtomicReference<View> state = new AtomicReference<>(View.waitingForMedia());

    private volatile LyricsFetcher fetcher;
    private volatile Future<?> pendingLyrics;

    MediaLyricsController(MediaTaskRuntime tasks, BooleanSupplier running, Runnable snapshotPublisher) {
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.running = Objects.requireNonNull(running, "running");
        this.snapshotPublisher = Objects.requireNonNull(snapshotPublisher, "snapshotPublisher");
    }

    void bindHttpClient(HttpClientService httpClient) {
        fetcher = new LyricsFetcher(Objects.requireNonNull(httpClient, "httpClient"));
    }

    void update(LyricsFetcher.TrackIdentity identity, String trackKey, long durationSeconds) {
        if (requests.switchTrack(trackKey)) {
            cancelPending();
            generation.incrementAndGet();
            state.set(identity == null || identity.title().isBlank()
                    ? View.waitingForMetadata()
                    : View.loadingPrompt());
            snapshotPublisher.run();
        }

        View current = state.get();
        if (identity == null || identity.title().isBlank() || current.loading() || !current.document().isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!requests.canRequest(now)) {
            return;
        }
        queue(identity, trackKey, durationSeconds, now);
    }

    void reset() {
        cancelPending();
        generation.incrementAndGet();
        requests.reset();
        state.set(View.waitingForMedia());
    }

    void reportRuntimeFailure(String message) {
        String safeMessage = message == null ? "" : message.trim();
        View current = state.get();
        state.set(new View(current.lyrics(), current.document(), safeMessage, false));
        snapshotPublisher.run();
    }

    View snapshot() {
        return state.get();
    }

    private void queue(
            LyricsFetcher.TrackIdentity identity,
            String requestedTrack,
            long durationSeconds,
            long requestStartedAt
    ) {
        int attempt = requests.beginAttempt(requestStartedAt);
        state.set(new View("", LyricsTimeline.empty(), "Loading lyrics " + attempt + "/3", true));
        snapshotPublisher.run();
        long requestGeneration = generation.get();

        try {
            pendingLyrics = tasks.executeLyrics(() -> resolve(identity, requestedTrack, durationSeconds, attempt, requestGeneration));
        } catch (RejectedExecutionException failure) {
            state.set(new View("", LyricsTimeline.empty(), "Lyrics worker unavailable", false));
            snapshotPublisher.run();
        }
    }

    private void cancelPending() {
        Future<?> pending = pendingLyrics;
        pendingLyrics = null;
        if (pending != null) {
            pending.cancel(true);
        }
    }

    private void resolve(
            LyricsFetcher.TrackIdentity identity,
            String requestedTrack,
            long durationSeconds,
            int attempt,
            long requestGeneration
    ) {
        LyricsTimeline.Document fetched = fetch(identity, durationSeconds, requestedTrack);
        if (!running.getAsBoolean()
                || requestGeneration != generation.get()
                || !requests.matches(requestedTrack)) {
            return;
        }

        if (fetched != null && fetched.source() == LyricsTimeline.Source.LRCLIB_INSTRUMENTAL) {
            state.set(new View("", fetched, "Instrumental", false));
            requests.stopRetrying();
        } else if (fetched != null && !fetched.isEmpty()) {
            state.set(new View(fetched.plainText(), fetched, describe(fetched), false));
            requests.stopRetrying();
        } else {
            state.set(new View(
                    "",
                    LyricsTimeline.empty(),
                    attempt >= 3 ? "Lyrics not found; retry scheduled" : "Lyrics retry pending",
                    false
            ));
        }
        snapshotPublisher.run();
    }

    private LyricsTimeline.Document fetch(
            LyricsFetcher.TrackIdentity identity,
            long durationSeconds,
            String requestedTrack
    ) {
        try {
            LyricsFetcher currentFetcher = fetcher;
            return currentFetcher == null
                    ? LyricsTimeline.empty()
                    : currentFetcher.fetch(identity.artist(), identity.title(), Math.max(0L, durationSeconds));
        } catch (RuntimeException | LinkageError failure) {
            return LyricsTimeline.empty();
        }
    }

    private static String describe(LyricsTimeline.Document document) {
        if (document.hasRealWordTiming()) {
            return "Word-synced lyrics";
        }
        if (document.hasLineTiming()) {
            return "Line-synced lyrics";
        }
        return "Plain lyrics";
    }

    record View(String lyrics, LyricsTimeline.Document document, String status, boolean loading) {
        View {
            lyrics = lyrics == null ? "" : lyrics;
            document = document == null ? LyricsTimeline.empty() : document;
            status = status == null ? "" : status;
        }

        static View waitingForMedia() {
            return new View("", LyricsTimeline.empty(), "Waiting for media", false);
        }

        static View waitingForMetadata() {
            return new View("", LyricsTimeline.empty(), "Waiting for track metadata", false);
        }

        static View loadingPrompt() {
            return new View("", LyricsTimeline.empty(), "Loading lyrics", false);
        }
    }
}
