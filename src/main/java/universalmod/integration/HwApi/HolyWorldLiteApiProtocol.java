package universalmod.integration.HwApi;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;


final class HolyWorldLiteApiProtocol {
    private HolyWorldLiteApiProtocol() {
    }

    static String checkFeaturesRequest(String method, String requestId, String clientId, Collection<String> features) {
        JsonObject root = new JsonObject();
        root.addProperty("method", method);
        root.addProperty("id", requestId);

        JsonObject payload = new JsonObject();
        payload.addProperty("client", clientId);
        JsonArray featureArray = new JsonArray();
        if (features != null) {
            for (String feature : features) {
                String normalized = HolyWorldFeatureId.normalize(feature);
                if (!normalized.isEmpty()) {
                    featureArray.add(normalized);
                }
            }
        }
        payload.add("features", featureArray);
        root.add("payload", payload);
        return root.toString();
    }

    static Optional<JsonObject> parseObject(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonElement parsed = JsonParser.parseString(json);
            return parsed.isJsonObject() ? Optional.of(parsed.getAsJsonObject()) : Optional.empty();
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
    }

    static Optional<Set<String>> parseBlocklist(JsonElement payload) {
        if (payload == null || payload.isJsonNull()) {
            return Optional.empty();
        }

        JsonArray array = null;
        if (payload.isJsonArray()) {
            array = payload.getAsJsonArray();
        } else if (payload.isJsonObject()) {
            JsonObject object = payload.getAsJsonObject();
            array = arrayField(object, "blocklist");
            if (array == null) {
                array = arrayField(object, "features");
            }
            if (array == null) {
                array = arrayField(object, "disabled");
            }
        }

        if (array == null) {
            return Optional.empty();
        }

        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (JsonElement element : array) {
            if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
                continue;
            }
            try {
                String normalized = HolyWorldFeatureId.normalize(element.getAsString());
                if (!normalized.isEmpty()) {
                    result.add(normalized);
                }
            } catch (RuntimeException ignored) {
                
            }
        }
        return Optional.of(Set.copyOf(result));
    }

    static String getString(JsonObject object, String key) {
        if (object == null || key == null) {
            return "";
        }
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return "";
        }
        try {
            return element.getAsString();
        } catch (RuntimeException invalid) {
            return "";
        }
    }

    private static JsonArray arrayField(JsonObject object, String key) {
        if (!object.has(key)) {
            return null;
        }
        JsonElement element = object.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }
}
