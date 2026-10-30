package universalmod.service.media;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class LyricsProviderSupport {
    private LyricsProviderSupport() {
    }

    static boolean hasNonBlank(JsonObject object, String key) {
        return !stringValue(object, key).isBlank();
    }

    static String stringValue(JsonObject object, String key) {
        JsonElement element = scalar(object, key);
        return element == null ? "" : element.getAsString();
    }

    static long longValue(JsonObject object, String key, long fallback) {
        JsonElement element = scalar(object, key);
        if (element == null) {
            return fallback;
        }
        try {
            return Math.round(Double.parseDouble(element.getAsString()));
        } catch (NumberFormatException malformedNumber) {
            return fallback;
        }
    }

    static boolean booleanValue(JsonObject object, String key) {
        JsonElement element = scalar(object, key);
        return element != null && Boolean.parseBoolean(element.getAsString());
    }

    private static JsonElement scalar(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key)) {
            return null;
        }
        JsonElement element = object.get(key);
        return element != null && !element.isJsonNull() && element.isJsonPrimitive() ? element : null;
    }

    static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? safe(second) : first;
    }

    static String url(String value) {
        return URLEncoder.encode(safe(value), StandardCharsets.UTF_8);
    }

    static String safe(String value) {
        return value == null ? "" : value;
    }
}
