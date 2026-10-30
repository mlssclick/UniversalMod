package universalmod.service.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class MediaPreferences {
    private static final Path FILE = Path.of(System.getProperty("user.dir", "."),
            "universalmod", "media", "player.json");
    private MediaPreferences() { }

    static long readLyricsOffset() {
        try {
            if (!Files.isRegularFile(FILE) || Files.size(FILE) > 4096) return 0L;
            try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                return Math.max(-3000, Math.min(3000, json.get("lyricsOffsetMs").getAsLong()));
            }
        } catch (IOException | RuntimeException ignored) { return 0L; }
    }

    static void writeLyricsOffset(long offsetMs) {
        Path temp = null;
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject json = new JsonObject();
            json.addProperty("lyricsOffsetMs", Math.max(-3000, Math.min(3000, offsetMs)));
            temp = Files.createTempFile(FILE.getParent(), "player-", ".json.tmp");
            Files.writeString(temp, json.toString(), StandardCharsets.UTF_8);
            try { Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) { }
        finally { if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException ignored) { } }
    }
}
