package universalmod.module.utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;
import universalmod.service.language.LanguageManager;
import universalmod.setting.BooleanSetting;
import universalmod.setting.NumberSetting;
import universalmod.setting.StringSetting;

@ModuleInformation(
        moduleName = "Custom Donate",
        moduleCategory = ModuleCategory.UTILS
)
public final class CustomDonate extends Module {
    private static CustomDonate instance;
    private static final String SCOREBOARD_GROUP_LABEL = "\u0413\u0440\u0443\u043f\u043f\u0430:";
    private static final String SCOREBOARD_TITLE_LABEL = "\u0422\u0438\u0442\u0443\u043b:";
    private static final String SCOREBOARD_GROUP_KEY = "\u0433\u0440\u0443\u043f\u043f\u0430";
    private static final String SCOREBOARD_TITLE_KEY = "\u0442\u0438\u0442\u0443\u043b";
    private static final int SCOREBOARD_TIME_COLOR = 0xFF989898;
    private final StringSetting prefix = new StringSetting("Prefix", "", 512);
    private final StringSetting suffix = new StringSetting("Suffix", "", 512);
    private final BooleanSetting scoreboardTime = new BooleanSetting("Время", false);
    private final NumberSetting scoreboardDays = new NumberSetting("Дни", 29.0, 1.0, 180.0, 1.0);

    public CustomDonate() {
        prefix.setDescription("Text inserted before your nickname.");
        suffix.setDescription("Text inserted after your nickname.");
        scoreboardTime.setDescription("Shows a fake group age in the scoreboard.");
        scoreboardDays.setDescription("Days shown after the fake group in the scoreboard.");
        scoreboardDays.setVisible(scoreboardTime::getValue);
        instance = this;
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }

