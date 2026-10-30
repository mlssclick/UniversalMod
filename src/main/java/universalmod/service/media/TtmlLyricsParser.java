package universalmod.service.media;


import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static universalmod.service.media.LyricsTimeline.Document;
import static universalmod.service.media.LyricsTimeline.Line;
import static universalmod.service.media.LyricsTimeline.Source;
import static universalmod.service.media.LyricsTimeline.SyncType;
import static universalmod.service.media.LyricsTimeline.Word;


final class TtmlLyricsParser {
    private static final ErrorHandler REJECTING_ERROR_HANDLER = new ErrorHandler() {
        @Override
        public void warning(SAXParseException exception) {
            
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            throw exception;
        }
    };

    private TtmlLyricsParser() {
    }

    static Document parse(String ttml, Source source) {
        String normalized = ttml == null ? "" : ttml.trim();
        if (normalized.isBlank() || normalized.length() > LyricsTimeline.MAX_DOCUMENT_CHARS) {
            return LyricsTimeline.empty().withSource(source);
        }
        try {
            DocumentBuilderFactory factory = secureFactory();
            var documentBuilder = factory.newDocumentBuilder();
            documentBuilder.setErrorHandler(REJECTING_ERROR_HANDLER);
            org.w3c.dom.Document xml = documentBuilder.parse(new InputSource(new StringReader(normalized)));
            NodeList paragraphs = xml.getElementsByTagNameNS("*", "p");
            List<Line> lines = new ArrayList<>();
            boolean hasWords = false;
            for (int i = 0; i < paragraphs.getLength() && lines.size() < LyricsTimeline.MAX_LINES; i++) {
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
                    for (int j = 0; j < spans.size() && words.size() < LyricsTimeline.MAX_WORDS; j++) {
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
                    words = new ArrayList<>(LyricsTimeline.estimateWords(text, startMs, endMs));
                }
                lines.add(new Line(startMs, endMs, text, List.copyOf(words)));
            }
            lines.sort(Comparator.comparingLong(Line::startMs));
            lines = LyricsTimeline.inferMissingLineEnds(lines, 0L);
            boolean estimatedWords = !hasWords && lines.stream().anyMatch(line -> !line.words().isEmpty());
            return lines.isEmpty()
                    ? LyricsTimeline.empty().withSource(source)
                    : new Document(List.copyOf(lines),
                    hasWords ? SyncType.WORD : estimatedWords ? SyncType.ESTIMATED_WORD : SyncType.LINE,
                    source, normalized, 0L);
        } catch (ParserConfigurationException | SAXException | IOException | RuntimeException exception) {
            return LyricsTimeline.empty().withSource(source);
        }
    }

    private static DocumentBuilderFactory secureFactory() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory;
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

    static long parseTtmlTime(String raw, long fallback) {
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

    private record TimedText(String text, long startMs, long endMs) {
    }
}
