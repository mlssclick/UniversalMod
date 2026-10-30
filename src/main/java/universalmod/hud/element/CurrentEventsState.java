package universalmod.hud.element;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import universalmod.config.ConfigManager;
import universalmod.service.language.LanguageCode;
import universalmod.service.language.LanguageManager;
import universalmod.storage.json.JsonDocumentStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CurrentEventsState {
    static final String NEW_ANARCHY_FILTER_ID = "anarchy_120";

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "current_events" + ConfigManager.CONFIG_EXTENSION;
    private static final JsonDocumentStore STORE = new JsonDocumentStore(GSON);
    private static final Map<String, String> DEFAULT_ANARCHY_RANGES = Map.of(
            "Solo", "1-17",
            "Duo", "18-38",
            "Trio", "39-57",
            "Clan", "58-74"
    );
    private static final List<String> ANARCHY_CATEGORIES = List.of("Solo", "Duo", "Trio", "Clan");

    private final Map<String, CurrentEventsPanel.FilterEntry> filtersByName = new LinkedHashMap<>();
    private final List<CurrentEventsPanel.FilterEntry> filters;
    private final Map<String, Boolean> savedEnabledStates = new LinkedHashMap<>();
    private final Map<String, String> anarchyRanges = new LinkedHashMap<>(DEFAULT_ANARCHY_RANGES);
    private final Object stateLock = new Object();

    private volatile List<CurrentEventsPanel.LiveEvent> liveEvents = List.of();
    private List<CurrentEventsPanel.LiveEvent> visibleEventsCache = List.of();
    private List<CurrentEventsPanel.LiveEvent> visibleEventsSource = List.of();
    private long filterRevision;
    private long visibleEventsRevision = Long.MIN_VALUE;

    private long filterSnapshotsRevision = Long.MIN_VALUE;
    private LanguageCode filterSnapshotsLanguage;
    private List<CurrentEventsPanel.EventFilterSnapshot> filterSnapshotsCache = List.of();
    private List<CurrentEventsPanel.LiveEvent> eventSnapshotsSource = List.of();
    private List<CurrentEventsPanel.EventSnapshot> eventSnapshotsCache = List.of();

    private boolean loaded;

    CurrentEventsState(List<CurrentEventsPanel.FilterEntry> filterEntries) {
        for (CurrentEventsPanel.FilterEntry entry : filterEntries) {
            filtersByName.put(entry.stateKey(), entry);
        }
        filters = List.copyOf(filtersByName.values());
        loadState();
        applySavedStates();
    }

    void updateLiveEvents(List<CurrentEventsPanel.LiveEvent> events) {
        liveEvents = events == null ? List.of() : events;
    }

    List<CurrentEventsPanel.FilterEntry> filters() {
        return filters;
    }

    List<CurrentEventsPanel.EventFilterSnapshot> filterSnapshots() {
        long revision = filterRevision;
        LanguageCode language = LanguageManager.current();
        if (filterSnapshotsRevision == revision && filterSnapshotsLanguage == language) {
            return filterSnapshotsCache;
        }

        List<CurrentEventsPanel.EventFilterSnapshot> snapshots = new ArrayList<>(filters.size());
        for (CurrentEventsPanel.FilterEntry entry : filters) {
            snapshots.add(new CurrentEventsPanel.EventFilterSnapshot(entry.stateKey(), entry.label(), entry.enabled));
        }
        filterSnapshotsRevision = revision;
        filterSnapshotsLanguage = language;
        filterSnapshotsCache = List.copyOf(snapshots);
        return filterSnapshotsCache;
    }

    List<CurrentEventsPanel.EventSnapshot> visibleEventSnapshots() {
        List<CurrentEventsPanel.LiveEvent> current = visibleEvents();
        if (current == eventSnapshotsSource) {
            return eventSnapshotsCache;
        }

        List<CurrentEventsPanel.EventSnapshot> snapshots = new ArrayList<>(current.size());
        for (CurrentEventsPanel.LiveEvent event : current) {
            snapshots.add(new CurrentEventsPanel.EventSnapshot(
                    event.id,
                    event.displayName,
                    event.rarityLabel(),
                    event.apiNumber,
                    CurrentEventsRules.brighten(event.color, 0.12F)
            ));
        }
        eventSnapshotsSource = current;
        eventSnapshotsCache = List.copyOf(snapshots);
        return eventSnapshotsCache;
    }

    List<CurrentEventsPanel.LiveEvent> visibleEvents() {
        List<CurrentEventsPanel.LiveEvent> currentEvents = liveEvents;
        long revision = filterRevision;
        if (currentEvents == visibleEventsSource && revision == visibleEventsRevision) {
            return visibleEventsCache;
        }
        if (currentEvents.isEmpty()) {
            visibleEventsSource = currentEvents;
            visibleEventsRevision = revision;
            visibleEventsCache = List.of();
            return visibleEventsCache;
        }

        List<CurrentEventsPanel.LiveEvent> visible = new ArrayList<>(currentEvents.size());
        for (CurrentEventsPanel.LiveEvent event : currentEvents) {
            if (isEventEnabled(event.apiKey, event.id, event.displayName)) {
                visible.add(event);
            }
        }
        visibleEventsSource = currentEvents;
        visibleEventsRevision = revision;
        visibleEventsCache = List.copyOf(visible);
        return visibleEventsCache;
    }

    void toggleFilter(String stateKey) {
        String normalized = CurrentEventsRules.normalize(stateKey);
        CurrentEventsPanel.FilterEntry entry = filtersByName.get(normalized);
        if (entry != null) {
            toggleEntry(entry);
        }
    }

    void toggleEntry(CurrentEventsPanel.FilterEntry entry) {
        if (entry == null || filtersByName.get(entry.stateKey()) != entry) {
            return;
        }

        entry.enabled = !entry.enabled;
        filterRevision++;
        synchronized (stateLock) {
            savedEnabledStates.put(entry.stateKey(), entry.enabled);
        }
        saveState();
    }

    boolean isEnabled(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return false;
        }

        String normalized = CurrentEventsRules.normalize(displayName);
        for (CurrentEventsPanel.FilterEntry entry : filters) {
            if (entry.matches(normalized)) {
                return entry.enabled;
            }
        }
        return false;
    }

    String anarchyRange(String category) {
        synchronized (stateLock) {
            return anarchyRanges.getOrDefault(category, DEFAULT_ANARCHY_RANGES.getOrDefault(category, ""));
        }
    }

    boolean setAnarchyRange(String category, String range) {
        if (!DEFAULT_ANARCHY_RANGES.containsKey(category) || !CurrentEventsRules.isValidRange(range)) {
            return false;
        }
        String normalized = CurrentEventsRules.normalizeRange(range);
        synchronized (stateLock) {
            if (normalized.equals(anarchyRanges.get(category))) {
                return true;
            }
            anarchyRanges.put(category, normalized);
        }
        saveState();
        return true;
    }

    boolean matchesAnarchyRange(String category, int anarchyNumber) {
        return CurrentEventsRules.matchesRange(anarchyRange(category), anarchyNumber);
    }

    private boolean isEventEnabled(String apiKey, String id, String displayName) {
        if (CurrentEventsRules.isNewAnarchyEvent(apiKey, id) && !isNewAnarchyFilterEnabled()) {
            return false;
        }
        return isEnabled(displayName);
    }

    private boolean isNewAnarchyFilterEnabled() {
        CurrentEventsPanel.FilterEntry entry = filtersByName.get(NEW_ANARCHY_FILTER_ID);
        return entry == null || entry.enabled;
    }

    private void loadState() {
        if (loaded) {
            return;
        }
        loaded = true;

        Path source = configFile();

        boolean legacy = !Files.exists(source);
        if (legacy) {
            source = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                    .resolve("universalmod-current-events.json");
            if (!Files.exists(source)) {
                return;
            }
        }

        try {
            JsonObject root = STORE.readObject(source);
            if (root == null) {
                return;
            }
            synchronized (stateLock) {
                loadEnabledStates(root);
                if (legacy) {
                    migrateLegacyFilterKeys();
                }
                loadAnarchyRanges(root);
            }
        } catch (IOException | RuntimeException failure) {

        }
    }

    private void loadEnabledStates(JsonObject root) {
        if (!root.has("entries") || !root.get("entries").isJsonObject()) {
            return;
        }
        JsonObject entriesObject = root.getAsJsonObject("entries");
        savedEnabledStates.clear();
        for (Map.Entry<String, JsonElement> entry : entriesObject.entrySet()) {
            if (entry.getValue() != null && entry.getValue().isJsonPrimitive()) {
                savedEnabledStates.put(CurrentEventsRules.normalize(entry.getKey()), entry.getValue().getAsBoolean());
            }
        }
    }

    private void loadAnarchyRanges(JsonObject root) {
        if (!root.has("anarchyRanges") || !root.get("anarchyRanges").isJsonObject()) {
            return;
        }
        JsonObject rangesObject = root.getAsJsonObject("anarchyRanges");
        for (String category : DEFAULT_ANARCHY_RANGES.keySet()) {
            if (!rangesObject.has(category) || !rangesObject.get(category).isJsonPrimitive()) {
                continue;
            }
            String range = rangesObject.get(category).getAsString();
            if (CurrentEventsRules.isValidRange(range)) {
                anarchyRanges.put(category, CurrentEventsRules.normalizeRange(range));
            }
        }
    }

    private void migrateLegacyFilterKeys() {
        for (CurrentEventsPanel.FilterEntry entry : filters) {
            if (savedEnabledStates.containsKey(entry.stateKey())) {
                continue;
            }
            Boolean old = savedEnabledStates.get(entry.legacyStateKey());
            if (old == null && NEW_ANARCHY_FILTER_ID.equals(entry.stateKey())) {
                old = savedEnabledStates.get("1.20.1 anarchy");
            }
            if (old != null) {
                savedEnabledStates.put(entry.stateKey(), old);
            }
        }
    }

    private void applySavedStates() {
        synchronized (stateLock) {
            for (CurrentEventsPanel.FilterEntry entry : filters) {
                Boolean saved = savedEnabledStates.get(entry.stateKey());
                if (saved != null) {
                    entry.enabled = saved;
                }
            }
        }
        filterRevision++;
    }

    private void saveState() {
        Path file = configFile();
        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", 3);
            JsonObject entriesObject = new JsonObject();
            JsonObject rangesObject = new JsonObject();
            synchronized (stateLock) {
                for (Map.Entry<String, Boolean> entry : savedEnabledStates.entrySet()) {
                    entriesObject.addProperty(entry.getKey(), entry.getValue());
                }
                for (String category : ANARCHY_CATEGORIES) {
                    rangesObject.addProperty(category, anarchyRanges.getOrDefault(category,
                            DEFAULT_ANARCHY_RANGES.get(category)));
                }
            }
            root.add("entries", entriesObject);
            root.add("anarchyRanges", rangesObject);
            STORE.writeObject(file, root);
        } catch (IOException | RuntimeException failure) {

        }
    }

    private static Path configFile() {
        return ConfigManager.systemDirectory().resolve(CONFIG_FILE_NAME);
    }

}