    public static boolean appliesTo(PlayerEntity player) {
        if (!isActive() || player == null) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.getSession() == null) {
            return false;
        }
        return player.getUuid().equals(client.player.getUuid())
                || same(player.getEntityName(), client.getSession().getUsername())
                || same(player.getName().getString(), client.getSession().getUsername());
    }

    public static Text replaceDonate(PlayerEntity player, Text fallback) {
        if (!appliesTo(player)) {
            return fallback;
        }
        return buildDonateText(cleanPlayerName(player));
    }

    public static Text replaceDonateTab(PlayerEntity player, Text fallback) {
        if (!appliesTo(player)) {
            return fallback;
        }
        return replaceTabName(cleanPlayerName(player), fallback);
    }

    public static Text replaceDonateTab(PlayerListEntry info, Text fallback) {
        if (!isActive() || info == null || info.getProfile() == null || !isLocalTabEntry(info, null)) {
            return fallback;
        }
        return replaceTabName(info.getProfile().getName(), fallback);
    }

    private static Text replaceTabName(String name, Text fallback) {
        String cleanName = clean(name);
        if (cleanName.isBlank()) {
            return fallback;
        }
        if (fallback == null) {
            return buildDonateText(cleanName);
        }

        String plain = fallback.getString();
        int nameStart = indexOfIgnoreCase(plain, cleanName, 0);
        if (nameStart < 0) {
            return buildDonateText(cleanName);
        }

        int decorationEnd = leadingTabDecorationEnd(plain, nameStart);
        MutableText result = Text.empty();
        result.append(sliceText(fallback, 0, decorationEnd));
        result.append(buildDonateText(cleanName));
        return result;
    }

    public static String replaceDonateText(PlayerEntity player, String fallback) {
        if (!appliesTo(player)) {
            return fallback;
        }
        return instance.buildPlainText(cleanPlayerName(player));
    }

    public static String prefixText() {
        return isActive() ? instance.resolvePrefixText() : "";
    }

    public static String suffixText() {
        return isActive() ? normalizedDonateText(instance.suffix.getValue()) : "";
    }

    public static boolean hasPrefixText() {
        return isActive() && !stripFormatting(prefixText()).isBlank();
    }

    public static boolean hasSuffixText() {
        return isActive() && !stripFormatting(suffixText()).isBlank();
    }

    public static boolean scoreboardTimeEnabled() {
        return isActive() && instance.scoreboardTime.getValue();
    }
    public static int tabPriority(PlayerListEntry info, PlayerEntity player) {
        if (info == null || info.getProfile() == null) return DonateRankResolver.NONE;
        if (isLocalTabEntry(info, player) && isActive()) {
            int customOverride = DonateRankResolver.resolveKnown(DonateRankResolver.normalize(prefixText()));
            if (customOverride != DonateRankResolver.NONE) return customOverride;
            return stripFormatting(prefixText()).isBlank() ? DonateRankResolver.NONE : DonateRankResolver.CUSTOM;
        }
        return DonateRankResolver.resolveVisible(info, player);
    }

    public static Text buildDonateText(String name) {
        if (!isActive()) {
            return Text.literal(name == null ? "" : name);
        }
        return instance.buildText(name == null ? "" : name);
    }

    public static Text replaceChatMessage(Text message) {
        if (!isActive() || message == null) {
            return message;
        }
        String plain = message.getString();
        LocalNameMatch match = findLocalName(plain);
        if (match == null) {
            return message;
        }
        int nameStart = match.index();
        int nameEnd = nameStart + match.name().length();
        int replaceStart = chatPrefixStart(plain, nameStart);
        int replaceEnd = chatSuffixEnd(plain, nameEnd);

        MutableText result = Text.empty();
        result.append(sliceText(message, 0, replaceStart));
        result.append(buildDonateText(match.name()));
        result.append(sliceText(message, replaceEnd, plain.length()));
        return result;
    }

    public static Text replaceScoreboardLine(Text line) {
        if (!isActive() || line == null) {
            return line;
        }
        if (hasPrefixText() && isScoreboardGroupLine(line)) {
            return replaceScoreboardValue(line, SCOREBOARD_GROUP_LABEL, prefixText(), true);
        }
        if (hasSuffixText() && isScoreboardTitleLine(line)) {
            return replaceScoreboardValue(line, SCOREBOARD_TITLE_LABEL, suffixText(), false);
        }


        return line;
    }

    public static boolean isScoreboardGroupLine(Text line) {
        return containsLabel(line, SCOREBOARD_GROUP_LABEL);
    }

    public static boolean isScoreboardTitleLine(Text line) {
        return containsLabel(line, SCOREBOARD_TITLE_LABEL);
    }

    private Text buildText(String name) {
        MutableText result = Text.empty();
        String prefixText = resolvePrefixText();
        String suffixText = normalizedDonateText(suffix.getValue());
        if (!stripFormatting(prefixText).isBlank()) {
            result.append(parseFormatted(prefixText));
            result.append(Text.literal(" "));
        }
        result.append(Text.literal(name));
        if (!stripFormatting(suffixText).isBlank()) {
            result.append(Text.literal(" "));
            result.append(parseFormatted(suffixText));
        }
        return result;
    }

    private String buildPlainText(String name) {
        return join(stripFormatting(prefixText()), name, stripFormatting(suffixText()));
    }

    private String resolvePrefixText() {
        return effectivePrefixText(prefix.getValue());
    }

    private static String cleanPlayerName(PlayerEntity player) {
        if (player == null) {
            return "Player";
        }
        String name = clean(player.getName().getString());
        if (name.isBlank()) {
            name = clean(player.getEntityName());
        }
        return name.isBlank() ? "Player" : name;
    }

    private static String join(String prefix, String name, String suffix) {
        StringBuilder builder = new StringBuilder();
        String cleanPrefix = normalizedDonateText(prefix);
        String cleanSuffix = normalizedDonateText(suffix);
        if (!cleanPrefix.isBlank()) {
            builder.append(cleanPrefix);
            builder.append(' ');
        }
        builder.append(name);
        if (!cleanSuffix.isBlank()) {
            builder.append(' ');
            builder.append(cleanSuffix);
        }
        return builder.toString();
    }

    private static String clean(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(text.length());
        boolean skipFormatting = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (skipFormatting) {
                skipFormatting = false;
                continue;
            }
            if (c == '\u00a7') {
                skipFormatting = true;
                continue;
            }
            if (!Character.isISOControl(c)) {
                builder.append(c);
            }
        }
        return builder.toString().trim();
    }

    private static String normalizedDonateText(String text) {
        return text == null ? "" : text.strip();
    }

    private static String effectivePrefixText(String text) {
        return normalizedDonateText(text);
    }

    private static Text replaceLocalPlayerName(Text line) {
        LocalNameMatch match = findLocalName(line.getString());
        if (match == null) {
            return line;
        }
        return buildDonateText(match.name());
    }

    private static LocalNameMatch findLocalName(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return null;
        }
        List<String> candidates = new ArrayList<>();
        if (client.player != null) {
            addNameCandidate(candidates, client.player.getEntityName());
            addNameCandidate(candidates, client.player.getName().getString());
        }
        if (client.getSession() != null) {
            addNameCandidate(candidates, client.getSession().getUsername());
        }
        LocalNameMatch best = null;
        for (String candidate : candidates) {
            int index = indexOfIgnoreCase(text, candidate, 0);
            if (index < 0) {
                continue;
            }
            if (best == null || index < best.index() || index == best.index() && candidate.length() > best.name().length()) {
                best = new LocalNameMatch(candidate, index);
            }
        }
        return best;
    }

    private static void addNameCandidate(List<String> candidates, String value) {
        String name = clean(value);
        if (name.isBlank()) {
            return;
        }
        for (String candidate : candidates) {
            if (same(candidate, name)) {
                return;
            }
        }
        candidates.add(name);
    }

    private static boolean same(String left, String right) {
        return left != null && right != null && left.toLowerCase(Locale.ROOT).equals(right.toLowerCase(Locale.ROOT));
    }

    private static int leadingTabDecorationEnd(String text, int maxEnd) {
        if (text == null || text.isBlank() || maxEnd <= 0) {
            return 0;
        }
        int end = 0;
        while (end < maxEnd) {
            char c = text.charAt(end);
            if (Character.isLetterOrDigit(c)) {
                break;
            }
            end++;
        }
        return end;
    }

    private static boolean isLocalTabEntry(PlayerListEntry info, PlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getSession() == null) {
            return false;
        }
        if (player != null && appliesTo(player)) {
            return true;
        }
        return same(info.getProfile().getName(), client.getSession().getUsername());
    }

    private static Text replaceScoreboardValue(Text line, String label, String value, boolean groupLine) {
        String plain = line.getString();
        int labelStart = indexOfIgnoreCase(plain, label, 0);
        MutableText result = Text.empty();
        if (labelStart >= 0) {
            result.append(sliceText(line, 0, labelStart + label.length()));
            result.append(Text.literal(" "));
            result.append(parseFormatted(value));
            appendScoreboardTime(result, groupLine);
            return result;
        }
        int prefixEnd = Math.min(plain.length(), Math.max(1, firstLetterIndex(plain)));
        result.append(sliceText(line, 0, prefixEnd));
        result.append(Text.literal(label).setStyle(styleAt(line, prefixEnd)));
        result.append(Text.literal(" "));
        result.append(parseFormatted(value));
        appendScoreboardTime(result, groupLine);
        return result;
    }

    private static void appendScoreboardTime(MutableText result, boolean groupLine) {
        if (!groupLine || !scoreboardTimeEnabled()) {
            return;
        }
        int days = Math.max(1, Math.min(180, (int) Math.round(instance.scoreboardDays.getValue())));
        result.append(Text.literal(" ").setStyle(Style.EMPTY));
        result.append(Text.literal(LanguageManager.translateFormat("scoreboard.days.short", days)).setStyle(Style.EMPTY.withColor(SCOREBOARD_TIME_COLOR)));
    }

    private static boolean containsLabel(Text line, String label) {
        if (line == null) {
            return false;
        }
        String normalized = scoreboardKey(line.getString());
        return label.equals(SCOREBOARD_GROUP_LABEL) ? normalized.contains(SCOREBOARD_GROUP_KEY) : normalized.contains(SCOREBOARD_TITLE_KEY);
    }

    private static String scoreboardKey(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(text.length());
        boolean skipFormatting = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (skipFormatting) {
                skipFormatting = false;
                continue;
            }
            if (c == '\u00a7') {
                skipFormatting = true;
                continue;
            }
            if (Character.isLetter(c)) {
                builder.append(Character.toLowerCase(c));
            }
        }
        return builder.toString();
    }

    private static int firstLetterIndex(String text) {
        if (text == null) {
            return 0;
        }
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetter(text.charAt(i))) {
                return i;
            }
        }
        return 0;
    }

    private static int chatPrefixStart(String text, int nameStart) {
        int lineStart = text.lastIndexOf('\n', Math.max(0, nameStart - 1)) + 1;
        int pipe = text.lastIndexOf('|', Math.max(0, nameStart - 1));
        if (pipe >= lineStart && nameStart - pipe <= 96) {
            int start = pipe + 1;
            while (start < nameStart && Character.isWhitespace(text.charAt(start))) {
                start++;
            }
            return start;
        }
        int bracketStart = text.lastIndexOf('[', Math.max(0, nameStart - 1));
        if (bracketStart >= lineStart) {
            int bracketEndBeforeName = text.indexOf(']', bracketStart);
            if (bracketEndBeforeName >= 0 && bracketEndBeforeName < nameStart) {
                return bracketStart;
            }
        }
        int decoratedStart = lastDecoratedPrefixStart(text, lineStart, nameStart);
        if (decoratedStart >= 0) {
            return decoratedStart;
        }
        return nameStart;
    }

    private static int chatSuffixEnd(String text, int nameEnd) {
        int cursor = nameEnd;
        int length = text.length();
        while (cursor < length && Character.isWhitespace(text.charAt(cursor))) {
            cursor++;
        }
        cursor = skipOwnChatSuffix(text, cursor);
        while (cursor < length && Character.isWhitespace(text.charAt(cursor))) {
            cursor++;
        }
        if (cursor < length && text.charAt(cursor) == '[') {
            int close = text.indexOf(']', cursor + 1);
            int colon = text.indexOf(':', cursor + 1);
            if (close >= 0 && (colon < 0 || close < colon)) {
                cursor = close + 1;
            }
            while (cursor < length && Character.isWhitespace(text.charAt(cursor))) {
                cursor++;
            }
            return cursor;
        }
        return skipServerChatSuffix(text, cursor);
    }

    private static int skipOwnChatSuffix(String text, int start) {
        String suffix = stripFormatting(suffixText()).strip();
        if (suffix.isBlank()) {
            return start;
        }
        int suffixStart = indexOfIgnoreCase(text, suffix, start);
        if (suffixStart != start) {
            return start;
        }
        return suffixStart + suffix.length();
    }

    private static int skipServerChatSuffix(String text, int start) {
        int cursor = start;
        int length = text.length();
        int firstTokenStart = cursor;
        while (cursor < length && !Character.isWhitespace(text.charAt(cursor)) && !isChatMessageSeparator(text.charAt(cursor))) {
            cursor++;
        }
        if (cursor == firstTokenStart) {
            return start;
        }
        while (cursor < length && Character.isWhitespace(text.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    private static boolean isChatMessageSeparator(char c) {
        return c == ':' || c == '\u00bb' || c == '>' || c == '\u203a';
    }

    private static int lastDecoratedPrefixStart(String text, int lineStart, int nameStart) {
        int best = -1;
        char[] openers = {'[', '<', '\u00ab', '\u300a', '\u27e8', '\u3010', '('};
        for (char opener : openers) {
            int index = text.lastIndexOf(opener, Math.max(0, nameStart - 1));
            if (index >= lineStart && index > best) {
                best = index;
            }
        }
        return best;
    }

    private static int indexOfIgnoreCase(String text, String needle, int fromIndex) {
        if (text == null || needle == null || needle.isEmpty()) {
            return -1;
        }
        int max = text.length() - needle.length();
        for (int i = Math.max(0, fromIndex); i <= max; i++) {
            if (text.regionMatches(true, i, needle, 0, needle.length())) {
                return i;
            }
        }
        return -1;
    }
    private static Text sliceText(Text component, int start, int end) {
        return DonateComponentSlices.slice(component, start, end);
    }
    private static Style styleAt(Text component, int targetIndex) {
        return DonateComponentSlices.styleAt(component, targetIndex);
    }

    public static MutableText parseFormatted(String raw) {
        MutableText result = Text.empty();
        if (raw == null || raw.isEmpty()) {
            return result;
        }
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

    public static String stripFormatting(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            LegacyCode code = readLegacyCode(raw, i);
            if (code != null) {
                i = code.endIndex();
                continue;
            }
            if (raw.charAt(i) == '\u00a7' && i + 1 < raw.length()) {
                i++;
                continue;
            }
            builder.append(raw.charAt(i));
        }
        return builder.toString();
    }

    private static void appendSegment(MutableText target, StringBuilder segment, Style style) {
        if (segment.isEmpty()) {
            return;
        }
        target.append(Text.literal(segment.toString()).setStyle(style));
        segment.setLength(0);
    }

    private static Style applyCode(Style style, char code) {
        Integer color = colorFor(code);
        if (color != null) {
            return style.withColor(color);
        }
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
        if (text == null || index < 0 || index + 1 >= text.length() || text.charAt(index) != '&') {
            return null;
        }
        char code = Character.toLowerCase(text.charAt(index + 1));
        if (code == 'x' && index + 13 < text.length()) {
            StringBuilder hex = new StringBuilder(6);
            for (int cursor = index + 2; cursor <= index + 12; cursor += 2) {
                if (text.charAt(cursor) != '&' || !isHex(text.charAt(cursor + 1))) {
                    return new LegacyCode(null, code, index + 1);
                }
                hex.append(text.charAt(cursor + 1));
            }
            try {
                return new LegacyCode(0xFF000000 | Integer.parseInt(hex.toString(), 16), '\u0000', index + 13);
            } catch (NumberFormatException ignored) {
                return new LegacyCode(null, code, index + 1);
            }
        }
        if (code == '#' && index + 7 < text.length()) {
            String hex = text.substring(index + 2, index + 8);
            if (hex.chars().allMatch(value -> isHex((char) value))) {
                try {
                    return new LegacyCode(0xFF000000 | Integer.parseInt(hex, 16), '\u0000', index + 7);
                } catch (NumberFormatException ignored) {
                    return new LegacyCode(null, code, index + 1);
                }
            }
        }
        if (colorFor(code) != null || "klmnor".indexOf(code) >= 0) {
            return new LegacyCode(null, code, index + 1);
        }
        return null;
    }

    private static Integer colorFor(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> 0xFF000000;
            case '1' -> 0xFF0000AA;
            case '2' -> 0xFF00AA00;
            case '3' -> 0xFF00AAAA;
            case '4' -> 0xFFAA0000;
            case '5' -> 0xFFAA00AA;
            case '6' -> 0xFFFFAA00;
            case '7' -> 0xFFAAAAAA;
            case '8' -> 0xFF555555;
            case '9' -> 0xFF5555FF;
            case 'a' -> 0xFF55FF55;
            case 'b' -> 0xFF55FFFF;
            case 'c' -> 0xFFFF5555;
            case 'd' -> 0xFFFF55FF;
            case 'e' -> 0xFFFFFF55;
            case 'f' -> 0xFFFFFFFF;
            default -> null;
        };
    }

    private static boolean isHex(char c) {
        return c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F';
    }

    private record LegacyCode(Integer color, char code, int endIndex) {
    }

private record LocalNameMatch(String name, int index) {
    }
}
