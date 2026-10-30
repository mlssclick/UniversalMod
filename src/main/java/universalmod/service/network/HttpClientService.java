package universalmod.service.network;

import universalmod.lifecycle.service.ClientService;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class HttpClientService implements ClientService {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final int DEFAULT_MAX_BYTES = 2 * 1024 * 1024;

    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(3, namedDaemonFactory("UniversalMod-HTTP"));
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, namedDaemonFactory("UniversalMod-Scheduler"));
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .executor(ioExecutor)
            .build();

    public TextResponse get(URI uri, Duration requestTimeout, String userAgent) throws IOException, InterruptedException {
        return get(uri, requestTimeout, userAgent, DEFAULT_MAX_BYTES, 2);
    }

    public TextResponse get(URI uri, Duration requestTimeout, String userAgent, int maxBytes, int retries)
            throws IOException, InterruptedException {
        Objects.requireNonNull(uri, "uri");
        Duration timeout = requestTimeout == null ? Duration.ofSeconds(5) : requestTimeout;
        int cap = Math.max(1_024, maxBytes);
        IOException lastIo = null;
        InterruptedException lastInterrupted = null;

        int attempts = Math.max(1, retries + 1);
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                        .timeout(timeout)
                        .GET()
                        .header("Accept", "application/json");
                if (userAgent != null && !userAgent.isBlank()) {
                    builder.header("User-Agent", userAgent);
                }
                HttpResponse<InputStream> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();
                if (status < 200 || status >= 300) {
                    closeBody(response.body());
                    if (status >= 400 && status < 500) {
                        throw new HttpStatusException(status, uri);
                    }
                    if (attempt + 1 < attempts) {
                        backoff(attempt);
                        continue;
                    }
                    throw new HttpStatusException(status, uri);
                }
                byte[] body = readBoundedBody(response, uri, cap);
                String contentType = response.headers().firstValue("content-type").orElse("");
                return new TextResponse(status, contentType, new String(body, StandardCharsets.UTF_8), response.headers().map());
            } catch (HttpStatusException status) {
                throw status;
            } catch (IOException io) {
                lastIo = io;
                if (attempt + 1 < attempts) {
                    backoff(attempt);
                    continue;
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                lastInterrupted = interrupted;
                break;
            }
        }
        if (lastInterrupted != null) {
            throw lastInterrupted;
        }
        throw lastIo == null ? new IOException("HTTP request failed: " + uri) : lastIo;
    }

    public TextResponse getRaw(
            URI uri,
            Duration requestTimeout,
            int maxBytes,
            Map<String, String> headers
    ) throws IOException, InterruptedException {
        Objects.requireNonNull(uri, "uri");
        Duration timeout = requestTimeout == null ? Duration.ofSeconds(5) : requestTimeout;
        int cap = Math.max(1_024, maxBytes);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .GET();
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                    continue;
                }
                builder.header(entry.getKey(), entry.getValue());
            }
        }
        HttpResponse<InputStream> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        byte[] body = readBoundedBody(response, uri, cap);
        String contentType = response.headers().firstValue("content-type").orElse("");
        return new TextResponse(
                response.statusCode(),
                contentType,
                new String(body, StandardCharsets.UTF_8),
                response.headers().map()
        );
    }

    private static byte[] readBoundedBody(HttpResponse<InputStream> response, URI uri, int cap) throws IOException {
        long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        return BoundedBodyReader.read(response.body(), declaredLength, cap, uri);
    }

    private static void closeBody(InputStream body) {
        if (body == null) {
            return;
        }
        try {
            body.close();
        } catch (IOException closeFailure) {
            
        }
    }

    public void execute(Runnable task) {
        ioExecutor.execute(task);
    }

    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long initialDelay, long delay, TimeUnit unit) {
        return scheduler.scheduleWithFixedDelay(task, initialDelay, delay, unit);
    }

    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        return scheduler.scheduleAtFixedRate(task, initialDelay, period, unit);
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        ioExecutor.shutdownNow();
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
            }
            if (!ioExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void backoff(int attempt) throws InterruptedException {
        long delay = Math.min(1_500L, 150L * (1L << Math.min(attempt, 3)));
        Thread.sleep(delay);
    }

    private static ThreadFactory namedDaemonFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
                return thread;
        };
    }

    public record TextResponse(
            int statusCode,
            String contentType,
            String body,
            Map<String, java.util.List<String>> headers
    ) {
        public TextResponse {
            contentType = contentType == null ? "" : contentType;
            body = body == null ? "" : body;
            headers = headers == null ? Map.of() : Map.copyOf(headers);
        }

        public String firstHeader(String name) {
            if (name == null || name.isBlank()) {
                return "";
            }
            for (Map.Entry<String, java.util.List<String>> entry : headers.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)
                        && entry.getValue() != null && !entry.getValue().isEmpty()) {
                    String value = entry.getValue().get(0);
                    return value == null ? "" : value;
                }
            }
            return "";
        }
    }

    public static final class HttpStatusException extends IOException {
        private final int statusCode;

        public HttpStatusException(int statusCode, URI uri) {
            super("HTTP " + statusCode + " from " + uri);
            this.statusCode = statusCode;
        }

        public int statusCode() {
            return statusCode;
        }
    }
}
