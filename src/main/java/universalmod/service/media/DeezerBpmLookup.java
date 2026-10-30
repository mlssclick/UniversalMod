package universalmod.service.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;


final class DeezerBpmLookup {
    private DeezerBpmLookup() {
    }

    private static JsonObject toTrackJson(JsonObject result) {
        JsonObject artist = result.has("artist") && result.get("artist").isJsonObject()
                ? result.getAsJsonObject("artist")
                : null;
        double duration = result.has("duration") && !result.get("duration").isJsonNull()
                ? result.get("duration").getAsDouble()
                : 0.0D;
        return TrackMatcher.buildTrackJson(
                TrackMatcher.getStringOrEmpty(result, "title"),
                TrackMatcher.getStringOrEmpty(artist, "name"),
                duration
        );
    }

    private static float fetchBpmById(long trackId) {
        try {
            String response = httpGet("https://api.deezer.com/track/" + trackId);
            if (response == null) {
                return 0.0F;
            }
            JsonObject root = JsonParser.parseString(response).getAsJsonObject();
            if (!root.has("bpm") || root.get("bpm").isJsonNull()) {
                return 0.0F;
            }
            float value = root.get("bpm").getAsFloat();
            return value >= 40.0F && value <= 250.0F ? value : 0.0F;
        } catch (RuntimeException malformedResponse) {
            return 0.0F;
        }
    }

    static float findBpm(String artist, String title) {
        return findBpmNear(artist, title, 0L);
    }

    static float findBpmNear(String artist, String title, long durationSeconds) {
        if (artist == null || title == null || artist.isBlank() || title.isBlank()) {
            return 0.0F;
        }

        long trackId = searchTrackId(
                "artist:\"" + artist + "\" track:\"" + title + "\"",
                artist,
                title,
                durationSeconds
        );
        if (trackId == 0L) {
            trackId = searchTrackId(artist + " " + title, artist, title, durationSeconds);
        }
        if (trackId == 0L) {
            String firstArtist = TrackMatcher.firstArtist(artist);
            if (!firstArtist.isBlank() && !firstArtist.equalsIgnoreCase(artist.trim())) {
                trackId = searchTrackId(firstArtist + " " + title, artist, title, durationSeconds);
            }
        }
        return trackId == 0L ? 0.0F : fetchBpmById(trackId);
    }

    private static long searchTrackId(String query, String wantedArtist, String wantedTitle, long durationSeconds) {
        try {
            String response = httpGet("https://api.deezer.com/search?limit=10&q=" + urlEncode(query));
            if (response == null) {
                return 0L;
            }
            JsonArray data = JsonParser.parseString(response).getAsJsonObject().getAsJsonArray("data");
            if (data == null || data.isEmpty()) {
                return 0L;
            }

            long bestId = 0L;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (JsonElement element : data) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject candidate = element.getAsJsonObject();
                if (!candidate.has("id") || candidate.get("id").isJsonNull()) {
                    continue;
                }
                double score = TrackMatcher.scoreTrack(
                        toTrackJson(candidate),
                        wantedArtist,
                        wantedTitle,
                        durationSeconds
                );
                if (score > bestScore) {
                    bestScore = score;
                    bestId = candidate.get("id").getAsLong();
                }
            }
            return bestId;
        } catch (RuntimeException malformedResponse) {
            return 0L;
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }


    private static String httpGet(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(6_000);
            connection.setInstanceFollowRedirects(true);

            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (stream == null) {
                return null;
            }
            try (InputStream input = stream) {
                byte[] bytes = input.readAllBytes();
                if (status != 200) {
                    return null;
                }
                return new String(bytes, resolveCharset(connection));
            }
        } catch (IOException | RuntimeException requestFailure) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static Charset resolveCharset(HttpURLConnection connection) {
        String contentType = connection.getContentType();
        if (contentType == null) {
            return StandardCharsets.UTF_8;
        }
        for (String part : contentType.split(";")) {
            String trimmed = part.trim();
            if (!trimmed.regionMatches(true, 0, "charset=", 0, 8)) {
                continue;
            }
            String charset = trimmed.substring(8).replace("\"", "").trim();
            try {
                return Charset.forName(charset);
            } catch (IllegalArgumentException invalidCharset) {
                return StandardCharsets.UTF_8;
            }
        }
        return StandardCharsets.UTF_8;
    }
}
