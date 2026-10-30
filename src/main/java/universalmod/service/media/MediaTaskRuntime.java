package universalmod.service.media;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

final class MediaTaskRuntime implements AutoCloseable {
    private static final long POLLER_JOIN_TIMEOUT_MS = 250L;
    private static final long EXECUTOR_AWAIT_TIMEOUT_MS = 100L;

    private final ExecutorService actionExecutor = singleThreadExecutor("MusicService-Action", Thread.NORM_PRIORITY);
    private final ExecutorService artworkExecutor = singleThreadExecutor("MusicService-Artwork", Thread.MIN_PRIORITY);
    private final ExecutorService lyricsExecutor = singleThreadExecutor("MusicService-Lyrics", Thread.MIN_PRIORITY);
    private final ExecutorService metadataExecutor = singleThreadExecutor("MusicService-TrackMetadata", Thread.MIN_PRIORITY);
    private final List<Thread> pollers = new ArrayList<>(2);
    private final AtomicBoolean shutdownStarted = new AtomicBoolean();

    synchronized Thread startPoller(String name, Runnable action) {
        if (shutdownStarted.get()) {
            return null;
        }
        Thread thread = new Thread(action, name);
        thread.setDaemon(true);
        pollers.add(thread);
        thread.start();
        return thread;
    }

    void executeAction(Runnable action) {
        actionExecutor.execute(action);
    }

    void executeArtwork(Runnable action) {
        artworkExecutor.execute(action);
    }

    Future<?> executeLyrics(Runnable action) {
        return lyricsExecutor.submit(action);
    }

    void executeMetadata(Runnable action) {
        metadataExecutor.execute(action);
    }

    boolean acceptsActions() {
        return !shutdownStarted.get() && !actionExecutor.isShutdown();
    }

    
    void beginShutdown() {
        if (!shutdownStarted.compareAndSet(false, true)) {
            return;
        }
        actionExecutor.shutdownNow();
        artworkExecutor.shutdownNow();
        lyricsExecutor.shutdownNow();
        metadataExecutor.shutdownNow();
        synchronized (this) {
            for (Thread poller : pollers) {
                if (poller != null) {
                    poller.interrupt();
                }
            }
        }
    }

    
    void awaitTermination() {
        List<Thread> pollerSnapshot;
        synchronized (this) {
            pollerSnapshot = List.copyOf(pollers);
        }
        for (Thread poller : pollerSnapshot) {
            if (poller == null || !poller.isAlive()) {
                continue;
            }
            try {
                poller.join(POLLER_JOIN_TIMEOUT_MS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        await(actionExecutor, "action");
        await(artworkExecutor, "artwork");
        await(lyricsExecutor, "lyrics");
        await(metadataExecutor, "metadata");
    }

    private static ExecutorService singleThreadExecutor(String name, int priority) {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            thread.setPriority(priority);
            return thread;
        });
    }

    private static void await(ExecutorService executor, String role) {
        try {
            executor.awaitTermination(EXECUTOR_AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        beginShutdown();
        awaitTermination();
    }
}
