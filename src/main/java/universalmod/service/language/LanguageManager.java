package universalmod.service.language;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class LanguageManager {
    private static final String RESOURCE_ROOT = "assets/universalmod/lang/";
    private static final Map<LanguageCode, Map<String, String>> CACHE = new EnumMap<>(LanguageCode.class);
    private static volatile LanguageCode current = LanguageCode.RU_RU;

    private LanguageManager() {}

    public static void setCurrent(LanguageCode languageCode) { current = languageCode == null ? LanguageCode.RU_RU : languageCode; }
    public static void setCurrent(String mode) { setCurrent(LanguageCode.fromMode(mode)); }
    public static LanguageCode current() { return current; }


    public static String translate(String key) {
        if (key == null || key.isEmpty()) return "";
        Lookup hit = lookup(current, key);
        if (hit.found) return hit.value;
        hit = lookup(current, "ui." + key);
        if (hit.found) return hit.value;
        hit = lookup(current, "common.settings." + key + ".name");
        if (hit.found) return hit.value;
        hit = lookup(LanguageCode.EN_US, key);
        if (hit.found) return hit.value;
        hit = lookup(LanguageCode.EN_US, "ui." + key);
        if (hit.found) return hit.value;
        hit = lookup(LanguageCode.EN_US, "common.settings." + key + ".name");
        return hit.found ? hit.value : key;
    }

    public static String moduleName(String module) { return scoped("modules." + module + ".name", module); }
    public static String moduleDescription(String module) { return scoped("modules." + module + ".description", ""); }
    public static String categoryName(String category) { return scoped("categories." + category + ".name", category); }

    public static String settingName(String module, String setting) {
        if (setting == null || setting.isEmpty()) return "";
        String moduleKey = null;
        if (module != null && !module.isEmpty()) {
            String root = "Custom Theme".equals(module) ? "theme.settings." : "modules." + module + ".settings.";
            moduleKey = root + setting + ".name";
            Lookup hit = lookup(current, moduleKey);
            if (hit.found) return hit.value;
        }
        String commonKey = "common.settings." + setting + ".name";
        Lookup hit = lookup(current, commonKey);
        if (hit.found) return hit.value;
        if (moduleKey != null) {
            hit = lookup(LanguageCode.EN_US, moduleKey);
            if (hit.found) return hit.value;
        }
        hit = lookup(LanguageCode.EN_US, commonKey);
        return hit.found ? hit.value : setting;
    }

    public static String settingOption(String module, String setting, String option) {
        if (option == null || option.isEmpty()) return "";
        String moduleKey = null;
        if (module != null && !module.isEmpty()) {
            String root = "Custom Theme".equals(module) ? "theme.settings." : "modules." + module + ".settings.";
            moduleKey = root + setting + ".options." + option;
            Lookup hit = lookup(current, moduleKey);
            if (hit.found) return hit.value;
        }
        String commonKey = "common.settings." + setting + ".options." + option;
        Lookup hit = lookup(current, commonKey);
        if (hit.found) return hit.value;
        if (moduleKey != null) {
            hit = lookup(LanguageCode.EN_US, moduleKey);
            if (hit.found) return hit.value;
        }
        hit = lookup(LanguageCode.EN_US, commonKey);
        return hit.found ? hit.value : option;
    }

    public static String ui(String key) { return scoped("ui." + key, key); }

    public static String translateFormat(String key, Object... args) {
        String pattern = translate(key);
        try { return String.format(Locale.ROOT, pattern, args); } catch (Exception ignored) { return pattern; }
    }

    private static String scoped(String key, String fallback) {
        Lookup hit = lookup(current, key);
        if (hit.found) return hit.value;
        hit = lookup(LanguageCode.EN_US, key);
        return hit.found ? hit.value : fallback;
    }

    private static Lookup lookup(LanguageCode language, String key) {
        Map<String,String> map = translations(language);
        if (!map.containsKey(key)) return new Lookup(false, "");
        String value = map.get(key);
        if (value == null || value.equals(key) || (key.startsWith("ui.") && value.equals(key.substring(3)))) return new Lookup(false, "");
        return new Lookup(true, value);
    }

    private static Map<String, String> translations(LanguageCode languageCode) { return CACHE.computeIfAbsent(languageCode, LanguageManager::loadTranslations); }

    private static Map<String, String> loadTranslations(LanguageCode languageCode) {
        String resource = RESOURCE_ROOT + languageCode.fileName() + ".json";
        try (InputStream stream = LanguageManager.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) return Collections.emptyMap();
            JsonObject root = PARSE_EXPR.get(new InputStreamReader(stream, StandardCharsets.UTF_8));
            Map<String, String> result = new HashMap<>();
            flatten("", root, result);
            return result;
        } catch (Exception ignored) { return Collections.emptyMap(); }
    }

    private static void flatten(String prefix, JsonObject object, Map<String,String> out) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            JsonElement value = entry.getValue();
            if (value == null || value.isJsonNull()) continue;
            if (value.isJsonObject()) flatten(key, value.getAsJsonObject(), out);
            else if (value.isJsonPrimitive()) out.put(key, value.getAsString());
        }
    }

    private interface Parser { JsonObject get(InputStreamReader reader); }
    private static final Parser PARSE_EXPR = reader -> JsonParser.parseReader(reader).getAsJsonObject();
    private record Lookup(boolean found, String value) {}
}
