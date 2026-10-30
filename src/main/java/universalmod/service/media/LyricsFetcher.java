package universalmod.service.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class LyricsFetcher {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(7);
    private static final String USER_AGENT = "UniversalMod/5.0";
    private static final ConcurrentHashMap<String, LyricsTimeline.Document> CACHE = new ConcurrentHashMap<>();
    private static volatile long retryAfterMillis;
    private static final Pattern BRACKET_SUFFIX = Pattern.compile("(?i)\\s*[\\[(](?:official(?:\\s+(?:video|audio|music video|visualizer))?|lyrics?|lyric video|audio|video|visualizer|hd|4k|explicit)[^\\])]*[\\])]\\s*$");
    private static final Pattern DASH_SUFFIX = Pattern.compile("(?i)\\s+-\\s+(?:youtube|official audio|official video|lyrics?|lyric video|audio|video)\\s*$");
    private static final Pattern SPACE = Pattern.compile("\\s+");

    private LyricsFetcher() {
    }

    public static LyricsTimeline.Document fetch(String artist, String title, long durationSeconds) {
        String rawArtist = safe(artist).trim();
        String rawTitle = safe(title).trim();
        TrackIdentity identity = TrackIdentity.from(rawArtist, rawTitle);
        if (identity.title().isBlank()) {
            return LyricsTimeline.empty();
        }
        String cacheKey = normalize(identity.artist()) + '\u0000' + normalize(identity.title()) + '\u0000' + Math.max(0L, durationSeconds);
        
        String persistentKey = "universalmod-lrclib-v1\u0000" + cacheKey;
        LyricsTimeline.Document cached = CACHE.get(cacheKey);
        if (cached != null && !cached.isEmpty()) return cached;
        cached = LyricsDiskCache.read(persistentKey);
        if (cached != null && !cached.isEmpty()) {
            CACHE.put(cacheKey, cached);
            return cached;
        }
        if (System.currentTimeMillis() < retryAfterMillis) {
            return LyricsTimeline.empty();
        }

        LyricsTimeline.Document exact = fetchExactValues(rawArtist, rawTitle, durationSeconds);
        if (exact.isEmpty() && (!rawArtist.equals(identity.artist()) || !rawTitle.equals(identity.title()))) {
            exact = fetchExact(identity, durationSeconds);
        }
        if (!exact.isEmpty()) {
            CACHE.put(cacheKey, exact);
            LyricsDiskCache.write(persistentKey, exact);
            return exact;
        }

        LyricsTimeline.Document searched = fetchSearch(identity, rawArtist, rawTitle, durationSeconds);
        if (!searched.isEmpty()) {
            CACHE.put(cacheKey, searched);
            LyricsDiskCache.write(persistentKey, searched);
            return searched;
        }
        return LyricsTimeline.empty();
    }

    public static TrackIdentity identify(String artist, String title) {
        return TrackIdentity.from(artist, title);
    }

    private static LyricsTimeline.Document fetchExact(TrackIdentity identity, long durationSeconds) {
        return fetchExactValues(identity.artist(), identity.title(), durationSeconds);
    }

    private static LyricsTimeline.Document fetchExactValues(String artist, String title, long durationSeconds) {
        if (safe(artist).isBlank() || safe(title).isBlank()) {
            return LyricsTimeline.empty();
        }
        StringBuilder uri = new StringBuilder("https://lrclib.net/api/get?track_name=")
                .append(url(title))
                .append("&artist_name=").append(url(artist));
        if (durationSeconds > 0L && durationSeconds <= 3600L) {
            uri.append("&duration=").append(durationSeconds);
        }
        JsonElement response = requestJson(uri.toString());
        if (response == null || !response.isJsonObject()) {
            return LyricsTimeline.empty();
        }
        return parseRecord(response.getAsJsonObject(), durationSeconds);
    }

    private static LyricsTimeline.Document fetchSearch(TrackIdentity identity, String rawArtist, String rawTitle, long durationSeconds) {
        Set<String> urls = new LinkedHashSet<>();
        if (!safe(rawArtist).isBlank() && !safe(rawTitle).isBlank()) {
            urls.add("https://lrclib.net/api/search?track_name=" + url(rawTitle) + "&artist_name=" + url(rawArtist));
        }
        if (!identity.artist().isBlank()) {
            urls.add("https://lrclib.net/api/search?track_name=" + url(identity.title()) + "&artist_name=" + url(identity.artist()));
            urls.add("https://lrclib.net/api/search?q=" + url(identity.artist() + " " + identity.title()));
        }
        urls.add("https://lrclib.net/api/search?q=" + url(identity.title()));
        if (!safe(rawTitle).isBlank() && !rawTitle.equals(identity.title())) {
            urls.add("https://lrclib.net/api/search?q=" + url(rawTitle));
        }

        Candidate best = null;
        for (String uri : urls) {
            JsonElement response = requestJson(uri);
            if (response == null || !response.isJsonArray()) {
                if (System.currentTimeMillis() < retryAfterMillis) {
                    break;
                }
                continue;
            }
            JsonArray records = response.getAsJsonArray();
            for (JsonElement element : records) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject record = element.getAsJsonObject();
                LyricsTimeline.Document document = parseRecord(record, durationSeconds);
                if (document.isEmpty() || !document.hasLineTiming()) {
                    continue;
                }
                double score = candidateScore(identity, durationSeconds, record, document);
                if (score == Double.NEGATIVE_INFINITY) {
                    continue;
                }
                Candidate candidate = new Candidate(document, score);
                if (best == null || candidate.score() > best.score()) {
                    best = candidate;
                }
            }
            if (best != null && best.score() >= 132.0D) {
                break;
            }
        }
        return best == null ? LyricsTimeline.empty() : best.document();
    }

    private static LyricsTimeline.Document parseRecord(JsonObject record, long requestedDurationSeconds) {
        if (record == null || booleanValue(record, "instrumental")) {
            return LyricsTimeline.empty().withSource(LyricsTimeline.Source.LRCLIB);
        }
        long recordDurationSeconds = longValue(record, "duration", requestedDurationSeconds);
        String lyricsfile = stringValue(record, "lyricsfile");
        if (!lyricsfile.isBlank()) {
            LyricsTimeline.Document document = LyricsTimeline.parseLyricsfile(lyricsfile, LyricsTimeline.Source.LRCLIB);
            if (!document.isEmpty() && document.hasLineTiming()) {
                return document;
            }
        }
        String synced = stringValue(record, "syncedLyrics");
        if (!synced.isBlank()) {
            LyricsTimeline.Document document = LyricsTimeline.parseLrc(synced, Math.max(0L, recordDurationSeconds) * 1000L, LyricsTimeline.Source.LRCLIB);
            if (!document.isEmpty() && document.hasLineTiming()) {
                return document;
            }
        }
        String plain = stringValue(record, "plainLyrics");
        if (!plain.isBlank() && recordDurationSeconds > 0L) {
            LyricsTimeline.Document document = LyricsTimeline.estimatePlain(plain, recordDurationSeconds * 1000L, LyricsTimeline.Source.LRCLIB);
            if (!document.isEmpty() && document.hasLineTiming()) {
                return document;
            }
        }
        return LyricsTimeline.empty().withSource(LyricsTimeline.Source.LRCLIB);
    }

    private static double candidateScore(TrackIdentity wanted, long durationSeconds, JsonObject record, LyricsTimeline.Document document) {
        String candidateTitle = cleanTitle(firstNonBlank(stringValue(record, "trackName"), stringValue(record, "name")));
        String candidateArtist = cleanArtist(stringValue(record, "artistName"));
        double titleSimilarity = textSimilarity(wanted.title(), candidateTitle);
        if (titleSimilarity < 0.48D) {
            return Double.NEGATIVE_INFINITY;
        }
        double artistSimilarity = wanted.artist().isBlank() ? 1.0D : textSimilarity(wanted.artist(), candidateArtist);
        if (!wanted.artist().isBlank() && artistSimilarity < 0.24D) {
            return Double.NEGATIVE_INFINITY;
        }
        long candidateDuration = longValue(record, "duration", 0L);
        long durationDelta = durationSeconds > 0L && candidateDuration > 0L ? Math.abs(durationSeconds - candidateDuration) : 0L;
        if (durationSeconds > 0L && candidateDuration > 0L && durationDelta > Math.max(45L, Math.round(durationSeconds * 0.18D))) {
            return Double.NEGATIVE_INFINITY;
        }
        double score = titleSimilarity * 100.0D + artistSimilarity * 38.0D;
        if (durationSeconds > 0L && candidateDuration > 0L) {
            score += Math.max(0.0D, 24.0D - durationDelta * 2.5D);
        }
        if (document.hasWordTiming()) {
            score += 8.0D;
        }
        if (normalize(wanted.title()).equals(normalize(candidateTitle))) {
            score += 20.0D;
        }
        if (!wanted.artist().isBlank() && normalize(wanted.artist()).equals(normalize(candidateArtist))) {
            score += 8.0D;
        }
        return score;
    }

    private static JsonElement requestJson(String uri) {
        try {
            HttpResponse<String> response = CLIENT.send(HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .header("Lrclib-Client", USER_AGENT)
                    .GET()
                    .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 429) {
                retryAfterMillis = retryAt(response);
                return null;
            }
            if (response.statusCode() != 200) {
                return null;
            }
            return JsonParser.parseString(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception exception) {
            return null;
        }
    }

    private static long retryAt(HttpResponse<?> response) {
        long now = System.currentTimeMillis();
        String value = response.headers().firstValue("Retry-After").orElse("").trim();
        try {
            return now + Math.max(1L, Long.parseLong(value)) * 1000L;
        } catch (NumberFormatException ignored) {
            return now + 30_000L;
        }
    }

    private static double textSimilarity(String a, String b) {
        String left = normalize(a);
        String right = normalize(b);
        if (left.equals(right)) {
            return 1.0D;
        }
        if (left.isBlank() || right.isBlank()) {
            return 0.0D;
        }
        double edit = 1.0D - levenshtein(left, right) / (double) Math.max(left.length(), right.length());
        double tokens = tokenSimilarity(left, right);
        double containment = left.contains(right) || right.contains(left)
                ? Math.min(left.length(), right.length()) / (double) Math.max(left.length(), right.length())
                : 0.0D;
        return Math.max(edit, Math.max(tokens, containment));
    }

    private static double tokenSimilarity(String a, String b) {
        Set<String> left = new LinkedHashSet<>(List.of(a.split(" ")));
        Set<String> right = new LinkedHashSet<>(List.of(b.split(" ")));
        left.removeIf(String::isBlank);
        right.removeIf(String::isBlank);
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0D;
        }
        Set<String> intersection = new LinkedHashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new LinkedHashSet<>(left);
        union.addAll(right);
        return intersection.size() / (double) union.size();
    }

    private static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    private static String cleanTitle(String value) {
        String result = safe(value).trim();
        String previous;
        do {
            previous = result;
            result = BRACKET_SUFFIX.matcher(result).replaceFirst("").trim();
            result = DASH_SUFFIX.matcher(result).replaceFirst("").trim();
        } while (!result.equals(previous));
        return result;
    }

    private static String cleanArtist(String value) {
        String result = safe(value).trim();
        int topic = result.toLowerCase(Locale.ROOT).lastIndexOf(" - topic");
        if (topic > 0) {
            result = result.substring(0, topic).trim();
        }
        return result;
    }

    private static String normalize(String value) {
        String lower = safe(value).toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replace('&', ' ')
                .replaceAll("[^\\p{L}\\p{N}]+", " ");
        return SPACE.matcher(lower).replaceAll(" ").trim();
    }

    private static String stringValue(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        try {
            return object.get(key).getAsString();
        } catch (Exception exception) {
            return "";
        }
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return Math.round(object.get(key).getAsDouble());
        } catch (Exception exception) {
            return fallback;
        }
    }

    private static boolean booleanValue(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return false;
        }
        try {
            return object.get(key).getAsBoolean();
        } catch (Exception exception) {
            return false;
        }
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? safe(second) : first;
    }

    private static String url(String value) {
        return URLEncoder.encode(safe(value), StandardCharsets.UTF_8);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public record TrackIdentity(String artist, String title) {
        public TrackIdentity {
            artist = cleanArtist(artist);
            title = cleanTitle(title);
        }

        private static TrackIdentity from(String artist, String title) {
            String cleanArtist = cleanArtist(artist);
            String cleanTitle = cleanTitle(title);
            if (cleanArtist.isBlank()) {
                for (String separator : List.of(" - ", " — ", " – ", " • ")) {
                    int index = cleanTitle.indexOf(separator);
                    if (index > 0 && index + separator.length() < cleanTitle.length()) {
                        String left = cleanTitle.substring(0, index).trim();
                        String right = cleanTitle.substring(index + separator.length()).trim();
                        if (!left.isBlank() && !right.isBlank()) {
                            return new TrackIdentity(left, right);
                        }
                    }
                }
            }
            return new TrackIdentity(cleanArtist, cleanTitle);
        }
    }

    private record Candidate(LyricsTimeline.Document document, double score) {
    }
}
