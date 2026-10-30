package universalmod.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import universalmod.config.ConfigManager;
import universalmod.storage.json.JsonDocumentStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


public final class RepositoryStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final JsonDocumentStore STORE = new JsonDocumentStore(GSON);

    private RepositoryStorage() {
    }

    public static Path root() {
        return ConfigManager.systemDirectory();
    }

    public static List<String> loadStringList(String fileName, String key) {
        List<String> result = new ArrayList<>();
        JsonObject object = readObject(fileName);
        JsonArray array = object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
        for (JsonElement element : array) {
            if (element.isJsonPrimitive()) {
                result.add(element.getAsString());
            }
        }
        return result;
    }

    public static void saveStringList(String fileName, String key, List<String> values) {
        JsonObject object = new JsonObject();
        object.addProperty("version", 1);
        JsonArray array = new JsonArray();
        if (values != null) {
            for (String value : values) {
                array.add(value);
            }
        }
        object.add(key, array);
        write(fileName, object);
    }

    public static JsonObject readObject(String fileName) {
        Path file = resolveForRead(fileName);
        if (!Files.exists(file)) {
            return new JsonObject();
        }
        try {
            JsonObject object = STORE.readObject(file);
            return object == null ? new JsonObject() : object;
        } catch (IOException | RuntimeException exception) {
            return new JsonObject();
        }
    }

    public static void write(String fileName, JsonObject object) {
        Path file = resolveForWrite(fileName);
        try {
            STORE.writeObject(file, object == null ? new JsonObject() : object);
        } catch (IOException | RuntimeException exception) {
            
        }
    }

    public static String universalmodFile(String baseName) {
        String normalized = baseName == null ? "" : baseName.trim();
        if (normalized.endsWith(ConfigManager.CONFIG_EXTENSION)) {
            return normalized;
        }
        int dot = normalized.lastIndexOf('.');
        if (dot >= 0) {
            normalized = normalized.substring(0, dot);
        }
        return normalized + ConfigManager.CONFIG_EXTENSION;
    }

    private static Path resolveForWrite(String fileName) {
        return root().resolve(universalmodFile(fileName));
    }

    private static Path resolveForRead(String fileName) {
        return resolveForWrite(fileName);
    }
}
