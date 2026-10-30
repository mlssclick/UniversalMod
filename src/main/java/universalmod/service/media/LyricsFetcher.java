package universalmod.service.media;

import universalmod.service.network.HttpClientService;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class LyricsFetcher {
    private static final long SOURCE_DEADLINE_MS = 18_000L;
    private static final int CACHE_LIMIT = 64;
    private static final ConcurrentHashMap<String, LyricsTimeline.Document> CACHE = new ConcurrentHashMap<>();

    private static final Pattern BRACKET_SUFFIX = Pattern.compile(
            "(?i)\\s*[\\[(](?:official(?:\\s+(?:video|audio|music video|visualizer))?|lyrics?|lyric video|audio|video|visualizer|hd|4k|explicit)[^\\])]*[\\])]\\s*$"
    );
    private static final Pattern DASH_SUFFIX = Pattern.compile(
            "(?i)\\s+-\\s+(?:youtube|official audio|official video|lyrics?|lyric video|audio|video)\\s*$"
    );

    private final LyricsProvider lrclib;

    public LyricsFetcher(HttpClientService http) {
        LyricsHttpGateway gateway = new LyricsHttpGateway(Objects.requireNonNull(http, "http"));
        this.lrclib = new LrclibLyricsProvider(gateway);
    }

    public LyricsTimeline.Document fetch(String artist, String title, long durationSeconds) {
        TrackIdentity identity = TrackIdentity.from(artist, title);
        if (identity.title().isBlank()) {
            return LyricsTimeline.empty();
        }

        long safeDuration = Math.max(0L, durationSeconds);
        String key = "universalmod-lrclib-v1\u0000" + TrackMatcher.normalize(identity.artist()) + '\u0000'
                + TrackMatcher.normalize(identity.title()) + '\u0000' + safeDuration;
        LyricsTimeline.Document cached = CACHE.get(key);
        if (cached != null) return cached;
        cached = LyricsDiskCache.read(key);
        if (cached != null && !cached.isEmpty()) return cacheAndReturn(key, cached, false);

        LyricsTimeline.Document result = lrclib.fetch(identity, safeDuration,
                System.currentTimeMillis() + SOURCE_DEADLINE_MS);
        if (result != null && result.source() == LyricsTimeline.Source.LRCLIB_INSTRUMENTAL)
            return cacheAndReturn(key, result, false);
        if (result != null && !result.isEmpty())
            return cacheAndReturn(key, result, true);
        return LyricsTimeline.empty();
    }

    public static TrackIdentity identify(String artist, String title) {
        return TrackIdentity.from(artist, title);
    }

    private static LyricsTimeline.Document cacheAndReturn(String key, LyricsTimeline.Document document, boolean persist) {
        if (CACHE.size() > CACHE_LIMIT) {
            CACHE.clear();
        }
        LyricsTimeline.Document safe = document == null ? LyricsTimeline.empty() : document;
        CACHE.put(key, safe);
        if (persist) LyricsDiskCache.write(key, safe);
        return safe;
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
                int pipe = cleanTitle.lastIndexOf(" | ");
                if (pipe > 0 && pipe + 3 < cleanTitle.length()) {
                    String titlePart = cleanTitle.substring(0, pipe).trim();
                    String artistPart = cleanTitle.substring(pipe + 3).trim();
                    if (!titlePart.isBlank() && !artistPart.isBlank()) {
                        return new TrackIdentity(artistPart, titlePart);
                    }
                }
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
}
