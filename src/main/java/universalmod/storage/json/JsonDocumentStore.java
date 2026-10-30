package universalmod.storage.json;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class JsonDocumentStore {
    private final Gson gson;

    public JsonDocumentStore(Gson gson) {
        this.gson = Objects.requireNonNull(gson, "gson");
    }

    public JsonObject readObject(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return parseObject(path);
        } catch (RuntimeException failure) {
            throw new IOException("Invalid JSON document: " + path, failure);
        }
    }

    public synchronized void writeObject(Path path, JsonObject root) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(root, "root");
        Path absolutePath = path.toAbsolutePath().normalize();
        AtomicFileOps.replace(absolutePath, ".write-", temp -> {
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                gson.toJson(root, writer);
                writer.flush();
            }
        });
    }

    private JsonObject parseObject(Path path) throws IOException {

        String json = Files.readString(path, StandardCharsets.UTF_8);
        var element = JsonParser.parseString(json);
        if (element == null || !element.isJsonObject()) {
            throw new IllegalStateException("Root is not a JSON object");
        }
        return element.getAsJsonObject();
    }

}
