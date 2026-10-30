package universalmod.service.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

final class LyricsDiskCache {
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_FILE_BYTES = 2_500_000;
    private static final Path DIRECTORY = Path.of(System.getProperty("user.dir", "."),
            "universalmod", "media", "lyrics");

    private LyricsDiskCache() { }

    static LyricsTimeline.Document read(String key) {
        Path file = fileFor(key);
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE_BYTES) return null;
            try (Reader input = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(input).getAsJsonObject();
                if (root.get("v").getAsInt() != FORMAT_VERSION
                        || !key.equals(root.get("key").getAsString())) return null;
                var type = LyricsTimeline.SyncType.valueOf(root.get("sync").getAsString());
                var source = LyricsTimeline.Source.valueOf(root.get("source").getAsString());
                long duration = root.get("duration").getAsLong();
                String raw = root.get("raw").getAsString();
                if (raw.length() > LyricsTimeline.MAX_DOCUMENT_CHARS) return null;
                JsonArray array = root.getAsJsonArray("lines");
                if (array.size() > LyricsTimeline.MAX_LINES) return null;
                List<LyricsTimeline.Line> lines = new ArrayList<>();
                for (JsonElement entry : array) {
                    JsonObject line = entry.getAsJsonObject();
                    List<LyricsTimeline.Word> words = new ArrayList<>();
                    JsonArray wordJson = line.getAsJsonArray("words");
                    if (wordJson.size() > LyricsTimeline.MAX_WORDS) return null;
                    for (JsonElement w : wordJson) {
                        JsonObject word = w.getAsJsonObject();
                        words.add(new LyricsTimeline.Word(word.get("start").getAsLong(),
                                word.get("end").getAsLong(), word.get("text").getAsString(),
                                word.get("first").getAsInt(), word.get("last").getAsInt()));
                    }
                    String text = line.get("text").getAsString();
                    if (text.length() > 10000) return null;
                    lines.add(new LyricsTimeline.Line(line.get("start").getAsLong(),
                            line.get("end").getAsLong(), text, words));
                }
                return new LyricsTimeline.Document(lines, type, source, raw, duration);
            }
        } catch (Exception ignored) { return null; }
    }

    static void write(String key, LyricsTimeline.Document doc) {
        if (doc == null || doc.isEmpty() || doc.raw().length() > LyricsTimeline.MAX_DOCUMENT_CHARS
                || doc.lines().size() > LyricsTimeline.MAX_LINES) return;
        Path target = fileFor(key);
        Path temp = null;
        try {
            JsonObject root = new JsonObject();
            root.addProperty("v", FORMAT_VERSION);
            root.addProperty("key", key);
            root.addProperty("sync", doc.syncType().name());
            root.addProperty("source", doc.source().name());
            root.addProperty("duration", doc.durationMs());
            root.addProperty("raw", doc.raw());
            JsonArray lines = new JsonArray();
            for (LyricsTimeline.Line line : doc.lines()) {
                JsonObject object = new JsonObject();
                object.addProperty("start", line.startMs());
                object.addProperty("end", line.endMs());
                object.addProperty("text", line.text());
                JsonArray words = new JsonArray();
                for (LyricsTimeline.Word word : line.words()) {
                    JsonObject w = new JsonObject();
                    w.addProperty("start", word.startMs());
                    w.addProperty("end", word.endMs());
                    w.addProperty("text", word.text());
                    w.addProperty("first", word.startChar());
                    w.addProperty("last", word.endChar());
                    words.add(w);
                }
                object.add("words", words);
                lines.add(object);
            }
            root.add("lines", lines);
            byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_FILE_BYTES) return;
            Files.createDirectories(DIRECTORY);
            temp = Files.createTempFile(DIRECTORY, "lyrics-", ".tmp");
            Files.write(temp, bytes);
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) { }
        finally { if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException ignored) { } }
    }

    private static Path fileFor(String key) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return DIRECTORY.resolve(HexFormat.of().formatHex(hash) + ".json");
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
