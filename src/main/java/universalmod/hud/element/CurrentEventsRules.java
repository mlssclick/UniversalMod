package universalmod.hud.element;

import java.util.Locale;

final class CurrentEventsRules {
    private CurrentEventsRules() {
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    static String normalizeRange(String range) {
        return range == null ? "" : range.replace(" ", "").trim();
    }

    static boolean isValidRange(String range) {
        String normalized = normalizeRange(range);
        if (!normalized.matches("\\d{1,3}-\\d{1,3}")) {
            return false;
        }
        int dash = normalized.indexOf('-');
        try {
            int start = Integer.parseInt(normalized.substring(0, dash));
            int end = Integer.parseInt(normalized.substring(dash + 1));
            return start >= 0 && start <= end;
        } catch (NumberFormatException malformedNumber) {
            return false;
        }
    }

    static boolean matchesRange(String range, int number) {
        if (number < 0) {
            return false;
        }
        String normalized = normalizeRange(range);
        int dash = normalized.indexOf('-');
        if (dash <= 0 || dash >= normalized.length() - 1) {
            return false;
        }
        try {
            int start = Integer.parseInt(normalized.substring(0, dash));
            int end = Integer.parseInt(normalized.substring(dash + 1));
            return number >= start && number <= end;
        } catch (NumberFormatException malformedNumber) {
            return false;
        }
    }

    static boolean isNewAnarchyEvent(String apiKey, String id) {
        return containsNewToken(apiKey) || containsNewToken(id);
    }

    private static boolean containsNewToken(String value) {
        String canonical = normalize(value);
        if (canonical.isEmpty()) {
            return false;
        }
        canonical = canonical.replaceAll("[^\\p{L}0-9]+", "_");
        canonical = canonical.replaceAll("^_+|_+$", "");
        canonical = canonical.replaceAll("_+", "_");
        return canonical.equals("new") || canonical.startsWith("new_") || canonical.endsWith("_new") || canonical.contains("_new_");
    }

    static int colorFor(String apiKey, String id, String displayName, String rare) {
        String key = normalize(apiKey + " " + id + " " + displayName + " " + rare);
        if (key.contains("лихорад") || key.contains("fever")) return 0xFFF1C24A;
        if (key.contains("кораб") || key.contains("ship")) return 0xFF8A5A2B;
        if (key.contains("посыл") || key.contains("parcel")) return 0xFF8A5A2B;
        if (key.contains("груз") || key.contains("cargo")) return 0xFFF34B4B;
        if (key.contains("шахт") || key.contains("mine")) return 0xFF8B1E1E;
        if (key.contains("полян") || key.contains("glade") || key.contains("snowquarry")) return 0xFF4CAF50;
        if (key.contains("контейн") || key.contains("container")) return 0xFF5DBBFF;
        if (key.contains("кубик") || key.contains("cube")) return 0xFF5DAEFF;
        if (key.contains("типо") || key.contains("jaycob")) return 0xFF7CFF3A;
        return 0xFFF3F6FA;
    }

    static int brighten(int color, float amount) {
        amount = Math.max(0.0F, Math.min(1.0F, amount));
        int a = (color >>> 24) & 0xFF;
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;
        r = (int) (r + (255 - r) * amount);
        g = (int) (g + (255 - g) * amount);
        b = (int) (b + (255 - b) * amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    static String normalizeRare(String displayName, String rare) {
        String value = canonicalizeRare(rare);
        if (value.isEmpty()) return "";
        boolean parcel = isParcelDisplayName(displayName);
        return switch (value) {
            case "обычный", "обычная" -> parcel ? "обычная" : "обычный";
            case "редкий", "редкая" -> "редкая";
            case "эпический", "эпическая" -> "эпический";
            case "легендарный", "легендарная" -> parcel ? "легендарная" : "легендарный";
            case "мифический", "мифическая" -> "мифическая";
            case "взрывной", "взрывная" -> "взрывной";
            case "смертельный", "смертельная" -> "смертельный";
            case "мирный", "мирная" -> "мирный";
            case "default" -> parcel ? "обычная" : "обычный";
            case "normal", "normal_plains", "normal_desert" -> parcel ? "обычная" : "обычный";
            case "rare_desert", "rare_plains" -> "редкая";
            case "epic" -> "эпический";
            case "legendary", "legendary_desert" -> parcel ? "легендарная" : "легендарный";
            case "mythical" -> "мифическая";
            case "explosive" -> "взрывной";
            case "deadly" -> "смертельный";
            case "legendary_plains" -> parcel ? "легендарная" : "легендарный";
            case "ship_default", "ship_default_f", "container_default", "container_normal", "cargo_peaceful" -> "обычный";
            case "ship_zajit", "ship_zajit_f" -> "зажиточный";
            case "ship_roskoshni", "ship_roskoshni_f" -> "роскошный";
            case "peaceful" -> "мирный";
            case "rare" -> "обычный";
            default -> rare;
        };
    }

    private static boolean isParcelDisplayName(String displayName) {
        String value = normalize(displayName);
        return value.contains("посылка") || value.contains("parcel");
    }

    private static String canonicalizeRare(String rare) {
        String value = normalize(rare);
        if (value.isEmpty()) return "";
        value = value.replaceAll("[^\\p{L}0-9]+", "_");
        value = value.replaceAll("^_+|_+$", "");
        return value.replaceAll("_+", "_");
    }

    static int parseApiNumber(String apiKey) {
        if (apiKey == null) return -1;
        int underscore = apiKey.lastIndexOf('_');
        if (underscore < 0 || underscore == apiKey.length() - 1) return -1;
        try {
            return Integer.parseInt(apiKey.substring(underscore + 1));
        } catch (NumberFormatException exception) {
            return -1;
        }
    }
}
