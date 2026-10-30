package universalmod.module.misc.coinprice;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import universalmod.lifecycle.service.ClientService;
import universalmod.service.network.HttpClientService;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


public final class CoinRateService implements ClientService {
    public static final String API_URL = "https://api.holyworld.me/v1/coins-trades?limit=5";
    public static final long UPDATE_INTERVAL_SECONDS = 60L;
    private static final double API_RATE_SCALE = 1_000_000.0D;
    private static final URI API_URI = URI.create(API_URL);
    private static final CoinRateService INSTANCE = new CoinRateService();

    private final AtomicBoolean requestRunning = new AtomicBoolean(false);
    private volatile HttpClientService http;
    private volatile ScheduledFuture<?> pollingTask;
    private volatile boolean started;
    private volatile long lifecycleEpoch;
    private volatile double averageRate;
    private volatile long lastSuccessfulUpdate;
    private volatile String lastError = "";

    private CoinRateService() {
    }

    public static CoinRateService instance() {
        return INSTANCE;
    }

    public static void initialize(HttpClientService http) {
        INSTANCE.http = Objects.requireNonNull(http, "http");
    }

    public static synchronized void startPolling() {
        INSTANCE.startInternal();
    }

    public static void refreshNow() {
        INSTANCE.refreshNowInternal();
    }

    public static double getAverageRate() {
        return INSTANCE.averageRate;
    }

    public static long getLastSuccessfulUpdate() {
        return INSTANCE.lastSuccessfulUpdate;
    }

    public static String getLastError() {
        return INSTANCE.lastError;
    }

    @Override
    public void start() {
        
    }

    @Override
    public synchronized void close() {
        ScheduledFuture<?> task = pollingTask;
        pollingTask = null;
        started = false;
        lifecycleEpoch++;
        if (task != null) {
            task.cancel(false);
        }
    }

    private synchronized void startInternal() {
        if (started) {
            return;
        }
        HttpClientService client = requireHttp();
        started = true;
        long epoch = ++lifecycleEpoch;
        pollingTask = client.scheduleAtFixedRate(() -> fetchAndUpdateSafely(epoch), 0L, UPDATE_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void refreshNowInternal() {
        if (!started) {
            startInternal();
            return;
        }
        long epoch = lifecycleEpoch;
        requireHttp().execute(() -> fetchAndUpdateSafely(epoch));
    }

    private void fetchAndUpdateSafely(long expectedEpoch) {
        if (!isCurrentLifecycle(expectedEpoch)
                || !CoinPrice.isActive()
                || !requestRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            HttpClientService.TextResponse response = requireHttp().get(
                    API_URI, Duration.ofSeconds(5), "UniversalMod-CoinPrice/2.0", 512 * 1024, 1);
            double parsed = calculateAverageRate(response.body());
            if (!isCurrentLifecycle(expectedEpoch)) {
                return;
            }
            averageRate = parsed;
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

    static double calculateAverageRate(String json) {
        JsonElement root = JsonParser.parseString(json == null ? "" : json);
        RateAccumulator accumulator = new RateAccumulator();
        collectRates(root, accumulator);
        if (accumulator.count == 0) {
            throw new IllegalArgumentException("API response contains no valid rate values");
        }
        return accumulator.sum / accumulator.count;
    }

    private static void collectRates(JsonElement element, RateAccumulator accumulator) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectRates(child, accumulator);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        JsonElement rate = object.get("rate");
        if (rate != null && rate.isJsonPrimitive() && rate.getAsJsonPrimitive().isNumber()) {
            double value = rate.getAsDouble();
            if (value > 0.0D && Double.isFinite(value)) {
                accumulator.sum += value * API_RATE_SCALE;
                accumulator.count++;
            }
        }
        for (var entry : object.entrySet()) {
            if (!"rate".equals(entry.getKey())) {
                collectRates(entry.getValue(), accumulator);
            }
        }
    }

    private HttpClientService requireHttp() {
        HttpClientService client = http;
        if (client == null) {
            throw new IllegalStateException("CoinRateService has not been initialized with HttpClientService");
        }
        return client;
    }

    private static final class RateAccumulator {
        double sum;
        int count;
    }
}
