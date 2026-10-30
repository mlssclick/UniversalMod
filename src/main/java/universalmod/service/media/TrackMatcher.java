package universalmod.service.media;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;


final class TrackMatcher {
    private static final Pattern ARTIST_SPLIT = Pattern.compile(
            "(?iu)\\s*(?:[,;/&+]|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bvs\\.?\\b|\\bwith\\b|\\bx\\b|\\bи\\b)\\s*"
    );

    private TrackMatcher() {
    }

    static JsonObject buildTrackJson(String trackName, String artistName, double durationSeconds) {
        JsonObject result = new JsonObject();
        result.addProperty("trackName", trackName == null ? "" : trackName);
        result.addProperty("artistName", artistName == null ? "" : artistName);
        if (durationSeconds > 0.0D) {
            result.addProperty("duration", durationSeconds);
        }
        return result;
    }

    static String getStringOrEmpty(JsonObject object, String key) {
        return getString(object, key);
    }

    static double scoreTrack(JsonObject candidate, String wantedArtist, String wantedTitle, long wantedDurationSeconds) {
        String wantedTitleNorm = normalize(wantedTitle);
        String candidateTitleNorm = normalize(getString(candidate, "trackName"));
        double titleScore = similarity(wantedTitleNorm, candidateTitleNorm);

        boolean titlePrefix = candidateTitleNorm.equals(wantedTitleNorm)
                || candidateTitleNorm.startsWith(wantedTitleNorm + " ")
                || wantedTitleNorm.startsWith(candidateTitleNorm + " ");
        if (!titlePrefix && titleScore < 0.55D) {
            return Double.NEGATIVE_INFINITY;
        }
        if (candidateTitleNorm.equals(wantedTitleNorm)) {
            titleScore += 1.0D;
        } else if (titlePrefix) {
            titleScore += 0.85D;
        }

        double durationDelta = -1.0D;
        double durationScore = 0.0D;
        if (wantedDurationSeconds > 0L && candidate.has("duration") && !candidate.get("duration").isJsonNull()) {
            Double duration = scalarDouble(candidate.get("duration"));
            if (duration != null && Double.isFinite(duration)) {
                durationDelta = Math.abs(duration - wantedDurationSeconds);
                durationScore = durationDelta <= 2.0D
                        ? 2.0D
                        : Math.max(-1.0D, 1.0D - durationDelta / 15.0D);
            }
        }

        double artistScore = scoreArtist(wantedArtist, getString(candidate, "artistName"));
        if (artistScore < 0.62D && (durationDelta < 0.0D || durationDelta > 2.0D)) {
            return Double.NEGATIVE_INFINITY;
        }

        return titleScore * 1000.0D + artistScore * 900.0D + durationScore * 1200.0D;
    }

    static double scoreArtist(String wanted, String candidate) {
        String left = normalize(wanted);
        String right = normalize(candidate);
        if (left.isEmpty() || right.isEmpty()) {
            return 1.0D;
        }
        if (left.equals(right) || left.contains(right) || right.contains(left)) {
            return 1.0D;
        }

        Set<String> wantedArtists = tokenizeArtists(wanted);
        for (String candidateArtist : tokenizeArtists(candidate)) {
            if (wantedArtists.contains(candidateArtist)) {
                return 1.0D;
            }
        }
        return similarity(left, right);
    }

    static String firstArtist(String artist) {
        if (artist == null || artist.isBlank()) {
            return "";
        }
        String[] parts = ARTIST_SPLIT.split(artist.trim());
        return parts.length == 0 ? artist.trim() : parts[0].trim();
    }

    static double similarity(String a, String b) {
        if (a.equals(b)) {
            return 1.0D;
        }
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0D;
        }
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(previous[j] + 1, current[j - 1] + 1),
                        previous[j - 1] + cost
                );
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return 1.0D - previous[b.length()] / (double) Math.max(a.length(), b.length());
    }

    static String normalize(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("[^\\p{L}\\p{N}]+", "")
                .trim();
        return normalized;
    }

    private static Set<String> tokenizeArtists(String value) {
        Set<String> result = new HashSet<>();
        String[] parts = ARTIST_SPLIT.split(value == null ? "" : value);
        for (String part : parts) {
            String normalized = normalize(part);
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return result;
    }

    static String getString(JsonObject object, String key) {
        if (object == null || !object.has(key)) {
            return "";
        }
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return "";
        }
        return element.isJsonPrimitive() ? element.getAsString() : "";
    }

    private static Double scalarDouble(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        try {
            return Double.parseDouble(element.getAsString());
        } catch (NumberFormatException malformedNumber) {
            return null;
        }
    }
}
