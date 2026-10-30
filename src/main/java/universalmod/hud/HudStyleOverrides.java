package universalmod.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import universalmod.config.ConfigManager;
import universalmod.hud.registry.HudElementId;
import universalmod.storage.json.JsonDocumentStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;


public final class HudStyleOverrides {
    public static final float SIZE_MIN_PERCENT = 35.0F;
    public static final float SIZE_MAX_PERCENT = 250.0F;
    public static final float SIZE_DEFAULT_PERCENT = 100.0F;


    public static final float VISUAL_BASE_SCALE = 1.30F;
    private static final int FORMAT_VERSION = 6;

    private static final String FILE_NAME = "hud_style_overrides" + ConfigManager.CONFIG_EXTENSION;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final JsonDocumentStore STORE = new JsonDocumentStore(GSON);
    private static final HudStyleOverrides INSTANCE = new HudStyleOverrides();

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private boolean loaded;

    private HudStyleOverrides() {
    }

    public static HudStyleOverrides getInstance() {
        INSTANCE.ensureLoaded();
        return INSTANCE;
    }

    public float getSizePercent(HudElementId elementId) {
        if (elementId == null) {
            return SIZE_DEFAULT_PERCENT;
        }
        return getSizePercent(elementId.persistentId());
    }

    public float getSizePercent(String elementId) {
        Entry entry = entry(elementId);
        return entry == null ? SIZE_DEFAULT_PERCENT : normalizeSizePercent(entry.sizePercent);
    }

    public void setSizePercent(HudElementId elementId, float sizePercent) {
        if (elementId != null) {
            setSizePercentInternal(elementId.persistentId(), sizePercent, true);
        }
    }

    public void setSizePercent(String elementId, float sizePercent) {
        setSizePercentInternal(elementId, sizePercent, true);
    }

    public void previewSizePercent(HudElementId elementId, float sizePercent) {
        if (elementId != null) {
            setSizePercentInternal(elementId.persistentId(), sizePercent, false);
        }
    }

    public void previewSizePercent(String elementId, float sizePercent) {
        setSizePercentInternal(elementId, sizePercent, false);
    }

    public void commitSizePercent(HudElementId elementId) {
        if (elementId != null) {
            commitSizePercent(elementId.persistentId());
        }
    }

    public void commitSizePercent(String elementId) {
        ensureLoaded();
        String key = normalize(elementId);
        Entry entry = entries.get(key);
        if (entry != null) {
            entry.sizePercent = normalizeSizePercent(entry.sizePercent);
            pruneIfDefault(key, entry);
        }
        save();
    }

    private void setSizePercentInternal(String elementId, float sizePercent, boolean persist) {
        ensureLoaded();
        String key = normalize(elementId);
        Entry entry = editableEntry(key);
        entry.sizePercent = normalizeSizePercent(sizePercent);
        if (persist) {
            pruneIfDefault(key, entry);
            save();
        }
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path file = configFile();
        if (!Files.exists(file)) {
            return;
        }
        boolean migrated = false;
        try {
            JsonObject root = STORE.readObject(file);
            if (root == null || !root.has("entries") || !root.get("entries").isJsonObject()) {
                return;
            }
            int version = root.has("version") && root.get("version").isJsonPrimitive()
                    ? root.get("version").getAsInt()
                    : 1;
            if (version < FORMAT_VERSION) {
                migrated = true;
            }
            JsonObject object = root.getAsJsonObject("entries");
            for (String key : object.keySet()) {
                Entry entry = GSON.fromJson(object.get(key), Entry.class);
                if (entry == null) {
                    continue;
                }
                String normalizedKey = normalize(key);
                if (!normalizedKey.equals(normalizeRaw(key))) {
                    migrated = true;
                }
                float value = normalizeSizePercent(entry.sizePercent);
                if (version <= 3) {


                    if (!usesOriginalVisualBase(normalizedKey)) {
                        value = normalizeSizePercent(value / VISUAL_BASE_SCALE);
                    }
                    migrated = true;
                } else if (version == 4 && usesOriginalVisualBase(normalizedKey)) {


                    value = normalizeSizePercent(value * VISUAL_BASE_SCALE);
                    migrated = true;
                }
                entry.sizePercent = value;
                if (Math.abs(value - SIZE_DEFAULT_PERCENT) >= 0.001F) {
                    entries.put(normalizedKey, entry);
                }
            }
        } catch (IOException | RuntimeException exception) {
            return;
        }
        if (migrated) {
            save();
        }
    }

    private void save() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", FORMAT_VERSION);
            JsonObject object = new JsonObject();
            for (Map.Entry<String, Entry> entry : entries.entrySet()) {
                object.add(entry.getKey(), GSON.toJsonTree(entry.getValue()));
            }
            root.add("entries", object);
            STORE.writeObject(configFile(), root);
        } catch (IOException exception) {
            
        }
    }

    private Entry entry(String elementId) {
        ensureLoaded();
        return entries.get(normalize(elementId));
    }

    private Entry editableEntry(String key) {
        return entries.computeIfAbsent(key, ignored -> new Entry());
    }

    private void pruneIfDefault(String key, Entry entry) {
        if (entry != null && Math.abs(normalizeSizePercent(entry.sizePercent) - SIZE_DEFAULT_PERCENT) < 0.001F) {
            entries.remove(key);
        }
    }

    private static float normalizeSizePercent(float value) {
        if (!Float.isFinite(value) || value <= 0.0F) {
            return SIZE_DEFAULT_PERCENT;
        }
        return Math.max(SIZE_MIN_PERCENT, Math.min(SIZE_MAX_PERCENT, Math.round(value)));
    }


    private static boolean usesOriginalVisualBase(String key) {
        return HudElementId.fromPersistentOrLegacy(stripHudPrefix(key))
                .map(id -> id == HudElementId.BOSSBAR
                        || id == HudElementId.EVENT
                        || id == HudElementId.KEYSTROKES
                        || id == HudElementId.TNT_TIMER)
                .orElse(false);
    }

    static String normalize(String id) {
        String raw = normalizeRaw(id);
        String withoutPrefix = stripHudPrefix(raw);
        return HudElementId.fromPersistentOrLegacy(withoutPrefix)
                .map(HudElementId::persistentId)
                .orElse(raw);
    }

    private static String normalizeRaw(String id) {
        return id == null ? "" : id.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String stripHudPrefix(String id) {
        String normalized = normalizeRaw(id);
        return normalized.startsWith("hud.") ? normalized.substring("hud.".length()) : normalized;
    }

    private static Path configFile() {
        return ConfigManager.systemDirectory().resolve(FILE_NAME);
    }

    private static final class Entry {
        private float sizePercent = SIZE_DEFAULT_PERCENT;
    }
}
