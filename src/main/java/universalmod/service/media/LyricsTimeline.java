package universalmod.service.media;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LyricsTimeline {
    static final int MAX_DOCUMENT_CHARS = 2_000_000;
    static final int MAX_LINES = 5_000;
    static final int MAX_WORDS = 50_000;

    private LyricsTimeline() {
    }

    public static Document empty() {
        return new Document(List.of(), SyncType.PLAIN, Source.NONE, "", 0L);
    }

    public static Document parseLrc(String lyrics, long durationMs, Source source) {
        return LrcLyricsParser.parse(lyrics, durationMs, source);
    }

    public static Document parseLyricsfile(String yaml, Source source) {
        return LyricsFileYamlParser.parse(yaml, source);
    }

    public static Document parseTtml(String ttml, Source source) {
        return TtmlLyricsParser.parse(ttml, source);
    }

    public static Document plain(String lyrics, Source source) {
        String normalized = normalize(lyrics);
        if (normalized.isBlank()) {
            return empty().withSource(source);
        }
        List<String> lines = normalized.lines().map(String::trim).filter(s -> !s.isBlank()).limit(MAX_LINES).toList();
        return plainDocument(lines, source, normalized, 0L);
    }

    public static Document estimatePlain(String lyrics, long durationMs, Source source) {
        String normalized = normalize(lyrics);
        if (normalized.isBlank() || durationMs <= 0L) {
            return plain(lyrics, source);
        }
        List<String> textLines = normalized.lines().map(String::trim).filter(line -> !line.isBlank()).limit(MAX_LINES).toList();
        if (textLines.isEmpty()) {
            return empty().withSource(source);
        }
        long introMs = Math.min(5_000L, Math.max(900L, Math.round(durationMs * 0.025D)));
        long outroMs = Math.min(3_500L, Math.max(700L, Math.round(durationMs * 0.018D)));
        long usable = Math.max(textLines.size() * 500L, durationMs - introMs - outroMs);
        double totalWeight = 0.0D;
        double[] weights = new double[textLines.size()];
        for (int i = 0; i < textLines.size(); i++) {
            weights[i] = lineWeight(textLines.get(i));
            totalWeight += weights[i];
        }
        List<Line> lines = new ArrayList<>(textLines.size());
        long cursor = introMs;
        for (int i = 0; i < textLines.size(); i++) {
            long remaining = Math.max(1L, durationMs - outroMs - cursor);
            long span = i == textLines.size() - 1
                    ? remaining
                    : Math.max(500L, Math.round(usable * (weights[i] / Math.max(0.001D, totalWeight))));
            long end = Math.min(durationMs, cursor + span);
            String text = textLines.get(i);
            lines.add(new Line(cursor, Math.max(cursor + 1L, end), text, estimateWords(text, cursor, Math.max(cursor + 1L, end))));
            cursor = end;
        }
        return new Document(List.copyOf(lines), SyncType.ESTIMATED_WORD, source, normalized, durationMs);
    }

    public static int activeIndexMillis(List<Line> lines, long positionMs) {
        if (lines == null || lines.isEmpty()) {
            return -1;
        }
        long position = Math.max(0L, positionMs);
        int low = 0;
        int high = lines.size() - 1;
        int candidate = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (lines.get(mid).startMs() <= position) {
                candidate = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return candidate;
    }

    public static List<Integer> activeIndicesMillis(List<Line> lines, long positionMs) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        long position = Math.max(0L, positionMs);
        List<Integer> active = new ArrayList<>();
        int lastStarted = activeIndexMillis(lines, position);
        if (lastStarted < 0) {
            return List.of();
        }
        for (int i = lastStarted; i >= 0; i--) {
            Line line = lines.get(i);
            if (line.endMs() <= position && line.startMs() < position) {
                if (i < lastStarted - 8) {
                    break;
                }
                continue;
            }
            if (line.startMs() <= position && position < line.endMs()) {
                active.add(0, i);
            }
        }
        return List.copyOf(active);
    }

    public static int activeWordIndex(Line line, long positionMs) {
        if (line == null || line.words().isEmpty()) {
            return -1;
        }
        long position = Math.max(0L, positionMs);
        List<Word> words = line.words();
        int low = 0;
        int high = words.size() - 1;
        int candidate = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (words.get(mid).startMs() <= position) {
                candidate = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        if (candidate < 0) {
            return -1;
        }
        Word word = words.get(candidate);
        return position < word.endMs() ? candidate : -1;
    }

    public static float wordProgress(Word word, long positionMs) {
        if (word == null) {
            return 0.0F;
        }
        long span = Math.max(1L, word.endMs() - word.startMs());
        double value = (positionMs - word.startMs()) / (double) span;
        return (float) Math.max(0.0D, Math.min(1.0D, value));
    }

    public static boolean isLineActive(Line line, long positionMs) {
        return line != null && positionMs >= line.startMs() && positionMs < line.endMs();
    }


    public static int activeLineIndex(Document document, long positionMs, long trackDurationMs, long graceMs) {
        if (document == null || document.isEmpty() || !document.hasLineTiming()) {
            return -1;
        }
        List<Line> lines = document.lines();
        int index = activeIndexMillis(lines, positionMs);
        if (index < 0 || index >= lines.size()) {
            return -1;
        }
        Line line = lines.get(index);
        if (punctuationOnly(line.text())) {
            return -1;
        }

        long lineEnd = effectiveLineEnd(lines, index, trackDurationMs);
        if (positionMs <= lineEnd) {
            return index;
        }
        long nextStart = nextLineStart(lines, index);
        if (nextStart != Long.MAX_VALUE && nextStart - lineEnd <= Math.max(0L, graceMs) * 2L) {
            return index;
        }
        return positionMs - lineEnd <= Math.max(0L, graceMs) ? index : -1;
    }


    public static float karaokeProgress(Line line, long positionMs) {
        if (line == null || line.text().isEmpty()) {
            return 0.0F;
        }
        int codePoints = line.text().codePointCount(0, line.text().length());
        if (codePoints <= 0) {
            return 0.0F;
        }
        if (positionMs <= line.startMs()) {
            return 0.0F;
        }
        if (positionMs >= line.endMs()) {
            return 1.0F;
        }

        if (line.words().isEmpty()) {
            long span = Math.max(1L, line.endMs() - line.startMs());
            return clamp01((positionMs - line.startMs()) / (float) span);
        }

        long previousTime = line.startMs();
        int previousCp = 0;
        for (Word word : line.words()) {
            int wordStartCp = line.text().codePointCount(0, clampIndex(word.startChar(), line.text().length()));
            int wordEndCp = line.text().codePointCount(0, clampIndex(word.endChar(), line.text().length()));
            long markerTime = Math.max(line.startMs(), word.startMs());
            if (markerTime > positionMs) {
                float t = markerTime <= previousTime ? 0.0F
                        : clamp01((positionMs - previousTime) / (float) (markerTime - previousTime));
                return clamp01((previousCp + (wordStartCp - previousCp) * t) / codePoints);
            }
            previousTime = markerTime;
            previousCp = Math.max(previousCp, wordStartCp);

            if (positionMs < word.endMs()) {
                float t = wordProgress(word, positionMs);
                return clamp01((wordStartCp + (wordEndCp - wordStartCp) * t) / codePoints);
            }
            previousTime = Math.max(previousTime, word.endMs());
            previousCp = Math.max(previousCp, wordEndCp);
        }

        long end = Math.max(previousTime + 1L, line.endMs());
        float t = clamp01((positionMs - previousTime) / (float) Math.max(1L, end - previousTime));
        return clamp01((previousCp + (codePoints - previousCp) * t) / codePoints);
    }

    public static float lineProgress(Line line, long positionMs) {
        return karaokeProgress(line, positionMs);
    }

    public static float revealedCodePoints(Line line, long positionMs) {
        if (line == null || line.text().isEmpty()) {
            return 0.0F;
        }
        int codePoints = line.text().codePointCount(0, line.text().length());
        return karaokeProgress(line, positionMs) * codePoints;
    }

    private static long effectiveLineEnd(List<Line> lines, int index, long trackDurationMs) {
        Line line = lines.get(index);
        long next = nextLineStart(lines, index);
        long end = line.endMs();
        if (next != Long.MAX_VALUE) {
            end = end > line.startMs() ? Math.min(end, next) : next;
        } else if (end <= line.startMs()) {
            long natural = Math.max(1_500L, Math.min(8_000L,
                    (long) line.text().codePointCount(0, line.text().length()) * 150L));
            end = line.startMs() + natural;
        }
        if (trackDurationMs > line.startMs()) {
            end = Math.min(end, trackDurationMs);
        }
        return Math.max(line.startMs() + 1L, end);
    }

    private static long nextLineStart(List<Line> lines, int index) {
        long current = lines.get(index).startMs();
        for (int i = index + 1; i < lines.size(); i++) {
            if (lines.get(i).startMs() > current) {
                return lines.get(i).startMs();
            }
        }
        return Long.MAX_VALUE;
    }

    private static boolean punctuationOnly(String text) {
        if (text == null || text.isEmpty()) {
            return true;
        }
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetterOrDigit(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static int clampIndex(int value, int length) {
        return Math.max(0, Math.min(length, value));
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    static List<Word> estimateWords(String text, long lineStartMs, long lineEndMs) {
        String value = text == null ? "" : text;
        long vocalEndMs = estimatedVocalEnd(value, lineStartMs, lineEndMs);
        long span = Math.max(1L, vocalEndMs - lineStartMs);
        Matcher matcher = Pattern.compile("\\S+").matcher(value);
        List<EstimatedToken> tokens = new ArrayList<>();
        double totalWeight = 0.0D;
        while (matcher.find() && tokens.size() < MAX_WORDS) {
            String token = matcher.group();
            double weight = wordWeight(token);
            tokens.add(new EstimatedToken(token, matcher.start(), matcher.end(), weight));
            totalWeight += weight;
        }
        if (tokens.isEmpty()) {
            return List.of();
        }
        List<Word> words = new ArrayList<>(tokens.size());
        double cursor = lineStartMs;
        for (int i = 0; i < tokens.size(); i++) {
            EstimatedToken token = tokens.get(i);
            long start = Math.max(lineStartMs, Math.round(cursor));
            long end;
            if (i == tokens.size() - 1) {
                end = vocalEndMs;
            } else {
                double portion = token.weight() / Math.max(0.001D, totalWeight);
                cursor += span * portion;
                end = Math.round(cursor);
            }
            end = Math.max(start + 1L, Math.min(vocalEndMs, end));
            words.add(new Word(start, end, token.text(), token.startChar(), token.endChar()));
        }
        return List.copyOf(words);
    }

    private static long estimatedVocalEnd(String text, long lineStartMs, long lineEndMs) {
        long availableSpan = Math.max(1L, lineEndMs - lineStartMs);
        double weight = lineWeight(text);
        long naturalSpan = Math.round(650.0D + weight * 175.0D);
        naturalSpan = Math.max(900L, Math.min(5_200L, naturalSpan));

        long vocalSpan = availableSpan;
        if (availableSpan > naturalSpan + 1_250L) {
            vocalSpan = naturalSpan;
        }
        return lineStartMs + Math.max(1L, Math.min(availableSpan, vocalSpan));
    }

    private static double lineWeight(String text) {
        Matcher matcher = Pattern.compile("\\S+").matcher(text == null ? "" : text);
        double weight = 0.0D;
        while (matcher.find()) {
            weight += wordWeight(matcher.group());
        }
        return Math.max(1.0D, weight);
    }

    private static double wordWeight(String word) {
        String value = word == null ? "" : word;
        int letters = 0;
        int vowelGroups = 0;
        boolean inVowel = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = Character.toLowerCase(value.charAt(i));
            boolean letter = Character.isLetterOrDigit(ch);
            if (letter) {
                letters++;
            }
            boolean vowel = "аеёиоуыэюяaeiouyіїє".indexOf(ch) >= 0;
            if (vowel && !inVowel) {
                vowelGroups++;
            }
            inVowel = vowel;
        }
        double weight = Math.max(1.0D, letters * 0.42D + vowelGroups * 0.78D);
        if (value.endsWith(",") || value.endsWith(";") || value.endsWith(":")) {
            weight += 0.75D;
        } else if (value.endsWith(".") || value.endsWith("!") || value.endsWith("?") || value.endsWith("…")) {
            weight += 1.35D;
        } else if (value.endsWith("-") || value.endsWith("—")) {
            weight += 0.55D;
        }
        return weight;
    }


    static List<Line> inferMissingLineEnds(List<Line> input, long durationMs) {
        if (input.isEmpty()) {
            return List.of();
        }
        List<Line> output = new ArrayList<>(input.size());
        for (int i = 0; i < input.size(); i++) {
            Line line = input.get(i);
            long end = line.endMs();
            if (end <= line.startMs()) {
                long next = i + 1 < input.size() ? input.get(i + 1).startMs() : durationMs;
                end = resolveLineEnd(line.startMs(), next, durationMs);
            }
            List<Word> words = line.words();
            if (!words.isEmpty()) {
                List<Word> adjusted = new ArrayList<>(words.size());
                for (int j = 0; j < words.size(); j++) {
                    Word word = words.get(j);
                    long wordEnd = word.endMs() > word.startMs() ? word.endMs()
                            : j + 1 < words.size() ? words.get(j + 1).startMs() : end;
                    adjusted.add(new Word(word.startMs(), Math.max(word.startMs() + 1L, wordEnd), word.text(), word.startChar(), word.endChar()));
                }
                words = List.copyOf(adjusted);
            }
            output.add(new Line(line.startMs(), end, line.text(), words));
        }
        return output;
    }

    static Document plainDocument(List<String> plain, Source source, String raw, long durationMs) {
        List<Line> lines = new ArrayList<>();
        for (String value : plain) {
            String text = value == null ? "" : value.trim();
            if (!text.isBlank()) {
                lines.add(new Line(-1L, -1L, text, List.of()));
            }
        }
        return new Document(List.copyOf(lines), SyncType.PLAIN, source, raw == null ? "" : raw, durationMs);
    }


    static long resolveLineEnd(long startMs, long nextStartMs, long durationMs) {
        if (nextStartMs > startMs) {
            return nextStartMs;
        }
        if (durationMs > startMs) {
            return durationMs;
        }
        return startMs + 4_000L;
    }


    static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        return normalized.length() > MAX_DOCUMENT_CHARS ? normalized.substring(0, MAX_DOCUMENT_CHARS) : normalized.trim();
    }

    public enum SyncType {
        WORD,
        ESTIMATED_WORD,
        LINE,
        PLAIN
    }

    public enum Source {
        NONE,
        LRCLIB,
        LRCLIB_INSTRUMENTAL,
        NETEASE,
        GENIUS
    }

    public record Document(List<Line> lines, SyncType syncType, Source source, String raw, long durationMs) {
        public Document {
            lines = lines == null ? List.of() : List.copyOf(lines);
            syncType = syncType == null ? SyncType.PLAIN : syncType;
            source = source == null ? Source.NONE : source;
            raw = raw == null ? "" : raw;
        }

        public boolean isEmpty() {
            return lines.isEmpty();
        }

        public boolean hasWordTiming() {
            return (syncType == SyncType.WORD || syncType == SyncType.ESTIMATED_WORD) && lines.stream().anyMatch(line -> !line.words().isEmpty());
        }

        public boolean hasRealWordTiming() {
            return syncType == SyncType.WORD && lines.stream().anyMatch(line -> !line.words().isEmpty());
        }

        public boolean hasEstimatedWordTiming() {
            return syncType == SyncType.ESTIMATED_WORD && lines.stream().anyMatch(line -> !line.words().isEmpty());
        }

        public boolean hasLineTiming() {
            return syncType != SyncType.PLAIN;
        }

        public String plainText() {
            return lines.stream().map(Line::text).filter(text -> !text.isBlank()).reduce((a, b) -> a + "\n" + b).orElse("");
        }

        public Document withSource(Source newSource) {
            return new Document(lines, syncType, newSource, raw, durationMs);
        }
    }

    public record Line(long startMs, long endMs, String text, List<Word> words) {
        public Line {
            text = text == null ? "" : text;
            words = words == null ? List.of() : List.copyOf(words);
        }
    }

    public record Word(long startMs, long endMs, String text, int startChar, int endChar) {
        public Word {
            text = text == null ? "" : text;
        }
    }


    private record EstimatedToken(String text, int startChar, int endChar, double weight) {
    }


}
