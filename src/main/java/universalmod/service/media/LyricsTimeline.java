package universalmod.service.media;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LyricsTimeline {
    private static final Pattern LINE_TIMESTAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern WORD_TIMESTAMP = Pattern.compile("<(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?>");
    private static final Pattern OFFSET = Pattern.compile("(?im)^\\[offset\\s*:\\s*([+-]?\\d+)\\]\\s*$");
    private static final Pattern METADATA = Pattern.compile("(?i)^\\[(?:ar|ti|al|by|re|ve|length|offset):.*]$");
    static final int MAX_DOCUMENT_CHARS = 2_000_000;
    static final int MAX_LINES = 5_000;
    static final int MAX_WORDS = 50_000;

    private LyricsTimeline() {
    }

    public static Document empty() {
        return new Document(List.of(), SyncType.PLAIN, Source.NONE, "", 0L);
    }

    public static Document parseLrc(String lyrics, long durationMs, Source source) {
        String normalized = normalize(lyrics);
        if (normalized.isBlank()) {
            return empty().withSource(source);
        }
        long offsetMs = extractOffsetMillis(normalized);
        List<RawLrcLine> timed = new ArrayList<>();
        List<String> plain = new ArrayList<>();
        for (String rawLine : normalized.lines().toList()) {
            if (timed.size() >= MAX_LINES) {
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
            if (text.isBlank()) {
                continue;
            }
            if (timestamps.isEmpty()) {
                plain.add(text);
                continue;
            }
            for (long timestamp : timestamps) {
                timed.add(new RawLrcLine(timestamp, payload, text, offsetMs));
            }
        }
        if (timed.isEmpty()) {
            return plainDocument(plain, source, normalized, durationMs);
        }
        timed.sort(Comparator.comparingLong(RawLrcLine::startMs));
        List<Line> lines = new ArrayList<>(timed.size());
        boolean hasWords = false;
        for (int i = 0; i < timed.size(); i++) {
            RawLrcLine raw = timed.get(i);
            long nextStart = i + 1 < timed.size() ? timed.get(i + 1).startMs() : durationMs;
            long endMs = resolveLineEnd(raw.startMs(), nextStart, durationMs);
            ParsedSegments segments = parseEnhancedSegments(raw.payload(), raw.offsetMs(), endMs);
            if (!segments.words().isEmpty()) {
                hasWords = true;
                lines.add(new Line(raw.startMs(), endMs, segments.text(), segments.words()));
            } else {
                List<Word> estimated = estimateWords(raw.text(), raw.startMs(), endMs);
                lines.add(new Line(raw.startMs(), endMs, raw.text(), estimated));
            }
        }
        boolean estimatedWords = !hasWords && lines.stream().anyMatch(line -> !line.words().isEmpty());
        return new Document(List.copyOf(lines), hasWords ? SyncType.WORD : estimatedWords ? SyncType.ESTIMATED_WORD : SyncType.LINE, source, normalized, durationMs);
    }

    public static Document parseLyricsfile(String yaml, Source source) {
        String normalized = normalize(yaml);
        if (normalized.isBlank() || normalized.length() > MAX_DOCUMENT_CHARS) {
            return empty().withSource(source);
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
            if (builders.size() >= MAX_LINES || wordCount >= MAX_WORDS) {
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

        List<Line> lines = new ArrayList<>();
        boolean hasWords = false;
        for (int i = 0; i < builders.size(); i++) {
            YamlLineBuilder builder = builders.get(i);
            if (builder.startMs < 0L) {
                continue;
            }
            long nextStart = nextValidStart(builders, i + 1);
            long lineEnd = builder.endMs >= builder.startMs ? builder.endMs : resolveLineEnd(builder.startMs, nextStart, durationMs);
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
                words = estimateWords(text, start, end);
            }
            lines.add(new Line(start, end, text, words));
        }
        lines.sort(Comparator.comparingLong(Line::startMs));
        if (!lines.isEmpty()) {
            boolean estimatedWords = !hasWords && lines.stream().anyMatch(line -> !line.words().isEmpty());
            return new Document(List.copyOf(lines), hasWords ? SyncType.WORD : estimatedWords ? SyncType.ESTIMATED_WORD : SyncType.LINE, source, normalized, durationMs);
        }
        List<String> plainLines = plain.toString().lines().map(String::trim).filter(s -> !s.isBlank()).toList();
        return plainDocument(plainLines, source, normalized, durationMs);
    }

    public static Document parseTtml(String ttml, Source source) {
        String normalized = ttml == null ? "" : ttml.trim();
        if (normalized.isBlank() || normalized.length() > MAX_DOCUMENT_CHARS) {
            return empty().withSource(source);
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            org.w3c.dom.Document xml = factory.newDocumentBuilder().parse(new InputSource(new StringReader(normalized)));
            NodeList paragraphs = xml.getElementsByTagNameNS("*", "p");
            List<Line> lines = new ArrayList<>();
            boolean hasWords = false;
            for (int i = 0; i < paragraphs.getLength() && lines.size() < MAX_LINES; i++) {
                Node node = paragraphs.item(i);
                if (!(node instanceof Element paragraph)) {
                    continue;
                }
                long startMs = parseTtmlTime(attribute(paragraph, "begin"), -1L);
                if (startMs < 0L) {
                    continue;
                }
                long endMs = parseTtmlTime(attribute(paragraph, "end"), -1L);
                if (endMs < startMs) {
                    long dur = parseTtmlTime(attribute(paragraph, "dur"), -1L);
                    endMs = dur > 0L ? startMs + dur : -1L;
                }
                List<TimedText> spans = new ArrayList<>();
                collectTimedSpans(paragraph, spans, startMs, endMs);
                List<Word> words = new ArrayList<>();
                String text;
                if (!spans.isEmpty()) {
                    StringBuilder builder = new StringBuilder();
                    for (int j = 0; j < spans.size() && words.size() < MAX_WORDS; j++) {
                        TimedText span = spans.get(j);
                        if (span.text().isEmpty()) {
                            continue;
                        }
                        int charStart = builder.length();
                        builder.append(span.text());
                        int charEnd = builder.length();
                        long wordEnd = span.endMs() > span.startMs()
                                ? span.endMs()
                                : j + 1 < spans.size() ? spans.get(j + 1).startMs() : endMs;
                        wordEnd = Math.max(span.startMs() + 1L, wordEnd);
                        words.add(new Word(span.startMs(), wordEnd, span.text(), charStart, charEnd));
                    }
                    text = builder.toString().stripTrailing();
                    hasWords |= !words.isEmpty();
                } else {
                    text = paragraph.getTextContent() == null ? "" : paragraph.getTextContent().strip();
                }
                if (text.isBlank()) {
                    continue;
                }
                if (words.isEmpty() && endMs > startMs) {
                    words = new ArrayList<>(estimateWords(text, startMs, endMs));
                }
                lines.add(new Line(startMs, endMs, text, List.copyOf(words)));
            }
            lines.sort(Comparator.comparingLong(Line::startMs));
            lines = inferMissingLineEnds(lines, 0L);
            boolean estimatedWords = !hasWords && lines.stream().anyMatch(line -> !line.words().isEmpty());
            return lines.isEmpty() ? empty().withSource(source)
                    : new Document(List.copyOf(lines), hasWords ? SyncType.WORD : estimatedWords ? SyncType.ESTIMATED_WORD : SyncType.LINE, source, normalized, 0L);
        } catch (Exception exception) {
            return empty().withSource(source);
        }
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

    public static float revealedCodePoints(Line line, long positionMs) {
        if (line == null || positionMs < line.startMs()) return 0.0F;
        String text = line.text();
        int total = text.codePointCount(0, text.length());
        if (positionMs >= line.endMs()) return total;
        if (line.words().isEmpty()) {
            return total * (float) Math.max(0.0, Math.min(1.0,
                    (positionMs - line.startMs()) / (double) Math.max(1L, line.endMs() - line.startMs())));
        }
        float revealed = 0.0F;
        for (Word word : line.words()) {
            if (positionMs < word.startMs()) continue;
            int start = Math.max(0, Math.min(text.length(), word.startChar()));
            int end = Math.max(start, Math.min(text.length(), word.endChar()));
            float count = text.codePointCount(0, start)
                    + text.codePointCount(start, end) * wordProgress(word, positionMs);
            revealed = Math.max(revealed, count);
        }
        return Math.min(total, revealed);
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

    private static List<Word> estimateWords(String text, long lineStartMs, long lineEndMs) {
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

    private static ParsedSegments parseEnhancedSegments(String payload, long offsetMs, long lineEndMs) {
        String value = payload == null ? "" : payload;
        Matcher matcher = WORD_TIMESTAMP.matcher(value);
        List<Long> starts = new ArrayList<>();
        List<Integer> ends = new ArrayList<>();
        while (matcher.find() && starts.size() < MAX_WORDS) {
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

    private static List<Word> buildYamlWords(YamlLineBuilder line, long lineEndMs, long offsetMs) {
        if (line.words.isEmpty()) {
            return List.of();
        }
        List<YamlWordBuilder> valid = line.words.stream().filter(word -> word.startMs >= 0L && word.text != null).toList();
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

    private static void collectTimedSpans(Element parent, List<TimedText> result, long lineStart, long lineEnd) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (!(node instanceof Element element)) {
                continue;
            }
            String local = element.getLocalName();
            if (local == null) {
                local = element.getTagName();
            }
            if ("span".equalsIgnoreCase(local)) {
                String text = directText(element);
                long start = parseTtmlTime(attribute(element, "begin"), lineStart);
                long end = parseTtmlTime(attribute(element, "end"), -1L);
                if (end < start) {
                    long dur = parseTtmlTime(attribute(element, "dur"), -1L);
                    end = dur > 0L ? start + dur : lineEnd;
                }
                if (!text.isEmpty() && start >= 0L) {
                    result.add(new TimedText(text, start, end));
                }
                collectTimedSpans(element, result, start, end);
            } else {
                collectTimedSpans(element, result, lineStart, lineEnd);
            }
        }
        result.sort(Comparator.comparingLong(TimedText::startMs));
    }

    private static String directText(Element element) {
        StringBuilder builder = new StringBuilder();
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
                builder.append(node.getNodeValue());
            }
        }
        return builder.toString();
    }

    private static String attribute(Element element, String name) {
        if (element.hasAttribute(name)) {
            return element.getAttribute(name);
        }
        if (element.hasAttributeNS("http://www.w3.org/ns/ttml#parameter", name)) {
            return element.getAttributeNS("http://www.w3.org/ns/ttml#parameter", name);
        }
        return "";
    }

    private static long parseTtmlTime(String raw, long fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        try {
            if (value.endsWith("ms")) {
                return Math.round(Double.parseDouble(value.substring(0, value.length() - 2)));
            }
            if (value.endsWith("s")) {
                return Math.round(Double.parseDouble(value.substring(0, value.length() - 1)) * 1000.0D);
            }
            String[] parts = value.split(":");
            if (parts.length == 3) {
                double seconds = Double.parseDouble(parts[2]);
                return Math.round((Long.parseLong(parts[0]) * 3600.0D + Long.parseLong(parts[1]) * 60.0D + seconds) * 1000.0D);
            }
            if (parts.length == 2) {
                double seconds = Double.parseDouble(parts[1]);
                return Math.round((Long.parseLong(parts[0]) * 60.0D + seconds) * 1000.0D);
            }
            return Math.round(Double.parseDouble(value) * 1000.0D);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static List<Line> inferMissingLineEnds(List<Line> input, long durationMs) {
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

    private static Document plainDocument(List<String> plain, Source source, String raw, long durationMs) {
        List<Line> lines = new ArrayList<>();
        for (String value : plain) {
            String text = value == null ? "" : value.trim();
            if (!text.isBlank()) {
                lines.add(new Line(-1L, -1L, text, List.of()));
            }
        }
        return new Document(List.copyOf(lines), SyncType.PLAIN, source, raw == null ? "" : raw, durationMs);
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

    private static String parseYamlScalar(String raw) {
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

    private static long resolveLineEnd(long startMs, long nextStartMs, long durationMs) {
        if (nextStartMs > startMs) {
            return nextStartMs;
        }
        if (durationMs > startMs) {
            return durationMs;
        }
        return startMs + 4_000L;
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

    private static String normalize(String value) {
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
        LRCLIB
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

    private record RawLrcLine(long startMs, String payload, String text, long offsetMs) {
    }

    private record SegmentDraft(long startMs, String text) {
    }

    private record EstimatedToken(String text, int startChar, int endChar, double weight) {
    }

    private record ParsedSegments(String text, List<Word> words) {
    }

    private record TimedText(String text, long startMs, long endMs) {
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
