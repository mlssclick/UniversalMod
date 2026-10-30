package universalmod.hud.element;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import universalmod.lifecycle.service.ClientService;
import universalmod.service.network.HttpClientService;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


public final class CurrentEventsService implements ClientService {
    private static final URI EVENTS_URI = URI.create("https://api.holyworld.me/v1/events");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(4);
    private static final long POLL_INTERVAL_SECONDS = 5L;

    private final HttpClientService http;
    private final AtomicBoolean requestRunning = new AtomicBoolean(false);
    private volatile List<CurrentEventsPanel.LiveEvent> events = List.of();
    private volatile String lastError = "";
    private volatile long lastSuccessfulUpdate;
    private volatile long lifecycleEpoch;
    private volatile boolean started;
    private ScheduledFuture<?> pollingTask;

    public CurrentEventsService(HttpClientService http) {
        this.http = Objects.requireNonNull(http, "http");
    }

    @Override
    public synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        long epoch = ++lifecycleEpoch;
        pollingTask = http.scheduleWithFixedDelay(() -> refreshSafely(epoch), 0L, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public synchronized void close() {
        started = false;
        lifecycleEpoch++;
        if (pollingTask != null) {
            pollingTask.cancel(false);
            pollingTask = null;
        }
        events = List.of();
    }

    public List<CurrentEventsPanel.LiveEvent> events() {
        return events;
    }

    public String lastError() {
        return lastError;
    }

    public long lastSuccessfulUpdate() {
        return lastSuccessfulUpdate;
    }

    public void refreshNow() {
        long epoch = lifecycleEpoch;
        if (!started) {
            return;
        }
        http.execute(() -> refreshSafely(epoch));
    }

    private void refreshSafely(long expectedEpoch) {
        if (!isCurrentLifecycle(expectedEpoch)
                || !shouldPollEvents()
                || !requestRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            HttpClientService.TextResponse response = http.get(
                    EVENTS_URI, REQUEST_TIMEOUT, "UniversalMod-CurrentEvents/2.0", 1024 * 1024, 1);
            List<CurrentEventsPanel.LiveEvent> fresh = parseEvents(response.body());
            if (!isCurrentLifecycle(expectedEpoch)) {
                return;
            }
            events = fresh;
            lastSuccessfulUpdate = System.currentTimeMillis();
            lastError = "";
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException failure) {
            if (!isCurrentLifecycle(expectedEpoch)) {
                return;
            }
            lastError = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        } finally {
            requestRunning.set(false);
        }
    }

    private boolean isCurrentLifecycle(long expectedEpoch) {
        return started && lifecycleEpoch == expectedEpoch;
    }

    private static boolean shouldPollEvents() {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        return minecraft != null
                && minecraft.player != null
                && minecraft.world != null
                && minecraft.getServer() == null;
    }

    static List<CurrentEventsPanel.LiveEvent> parseEvents(String body) {
        JsonElement rootElement = JsonParser.parseString(body == null ? "" : body);
        if (rootElement == null || !rootElement.isJsonObject()) {
            throw new IllegalArgumentException("Events API returned invalid JSON root");
        }

        List<CurrentEventsPanel.LiveEvent> fresh = new ArrayList<>();
        JsonObject root = rootElement.getAsJsonObject();
        for (Map.Entry<String, JsonElement> apiEntry : root.entrySet()) {
            String apiKey = apiEntry.getKey();
            JsonElement value = apiEntry.getValue();
            if (value == null || !value.isJsonArray()) {
                continue;
            }

            JsonArray array = value.getAsJsonArray();
            for (JsonElement eventElement : array) {
                if (eventElement == null || !eventElement.isJsonObject()) {
                    continue;
                }
                JsonObject eventObject = eventElement.getAsJsonObject();
                String id = stringOrEmpty(eventObject, "id");
                JsonObject metadata = eventObject.has("metadata") && eventObject.get("metadata").isJsonObject()
                        ? eventObject.getAsJsonObject("metadata") : null;
                String displayName = metadata == null ? "" : stringOrEmpty(metadata, "displayName");
                String rare = metadata == null ? "" : stringOrEmpty(metadata, "rare");
                if (displayName.isBlank()) {
                    continue;
                }
                fresh.add(new CurrentEventsPanel.LiveEvent(
                        apiKey, id, displayName, rare,
                        CurrentEventsRules.colorFor(apiKey, id, displayName, rare)));
            }
        }
        return List.copyOf(fresh);
    }

    private static String stringOrEmpty(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        JsonElement element = object.get(key);
        return element.isJsonPrimitive() ? element.getAsString() : "";
    }
}
