package universalmod.service.media;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static universalmod.service.media.LyricsProviderSupport.*;

final class LrclibLyricsProvider implements LyricsProvider {
    private static final String SOURCE = "lrclib";
    private static final String AGENT = "UniversalMod/1.21.11 (https://github.com/mlssclick/UniversalMod)";
    private final LyricsHttpGateway http;

    LrclibLyricsProvider(LyricsHttpGateway http) { this.http = http; }

    @Override
    public LyricsTimeline.Document fetch(LyricsFetcher.TrackIdentity identity, long durationSeconds, long deadline) {
        String artist = normalizeArtist(identity.artist());
        String title = cleanTitle(identity.title());
        if (title.isBlank() || http.coolingDown(SOURCE)) return null;

        String exact = "https://lrclib.net/api/get?track_name=" + url(title)
                + "&artist_name=" + url(artist)
                + (durationSeconds > 0 ? "&duration=" + durationSeconds : "");
        JsonElement exactJson = request(exact, deadline);
        JsonObject selected = exactJson != null && exactJson.isJsonObject()
                && exactJson.getAsJsonObject().has("id") ? exactJson.getAsJsonObject() : null;
        if (selected != null && hasNonBlank(selected, "syncedLyrics")) {
            return parse(selected, durationSeconds);
        }
        if (selected != null && booleanValue(selected, "instrumental")) {
            return LyricsTimeline.empty().withSource(LyricsTimeline.Source.LRCLIB_INSTRUMENTAL);
        }

        String firstArtist = firstArtist(artist);
        List<String> searches = new ArrayList<>();
        searches.add("track_name=" + url(title) + "&artist_name=" + url(artist));
        searches.add("q=" + url(title + " " + artist));
        if (!firstArtist.equalsIgnoreCase(artist)) {
            searches.add("q=" + url(firstArtist + " " + artist));
            searches.add("q=" + url(firstArtist + " " + firstArtist(artist)));
            searches.add("q=" + url(firstArtist));
        }
        searches.add("track_name=" + url(title));
        for (String query : searches) {
            if (Thread.currentThread().isInterrupted() || LyricsHttpGateway.deadlineExceeded(deadline)) break;
            JsonElement searchResult = request("https://lrclib.net/api/search?" + query, deadline);
            JsonObject candidate = chooseBestMatch(searchResult, artist, title, durationSeconds);
            if (candidate == null) continue;
            if (selected == null || hasNonBlank(candidate, "syncedLyrics")) selected = candidate;
            if (hasNonBlank(candidate, "syncedLyrics")) break;
        }
        return selected == null ? null : parse(selected, durationSeconds);
    }

    private JsonElement request(String uri, long deadline) {
        return http.requestJson(SOURCE, uri, deadline,
                "Accept", "application/json", "User-Agent", AGENT);
    }

    private static JsonObject chooseBestMatch(JsonElement response,
                                                 String artist, String title, long duration) {
        if (response == null || !response.isJsonArray()) return null;
        String normalizedTitle = normalized(title);
        String normalizedArtist = normalized(firstArtist(artist));
        double bestScore = Double.POSITIVE_INFINITY;
        JsonObject best = null;
        for (JsonElement item : response.getAsJsonArray()) {
            if (item == null || !item.isJsonObject()) continue;
            JsonObject candidate = item.getAsJsonObject();
            String candidateTitle = normalized(stringValue(candidate, "trackName"));
            String candidateArtist = normalized(firstArtist(stringValue(candidate, "artistName")));
            boolean titleMatches = !normalizedTitle.isEmpty()
                    && (candidateTitle.equals(normalizedTitle)
                    || candidateTitle.contains(normalizedTitle)
                    || (candidateTitle.length() > 3 && normalizedTitle.contains(candidateTitle)));
            if (!titleMatches) continue;
            double candidateDuration = numberValue(candidate, "duration");
            boolean artistMatches = !normalizedArtist.isEmpty()
                    && (candidateArtist.contains(normalizedArtist)
                    || normalizedArtist.contains(candidateArtist)
                    || candidateTitle.contains(normalizedArtist));
            boolean closeDuration = duration > 0 && candidateDuration > 0
                    && Math.abs(candidateDuration - duration) <= 3;
            if (!artistMatches && !closeDuration) continue;
            double score = duration > 0 && candidateDuration > 0
                    ? Math.abs(candidateDuration - duration) : 20;
            if (hasNonBlank(candidate, "syncedLyrics")) score -= 1000;
            if (artistMatches) score -= 50;
            if (score < bestScore) {bestScore = score; best = candidate;}
        }
        return best;
    }

    private static String normalized(String value) {
        StringBuilder out = new StringBuilder();
        for (char c : safe(value).toLowerCase(Locale.ROOT).toCharArray())
            if (Character.isLetterOrDigit(c)) out.append(c);
        return out.toString();
    }

    private static String normalizeArtist(String value) { return safe(value).trim(); }
    private static String firstArtist(String value) {
        String artist = safe(value).trim();
        for (String separator : new String[]{",", " & ", " feat. ", " ft. ", " x ", " × ", " and ", ";"}) {
            int index = artist.toLowerCase(Locale.ROOT).indexOf(separator.trim().equals("&") ? " & " : separator);
            if (index > 0) artist = artist.substring(0, index);
        }
        return artist.trim();
    }
    private static String cleanTitle(String value) {return safe(value).trim();}
    private static double numberValue(JsonObject candidate, String key) {
        try { return candidate.has(key) && !candidate.get(key).isJsonNull()
                ? candidate.get(key).getAsDouble() : 0D; }
        catch (RuntimeException exception) { return 0D; }
    }

    private static LyricsTimeline.Document parse(JsonObject record, long seconds) {
        if (booleanValue(record, "instrumental"))
            return LyricsTimeline.empty().withSource(LyricsTimeline.Source.LRCLIB_INSTRUMENTAL);
        double length = numberValue(record, "duration");
        long durationMs = (long) Math.max(0, Math.round(length > 0 ? length * 1000D : seconds * 1000D));
        String synced = stringValue(record, "syncedLyrics");
        if (!synced.isBlank()) {
            LyricsTimeline.Document result = LyricsTimeline.parseLrc(synced, durationMs, LyricsTimeline.Source.LRCLIB);
            if (!result.isEmpty() && result.hasLineTiming()) return result;
        }
        String plain = stringValue(record, "plainLyrics");
        if (!plain.isBlank()) return LyricsTimeline.plain(plain, LyricsTimeline.Source.LRCLIB);
        return null;
    }
}
