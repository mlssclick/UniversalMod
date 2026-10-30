package universalmod.service.media;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static universalmod.service.media.LyricsTimeline.Document;
import static universalmod.service.media.LyricsTimeline.Line;
import static universalmod.service.media.LyricsTimeline.Source;
import static universalmod.service.media.LyricsTimeline.SyncType;
import static universalmod.service.media.LyricsTimeline.Word;


final class LyricsFileYamlParser {
    private LyricsFileYamlParser() {
    }

    static Document parse(String yaml, Source source) {
        String normalized = LyricsTimeline.normalize(yaml);
        if (normalized.isBlank() || normalized.length() > LyricsTimeline.MAX_DOCUMENT_CHARS) {
            return LyricsTimeline.empty().withSource(source);
        }
        List<YamlLineBuilder> builders = new ArrayList<>();
        YamlLineBuilder currentLine = null;
        YamlWordBuilder currentWord = null;
        boolean inLines = false;
        boolean inWords = false;
        boolean inPlain = false;
        int plainIndent = -1;
        StringBuilder plain = new StringBuilder();
        long durationMs = 0L;
        long offsetMs = 0L;
        int wordCount = 0;

        for (String raw : normalized.split("\\n", -1)) {
            int indent = leadingSpaces(raw);
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                if (inPlain) {
                    plain.append('\n');
                }
                continue;
            }
            if (indent == 0) {
                inWords = false;
                inPlain = false;
                if (trimmed.equals("lines:")) {
                    inLines = true;
                    continue;
                }
                if (trimmed.startsWith("plain:")) {
                    inLines = false;
                    inPlain = true;
                    plainIndent = -1;
                    String value = valueAfterColon(trimmed);
                    if (!value.equals("|") && !value.equals(">") && !value.isBlank()) {
                        plain.append(parseYamlScalar(value));
                    }
                    continue;
                }
                if (!trimmed.equals("metadata:")) {
                    inLines = false;
                }
            }
            if (!inLines && !inPlain) {
                if (trimmed.startsWith("duration_ms:")) {
                    durationMs = parseLong(valueAfterColon(trimmed), durationMs);
                } else if (trimmed.startsWith("offset_ms:")) {
                    offsetMs = parseLong(valueAfterColon(trimmed), offsetMs);
                }
                continue;
            }
            if (inPlain) {
                if (plainIndent < 0) {
                    plainIndent = indent;
                }
                int cut = Math.min(raw.length(), Math.max(0, plainIndent));
                plain.append(raw.substring(cut)).append('\n');
                continue;
            }
            if (builders.size() >= LyricsTimeline.MAX_LINES || wordCount >= LyricsTimeline.MAX_WORDS) {
                break;
            }
            if (indent <= 2 && trimmed.startsWith("- ")) {
                currentLine = new YamlLineBuilder();
                builders.add(currentLine);
                currentWord = null;
                inWords = false;
                assignLineField(currentLine, trimmed.substring(2));
                continue;
            }
            if (currentLine == null) {
                continue;
            }
            if (indent <= 4 && trimmed.equals("words:")) {
                inWords = true;
                currentWord = null;
                continue;
            }
            if (inWords && indent >= 4 && trimmed.startsWith("- ")) {
                currentWord = new YamlWordBuilder();
                currentLine.words.add(currentWord);
                wordCount++;
                assignWordField(currentWord, trimmed.substring(2));
                continue;
            }
            if (inWords && currentWord != null && indent >= 6) {
                assignWordField(currentWord, trimmed);
            } else if (indent >= 4) {
                assignLineField(currentLine, trimmed);
            }
        }

