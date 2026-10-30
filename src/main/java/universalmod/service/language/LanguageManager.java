package universalmod.service.language;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;


public final class LanguageManager {
    private static final String RESOURCE_ROOT = "assets/universalmod/i18n/";
    private static final Map<LanguageCode, Map<String, String>> CACHE = new EnumMap<>(LanguageCode.class);
    private static volatile LanguageCode current = LanguageCode.RU_RU;

    private LanguageManager() {
    }

    public static void setCurrent(LanguageCode languageCode) {
        current = languageCode == null ? LanguageCode.RU_RU : languageCode;
    }

    public static void setCurrent(String mode) {
        setCurrent(LanguageCode.fromMode(mode));
    }

    public static LanguageCode current() {
        return current;
    }

    public static String translate(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        Lookup hit = lookup(current, key);
        if (hit.found) {
            return hit.value;
        }
        hit = lookup(current, "ui." + key);
        if (hit.found) {
            return hit.value;
        }
        hit = lookup(current, "common.settings." + key + ".name");
        if (hit.found) {
            return hit.value;
        }
        return key;
    }

    public static String moduleName(String module) {
        return scoped("modules." + module + ".name", module);
    }

    public static String moduleDescription(String module) {
        return scoped("modules." + module + ".description", "");
    }

    public static String categoryName(String category) {
        return scoped("categories." + category + ".name", category);
    }

    public static String settingName(String module, String setting) {
        if (setting == null || setting.isEmpty()) {
            return "";
        }
        String moduleKey = null;
        if (module != null && !module.isEmpty()) {
            String root = "Custom Theme".equals(module) ? "theme.settings." : "modules." + module + ".settings.";
            moduleKey = root + setting + ".name";
            Lookup hit = lookup(current, moduleKey);
            if (hit.found) {
                return hit.value;
            }
        }
        String commonKey = "common.settings." + setting + ".name";
        Lookup hit = lookup(current, commonKey);
        if (hit.found) {
            return hit.value;
        }
        return setting;
    }

    public static String settingOption(String module, String setting, String option) {
        if (option == null || option.isEmpty()) {
            return "";
        }
        String moduleKey = null;
        if (module != null && !module.isEmpty()) {
            String root = "Custom Theme".equals(module) ? "theme.settings." : "modules." + module + ".settings.";
            moduleKey = root + setting + ".options." + option;
            Lookup hit = lookup(current, moduleKey);
            if (hit.found) {
                return hit.value;
            }
        }
        String commonKey = "common.settings." + setting + ".options." + option;
        Lookup hit = lookup(current, commonKey);
        if (hit.found) {
            return hit.value;
        }
        return option;
    }

    public static String ui(String key) {
        return scoped("ui." + key, key);
    }

    public static String translateFormat(String key, Object... args) {
        String pattern = translate(key);
        return String.format(Locale.ROOT, pattern, args);
    }

    
    public static CatalogValidation validateCatalogs() {
        Map<String, String> reference = translations(LanguageCode.EN_US);
        Set<String> referenceKeys = reference.keySet();
        Map<LanguageCode, List<String>> missing = new EnumMap<>(LanguageCode.class);
        Map<LanguageCode, List<String>> extra = new EnumMap<>(LanguageCode.class);
        Map<LanguageCode, List<String>> blank = new EnumMap<>(LanguageCode.class);

        for (LanguageCode code : LanguageCode.values()) {
            Map<String, String> catalog = translations(code);
            Set<String> missingKeys = new HashSet<>(referenceKeys);
            missingKeys.removeAll(catalog.keySet());
            Set<String> extraKeys = new HashSet<>(catalog.keySet());
            extraKeys.removeAll(referenceKeys);
            List<String> blankKeys = catalog.entrySet().stream()
                    .filter(entry -> entry.getValue() == null || entry.getValue().isBlank())
                    .map(Map.Entry::getKey)
                    .sorted()
                    .toList();
            missing.put(code, missingKeys.stream().sorted().toList());
            extra.put(code, extraKeys.stream().sorted().toList());
            blank.put(code, blankKeys);
        }

        CatalogValidation result = new CatalogValidation(Map.copyOf(missing), Map.copyOf(extra), Map.copyOf(blank));
        return result;
    }

    public static Map<String, String> catalog(LanguageCode languageCode) {
        return translations(languageCode);
    }

    private static String scoped(String key, String fallback) {
        Lookup hit = lookup(current, key);
        if (hit.found) {
            return hit.value;
        }
        return fallback;
    }

    private static Lookup lookup(LanguageCode language, String key) {
        Map<String, String> map = translations(language);
        if (!map.containsKey(key)) {
            return new Lookup(false, "");
        }
        String value = map.get(key);
        if (value == null || value.equals(key)
                || (key.startsWith("ui.") && value.equals(key.substring(3)))) {
            return new Lookup(false, "");
        }
        return new Lookup(true, value);
    }

    private static Map<String, String> translations(LanguageCode languageCode) {
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(languageCode, LanguageManager::loadTranslations);
        }
    }

    private static Map<String, String> loadTranslations(LanguageCode languageCode) {
        String resource = RESOURCE_ROOT + languageCode.fileName() + ".json";
        try (InputStream stream = LanguageManager.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing translation resource: " + resource);
            }
            JsonElement parsed = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            if (parsed == null || !parsed.isJsonObject()) {
                throw new IllegalStateException("Invalid translation resource: " + resource);
            }
            Map<String, String> result = new HashMap<>();
            flatten("", parsed.getAsJsonObject(), result);
            return Map.copyOf(result);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load translation resource: " + resource, failure);
        }
    }

    private static void flatten(String prefix, JsonObject object, Map<String, String> out) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            JsonElement value = entry.getValue();
            if (value == null || value.isJsonNull()) {
                continue;
            }
            if (value.isJsonObject()) {
                flatten(key, value.getAsJsonObject(), out);
            } else if (value.isJsonPrimitive()) {
                out.put(key, value.getAsString());
            }
        }
    }

    public record CatalogValidation(
            Map<LanguageCode, List<String>> missingKeys,
            Map<LanguageCode, List<String>> extraKeys,
            Map<LanguageCode, List<String>> blankKeys
    ) {
        public CatalogValidation {
            missingKeys = immutableLists(missingKeys);
            extraKeys = immutableLists(extraKeys);
            blankKeys = immutableLists(blankKeys);
        }

        public boolean valid() {
            return missingKeys.values().stream().allMatch(List::isEmpty)
                    && extraKeys.values().stream().allMatch(List::isEmpty)
                    && blankKeys.values().stream().allMatch(List::isEmpty);
        }

        public String summary() {
            List<String> parts = new ArrayList<>();
            for (LanguageCode code : LanguageCode.values()) {
                int missing = missingKeys.getOrDefault(code, List.of()).size();
                int extra = extraKeys.getOrDefault(code, List.of()).size();
                int blank = blankKeys.getOrDefault(code, List.of()).size();
                if (missing != 0 || extra != 0 || blank != 0) {
                    parts.add(code + " missing=" + missing + " extra=" + extra + " blank=" + blank);
                }
            }
            return parts.isEmpty() ? "ok" : String.join("; ", parts);
        }

        private static Map<LanguageCode, List<String>> immutableLists(Map<LanguageCode, List<String>> source) {
            Map<LanguageCode, List<String>> copy = new EnumMap<>(LanguageCode.class);
            source.forEach((code, keys) -> copy.put(code, List.copyOf(keys)));
            return Collections.unmodifiableMap(copy);
        }
    }

    private record Lookup(boolean found, String value) {
    }
}
