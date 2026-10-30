package universalmod.service.media;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static universalmod.service.media.LyricsTimeline.Document;
import static universalmod.service.media.LyricsTimeline.Line;
import static universalmod.service.media.LyricsTimeline.Source;
import static universalmod.service.media.LyricsTimeline.SyncType;
import static universalmod.service.media.LyricsTimeline.Word;

final class LrcLyricsParser {
    private static final Pattern LINE_TIMESTAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern WORD_TIMESTAMP = Pattern.compile("<(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?>");
    private static final Pattern OFFSET = Pattern.compile("(?im)^\\[offset\\s*:\\s*([+-]?\\d+)\\]\\s*$");
    private static final Pattern METADATA = Pattern.compile("(?i)^\\[(?:ar|ti|al|by|re|ve|length|offset):.*]$");

    private LrcLyricsParser() {
    }

    static Document parse(String lyrics, long durationMs, Source source) {
        String normalized = LyricsTimeline.normalize(lyrics);
        if (normalized.isBlank()) {
            return LyricsTimeline.empty().withSource(source);
        }
        long offsetMs = extractOffsetMillis(normalized);
        List<RawLrcLine> timed = new ArrayList<>();
        List<String> plain = new ArrayList<>();
        for (String rawLine : normalized.lines().toList()) {
            if (timed.size() >= LyricsTimeline.MAX_LINES) {
                break;
            }
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isBlank() || METADATA.matcher(line).matches()) {
                continue;
            }
            Matcher lineMatcher = LINE_TIMESTAMP.matcher(line);
            List<Long> timestamps = new ArrayList<>();
            while (lineMatcher.find()) {
                timestamps.add(Math.max(0L, toMillis(lineMatcher) + offsetMs));
            }
            String payload = lineMatcher.replaceAll("").trim();
            String text = WORD_TIMESTAMP.matcher(payload).replaceAll("").trim();

            if (timestamps.isEmpty()) {
                if (text.isBlank()) continue;
                plain.add(text);
                continue;
            }
            for (long timestamp : timestamps) {
                timed.add(new RawLrcLine(timestamp, payload, text, offsetMs));
            }
        }
        if (timed.isEmpty()) {
            return LyricsTimeline.plainDocument(plain, source, normalized, durationMs);
        }
        timed.sort(Comparator.comparingLong(RawLrcLine::startMs));
        List<Line> lines = new ArrayList<>(timed.size());
        boolean hasWords = false;
        for (int i = 0; i < timed.size(); i++) {
            RawLrcLine raw = timed.get(i);
            long nextStart = i + 1 < timed.size() ? timed.get(i + 1).startMs() : Long.MAX_VALUE;
            long endMs = nextStart != Long.MAX_VALUE
                    ? Math.max(raw.startMs() + 1L, nextStart)
                    : lastLineEnd(raw.startMs(), raw.text(), durationMs);
            ParsedSegments segments = parseEnhancedSegments(raw.payload(), raw.offsetMs(), endMs);
            if (!segments.words().isEmpty()) {
                hasWords = true;
                lines.add(new Line(raw.startMs(), endMs, segments.text(), segments.words()));
            } else {
                lines.add(new Line(raw.startMs(), endMs, raw.text(), List.of()));
            }
        }
        return new Document(List.copyOf(lines), hasWords ? SyncType.WORD : SyncType.LINE, source, normalized, durationMs);
    }

    private static ParsedSegments parseEnhancedSegments(String payload, long offsetMs, long lineEndMs) {
        String value = payload == null ? "" : payload;
        Matcher matcher = WORD_TIMESTAMP.matcher(value);
        List<Long> starts = new ArrayList<>();
        List<Integer> ends = new ArrayList<>();
        while (matcher.find() && starts.size() < LyricsTimeline.MAX_WORDS) {
            starts.add(Math.max(0L, toMillis(matcher) + offsetMs));
            ends.add(matcher.end());
        }
        if (starts.isEmpty()) {
            return new ParsedSegments(WORD_TIMESTAMP.matcher(value).replaceAll("").trim(), List.of());
        }
        List<SegmentDraft> drafts = new ArrayList<>(starts.size());
        for (int i = 0; i < starts.size(); i++) {
            int textStart = ends.get(i);
            int textEnd;
            if (i + 1 < starts.size()) {
                Matcher next = WORD_TIMESTAMP.matcher(value);
                next.region(textStart, value.length());
                textEnd = next.find() ? next.start() : value.length();
            } else {
                textEnd = value.length();
            }
            if (textEnd < textStart) {
                continue;
            }
            String segment = value.substring(textStart, textEnd);
            if (!segment.isEmpty()) {
                drafts.add(new SegmentDraft(starts.get(i), segment));
            }
        }
        if (drafts.isEmpty()) {
            return new ParsedSegments(WORD_TIMESTAMP.matcher(value).replaceAll("").trim(), List.of());
        }
        StringBuilder text = new StringBuilder();
        List<Word> words = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            SegmentDraft draft = drafts.get(i);
            int startChar = text.length();
            text.append(draft.text());
            int endChar = text.length();
            long end = i + 1 < drafts.size() ? drafts.get(i + 1).startMs() : lineEndMs;
            words.add(new Word(draft.startMs(), Math.max(draft.startMs() + 1L, end), draft.text(), startChar, endChar));
        }
        return new ParsedSegments(text.toString().stripTrailing(), List.copyOf(words));
    }

    private static long lastLineEnd(long startMs, String text, long durationMs) {
        int codePoints = text == null ? 0 : text.codePointCount(0, text.length());
        long natural = Math.max(1_500L, Math.min(8_000L, Math.max(1, codePoints) * 150L));
        long naturalEnd = startMs + natural;
        if (durationMs > startMs) {
            return Math.max(startMs + 1L, Math.min(durationMs, naturalEnd));
        }
        return naturalEnd;
    }

    private static long extractOffsetMillis(String lyrics) {
        Matcher matcher = OFFSET.matcher(lyrics == null ? "" : lyrics);
        if (!matcher.find()) {
            return 0L;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private static long toMillis(Matcher matcher) {
        long minutes = Long.parseLong(matcher.group(1));
        long seconds = Long.parseLong(matcher.group(2));
        String fraction = matcher.group(3);
        long fractionMs = 0L;
        if (fraction != null && !fraction.isBlank()) {
            fractionMs = switch (fraction.length()) {
                case 1 -> Long.parseLong(fraction) * 100L;
                case 2 -> Long.parseLong(fraction) * 10L;
                default -> Long.parseLong(fraction.substring(0, Math.min(3, fraction.length())));
            };
        }
        return minutes * 60_000L + seconds * 1_000L + fractionMs;
    }

    private record RawLrcLine(long startMs, String payload, String text, long offsetMs) {
    }

    private record SegmentDraft(long startMs, String text) {
    }

    private record ParsedSegments(String text, List<Word> words) {
    }
}
