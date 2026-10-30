package universalmod.service.media;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import universalmod.service.network.HttpClientService;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;


final class LyricsHttpGateway {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(7);
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    private final HttpClientService http;
    private final ConcurrentHashMap<String, Long> retryAfterBySource = new ConcurrentHashMap<>();

    LyricsHttpGateway(HttpClientService http) {
        this.http = Objects.requireNonNull(http, "http");
    }

    JsonElement requestJson(String source, String uri, long deadline, String... headers) {
        String text = requestText(source, uri, deadline, headers);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return JsonParser.parseString(text);
        } catch (RuntimeException failure) {
            markFailure(source, 2_000L);
            return null;
        }
    }

    String requestText(String source, String uri, long deadline, String... headers) {
        if (deadlineExceeded(deadline) || coolingDown(source)) {
            return null;
        }
        try {
            long remaining = Math.max(250L, deadline - System.currentTimeMillis());
            Map<String, String> requestHeaders = new LinkedHashMap<>();
            for (int i = 0; i + 1 < headers.length; i += 2) {
                String name = headers[i];
                String value = headers[i + 1];
                if (name != null && !name.isBlank() && value != null) {
                    requestHeaders.put(name, value);
                }
            }
            HttpClientService.TextResponse response = http.getRaw(
                    URI.create(uri),
                    Duration.ofMillis(Math.min(REQUEST_TIMEOUT.toMillis(), remaining)),
                    MAX_RESPONSE_BYTES,
                    requestHeaders
            );
            int status = response.statusCode();
            if (status == 429) {
                retryAfterBySource.put(source, retryAt(response.firstHeader("Retry-After")));
                return null;
            }
            if (status < 200 || status >= 300) {
                if (status >= 500) {
                    markFailure(source, 5_000L);
                }
                return null;
            }
            retryAfterBySource.remove(source);
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException | RuntimeException exception) {
            markFailure(source, 2_500L);
            return null;
        }
    }

    boolean coolingDown(String source) {
        return System.currentTimeMillis() < retryAfterBySource.getOrDefault(source, 0L);
    }

    static boolean deadlineExceeded(long deadline) {
        return System.currentTimeMillis() >= deadline;
    }

    private void markFailure(String source, long delayMs) {
        retryAfterBySource.merge(source, System.currentTimeMillis() + delayMs, Math::max);
    }

    private static long retryAt(String retryAfter) {
        long now = System.currentTimeMillis();
        String value = retryAfter == null ? "" : retryAfter.trim();
        try {
            return now + Math.max(1L, Long.parseLong(value)) * 1000L;
        } catch (NumberFormatException malformedNumber) {
            return now + 30_000L;
        }
    }
}
