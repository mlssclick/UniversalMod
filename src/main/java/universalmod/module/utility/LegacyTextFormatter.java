package universalmod.module.utility;

import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;

final class LegacyTextFormatter {
    private LegacyTextFormatter() {
    }

    static MutableText parse(String raw) {
        MutableText result = Text.empty();
        if (raw == null || raw.isEmpty()) return result;
        Style style = Style.EMPTY;
        StringBuilder segment = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            LegacyCode code = readLegacyCode(raw, i);
            if (code != null) {
                appendSegment(result, segment, style);
                style = code.color() == null ? applyCode(style, code.code()) : style.withColor(code.color());
                i = code.endIndex();
                continue;
            }
            if (c == '\u00a7' && i + 1 < raw.length()) {
                appendSegment(result, segment, style);
                style = applyCode(style, Character.toLowerCase(raw.charAt(i + 1)));
                i++;
                continue;
            }
            segment.append(c);
        }
        appendSegment(result, segment, style);
        return result;
    }

    static String strip(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        StringBuilder builder = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            LegacyCode code = readLegacyCode(raw, i);
            if (code != null) { i = code.endIndex(); continue; }
            if (raw.charAt(i) == '\u00a7' && i + 1 < raw.length()) { i++; continue; }
            builder.append(raw.charAt(i));
        }
        return builder.toString();
    }

    private static void appendSegment(MutableText target, StringBuilder segment, Style style) {
        if (segment.isEmpty()) return;
        target.append(Text.literal(segment.toString()).setStyle(style));
        segment.setLength(0);
    }

    private static Style applyCode(Style style, char code) {
        Integer color = colorFor(code);
        if (color != null) return style.withColor(color);
        return switch (Character.toLowerCase(code)) {
            case 'k' -> style.withObfuscated(true);
            case 'l' -> style.withBold(true);
            case 'm' -> style.withStrikethrough(true);
            case 'n' -> style.withUnderline(true);
            case 'o' -> style.withItalic(true);
            case 'r' -> Style.EMPTY;
            default -> style;
        };
    }

    private static LegacyCode readLegacyCode(String text, int index) {
        if (text == null || index < 0 || index + 1 >= text.length() || text.charAt(index) != '&') return null;
        char code = Character.toLowerCase(text.charAt(index + 1));
        if (code == 'x' && index + 13 < text.length()) {
            StringBuilder hex = new StringBuilder(6);
            for (int cursor = index + 2; cursor <= index + 12; cursor += 2) {
                if (text.charAt(cursor) != '&' || !isHex(text.charAt(cursor + 1))) return new LegacyCode(null, code, index + 1);
                hex.append(text.charAt(cursor + 1));
            }
            try { return new LegacyCode(0xFF000000 | Integer.parseInt(hex.toString(), 16), '\u0000', index + 13); }
            catch (NumberFormatException ignored) { return new LegacyCode(null, code, index + 1); }
        }
        if (code == '#' && index + 7 < text.length()) {
            String hex = text.substring(index + 2, index + 8);
            if (hex.chars().allMatch(value -> isHex((char) value))) {
                try { return new LegacyCode(0xFF000000 | Integer.parseInt(hex, 16), '\u0000', index + 7); }
                catch (NumberFormatException ignored) { return new LegacyCode(null, code, index + 1); }
            }
        }
        if (colorFor(code) != null || "klmnor".indexOf(code) >= 0) return new LegacyCode(null, code, index + 1);
        return null;
    }

    private static Integer colorFor(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> 0xFF000000; case '1' -> 0xFF0000AA; case '2' -> 0xFF00AA00; case '3' -> 0xFF00AAAA;
            case '4' -> 0xFFAA0000; case '5' -> 0xFFAA00AA; case '6' -> 0xFFFFAA00; case '7' -> 0xFFAAAAAA;
            case '8' -> 0xFF555555; case '9' -> 0xFF5555FF; case 'a' -> 0xFF55FF55; case 'b' -> 0xFF55FFFF;
            case 'c' -> 0xFFFF5555; case 'd' -> 0xFFFF55FF; case 'e' -> 0xFFFFFF55; case 'f' -> 0xFFFFFFFF; default -> null;
        };
    }

    private static boolean isHex(char c) {
        return c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F';
    }

    private record LegacyCode(Integer color, char code, int endIndex) {
    }
}