        return buildDocument(builders, plain, source, normalized, durationMs, offsetMs);
    }

    private static Document buildDocument(
            List<YamlLineBuilder> builders,
            StringBuilder plain,
            Source source,
            String normalized,
            long durationMs,
            long offsetMs
    ) {
        List<Line> lines = new ArrayList<>();
        boolean hasWords = false;
        for (int i = 0; i < builders.size(); i++) {
            YamlLineBuilder builder = builders.get(i);
            if (builder.startMs < 0L) {
                continue;
            }
            long nextStart = nextValidStart(builders, i + 1);
            long lineEnd = builder.endMs >= builder.startMs
                    ? builder.endMs
                    : LyricsTimeline.resolveLineEnd(builder.startMs, nextStart, durationMs);
            List<Word> words = buildYamlWords(builder, lineEnd, offsetMs);
            String text = builder.text == null ? "" : builder.text;
            boolean realWords = !words.isEmpty();
            if (realWords) {
                String rebuilt = rebuildText(words);
                if (!rebuilt.isBlank()) {
                    text = rebuilt;
                }
                hasWords = true;
            }
            text = text.stripTrailing();
            if (text.isBlank()) {
                continue;
            }
            long start = Math.max(0L, builder.startMs + offsetMs);
            long end = Math.max(start + 1L, lineEnd + offsetMs);
            if (!realWords) {
                words = LyricsTimeline.estimateWords(text, start, end);
            }
            lines.add(new Line(start, end, text, words));
        }
        lines.sort(Comparator.comparingLong(Line::startMs));
        if (!lines.isEmpty()) {
            boolean estimatedWords = !hasWords && lines.stream().anyMatch(line -> !line.words().isEmpty());
            return new Document(List.copyOf(lines),
                    hasWords ? SyncType.WORD : estimatedWords ? SyncType.ESTIMATED_WORD : SyncType.LINE,
                    source, normalized, durationMs);
        }
        List<String> plainLines = plain.toString().lines().map(String::trim).filter(line -> !line.isBlank()).toList();
        return LyricsTimeline.plainDocument(plainLines, source, normalized, durationMs);
    }

    private static List<Word> buildYamlWords(YamlLineBuilder line, long lineEndMs, long offsetMs) {
        if (line.words.isEmpty()) {
            return List.of();
        }
        List<YamlWordBuilder> valid = line.words.stream()
                .filter(word -> word.startMs >= 0L && word.text != null)
                .toList();
        if (valid.isEmpty()) {
            return List.of();
        }
        StringBuilder text = new StringBuilder();
        List<Word> words = new ArrayList<>();
        for (int i = 0; i < valid.size(); i++) {
            YamlWordBuilder word = valid.get(i);
            int startChar = text.length();
            text.append(word.text);
            int endChar = text.length();
            long start = Math.max(0L, word.startMs + offsetMs);
            long inferredEnd = i + 1 < valid.size() ? valid.get(i + 1).startMs + offsetMs : lineEndMs + offsetMs;
            long end = word.endMs >= word.startMs ? word.endMs + offsetMs : inferredEnd;
            words.add(new Word(start, Math.max(start + 1L, end), word.text, startChar, endChar));
        }
        return List.copyOf(words);
    }

    private static String rebuildText(List<Word> words) {
        StringBuilder builder = new StringBuilder();
        for (Word word : words) {
            builder.append(word.text());
        }
        return builder.toString().stripTrailing();
    }

    private static long nextValidStart(List<YamlLineBuilder> lines, int from) {
        for (int i = from; i < lines.size(); i++) {
            if (lines.get(i).startMs >= 0L) {
                return lines.get(i).startMs;
            }
        }
        return -1L;
    }

    private static void assignLineField(YamlLineBuilder builder, String field) {
        int colon = field.indexOf(':');
        if (colon < 0) {
            return;
        }
        String key = field.substring(0, colon).trim();
        String value = field.substring(colon + 1).trim();
        switch (key) {
            case "text" -> builder.text = parseYamlScalar(value);
            case "start_ms" -> builder.startMs = parseLong(value, builder.startMs);
            case "end_ms" -> builder.endMs = parseLong(value, builder.endMs);
        }
    }

    private static void assignWordField(YamlWordBuilder builder, String field) {
        int colon = field.indexOf(':');
        if (colon < 0) {
            return;
        }
        String key = field.substring(0, colon).trim();
        String value = field.substring(colon + 1).trim();
        switch (key) {
            case "text" -> builder.text = parseYamlScalar(value);
            case "start_ms" -> builder.startMs = parseLong(value, builder.startMs);
            case "end_ms" -> builder.endMs = parseLong(value, builder.endMs);
        }
    }

    static String parseYamlScalar(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length() - 1).replace("''", "'");
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            String inner = value.substring(1, value.length() - 1);
            StringBuilder builder = new StringBuilder(inner.length());
            boolean escaped = false;
            for (int i = 0; i < inner.length(); i++) {
                char c = inner.charAt(i);
                if (escaped) {
                    builder.append(switch (c) {
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        case '\\' -> '\\';
                        case '"' -> '"';
                        default -> c;
                    });
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else {
                    builder.append(c);
                }
            }
            if (escaped) {
                builder.append('\\');
            }
            return builder.toString();
        }
        if (value.equals("null") || value.equals("~")) {
            return "";
        }
        return value;
    }

    private static int leadingSpaces(String value) {
        int count = 0;
        while (count < value.length() && value.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private static String valueAfterColon(String value) {
        int colon = value.indexOf(':');
        return colon < 0 ? "" : value.substring(colon + 1).trim();
    }

    private static long parseLong(String value, long fallback) {
        try {
            String scalar = parseYamlScalar(value);
            int comment = scalar.indexOf(" #");
            if (comment >= 0) {
                scalar = scalar.substring(0, comment).trim();
            }
            return Long.parseLong(scalar);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static final class YamlLineBuilder {
        private String text = "";
        private long startMs = -1L;
        private long endMs = -1L;
        private final List<YamlWordBuilder> words = new ArrayList<>();
    }

    private static final class YamlWordBuilder {
        private String text = "";
        private long startMs = -1L;
        private long endMs = -1L;
    }
}
