package universalmod.service.holyworld;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

public final class HolyWorldEventsService {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final URI EVENTS_URI = URI.create("https://api.holyworld.me/v1/events");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(4);
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final ScheduledExecutorService POLLER = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "universal-clickgui-events-poller");
            t.setDaemon(true);
            return t;
        }
                                                                                                      }
    );
    private static final String CONFIG_FILE_NAME = "universal-current-events.json";
    private static final String NEW_ANARCHY_FILTER_LABEL = "1.20 Events";
    private static final Map<String, String> DEFAULT_RANGES = Map.of("Solo", "1-17", "Duo", "18-38", "Trio", "39-57", "Clan", "58-74");
    private static final List<FilterDefinition> FILTER_DEFINITIONS = List.of(new FilterDefinition("Босс", 0xFFB861FF, new String[]{
            "босс"
    }
    ), new FilterDefinition("Тыпо", 0xFF7CFF3A, new String[]{
            "опытный типо", "опытный тыпо", "типо", "тыпо", "jaycob"
    }
    ), new FilterDefinition("Корабль", 0xFF8A5A2B, new String[]{
            "таинственный корабль", "корабль", "ship"
    }
    ), new FilterDefinition("Контейнер", 0xFF5DBBFF, new String[]{
            "контейнер", "container"
    }
    ), new FilterDefinition("Лихорадка", 0xFFF1C24A, new String[]{
            "золотая лихорадка", "лихорадка", "fever"
    }
    ), new FilterDefinition("Посылка", 0xFF8A5A2B, new String[]{
            "посылка", "parcel"
    }
    ), new FilterDefinition("Груз", 0xFFF34B4B, new String[]{
            "ценный груз", "груз", "cargo"
    }
    ), new FilterDefinition("Цветочная поляна", 0xFF4CAF50, new String[]{
            "цветочная поляна", "snowquarry", "glade"
    }
    ), new FilterDefinition("Смертельная шахта", 0xFF8B1E1E, new String[]{
            "смертельная шахта", "mine"
    }
    ), new FilterDefinition("Кубик", 0xFF5DAEFF, new String[]{
            "кубик", "cube"
    }
    ), new FilterDefinition(NEW_ANARCHY_FILTER_LABEL, 0xFFB388FF, new String[0]));
    private static final HolyWorldEventsService INSTANCE = new HolyWorldEventsService();
    private final Map<String, FilterEntry> filtersByKey = new LinkedHashMap<>();
    private final Map<String, String> ranges = new LinkedHashMap<>(DEFAULT_RANGES);
    private volatile List<LiveEvent> liveEvents = List.of();

    private HolyWorldEventsService() {
        for (FilterDefinition d : FILTER_DEFINITIONS) {
            FilterEntry e = new FilterEntry(d.label(), d.color(), d.aliases());
            filtersByKey.put(e.key(), e);
        }
        loadState();
        POLLER.scheduleWithFixedDelay(this::refreshSafely, 0L, 5L, TimeUnit.SECONDS);
    }

    public static HolyWorldEventsService getInstance() {
        return INSTANCE;
    }

    public List<EventFilterSnapshot> filterSnapshots() {
        List<EventFilterSnapshot> out = new ArrayList<>(filtersByKey.size());
        synchronized (filtersByKey) {
            for (FilterEntry e : filtersByKey.values()) out.add(new EventFilterSnapshot(e.key(), e.label, e.enabled));
        }
        return List.copyOf(out);
    }

    public List<EventSnapshot> visibleEventSnapshots() {
        List<EventSnapshot> out = new ArrayList<>();
        for (LiveEvent event : liveEvents) {
            if (!isEventEnabled(event)) continue;
            out.add(new EventSnapshot(event.id, event.displayName, normalizeRare(event.displayName, event.rare), event.apiNumber, brighten(event.color, .12f)));
        }
        return List.copyOf(out);
    }

    public void toggleFilter(String stateKey) {
        String key = normalize(stateKey);
        synchronized (filtersByKey) {
            FilterEntry e = filtersByKey.get(key);
            if (e == null) return;
            e.enabled = !e.enabled;
        }
        saveState();
    }

    public String anarchyRange(String category) {
        synchronized (ranges) {
            return ranges.getOrDefault(category, DEFAULT_RANGES.getOrDefault(category, ""));
        }
    }

    public boolean setAnarchyRange(String category, String value) {
        if (!DEFAULT_RANGES.containsKey(category) || !isValidAnarchyRange(value)) return false;
        synchronized (ranges) {
            ranges.put(category, normalizeRange(value));
        }
        saveState();
        return true;
    }

    public boolean matchesAnarchyRange(String category, int number) {
        if (number < 0) return false;
        String range = anarchyRange(category);
        int dash = range.indexOf('-');
        if (dash <= 0 || dash >= range.length() - 1) return false;
        try {
            int a = Integer.parseInt(range.substring(0, dash)), b = Integer.parseInt(range.substring(dash + 1));
            return number >= a && number <= b;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    public static boolean isValidAnarchyRange(String range) {
        String value = normalizeRange(range);
        if (!value.matches("\\d{1,3}-\\d{1,3}")) return false;
        int dash = value.indexOf('-');
        try {
            int a = Integer.parseInt(value.substring(0, dash)), b = Integer.parseInt(value.substring(dash + 1));
            return a >= 0 && a <= b;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private boolean isEventEnabled(LiveEvent event) {
        if (isNewAnarchyEvent(event.apiKey, event.id)) {
            FilterEntry e;
            synchronized (filtersByKey) {
                e = filtersByKey.get(normalize(NEW_ANARCHY_FILTER_LABEL));
            }
            if (e != null && !e.enabled) return false;
        }
        String display = normalize(event.displayName);
        synchronized (filtersByKey) {
            for (FilterEntry e : filtersByKey.values()) if (e.matches(display)) return e.enabled;
        }
        return false;
    }

    private void refreshSafely() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;
        try {
            refresh();
        } catch (Exception ignored) {
        }
    }

    private void refresh() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(EVENTS_URI).timeout(REQUEST_TIMEOUT).GET().header("Accept", "application/json").build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) return;
        JsonElement rootElement = JsonParser.parseString(response.body());
        if (rootElement == null || !rootElement.isJsonObject()) return;
        List<LiveEvent> fresh = new ArrayList<>();
        for (Map.Entry<String, JsonElement> apiEntry : rootElement.getAsJsonObject().entrySet()) {
            if (apiEntry.getValue() == null || !apiEntry.getValue().isJsonArray()) continue;
            JsonArray array = apiEntry.getValue().getAsJsonArray();
            for (JsonElement el : array) {
                if (el == null || !el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                String id = stringOrEmpty(obj, "id");
                JsonObject meta = obj.has("metadata") && obj.get("metadata").isJsonObject() ? obj.getAsJsonObject("metadata") : null;
                String display = meta == null ? "" : stringOrEmpty(meta, "displayName"), rare = meta == null ? "" : stringOrEmpty(meta, "rare");
                if (display.isEmpty()) continue;
                fresh.add(new LiveEvent(apiEntry.getKey(), id, display, rare, colorFor(apiEntry.getKey(), id, display, rare)));
            }
        }
        liveEvents = List.copyOf(fresh);
    }

    private void loadState() {
        Path file = configFile();
        if (file == null || !Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed == null || !parsed.isJsonObject()) return;
            JsonObject root = parsed.getAsJsonObject();
            if (root.has("entries") && root.get("entries").isJsonObject()) {
                JsonObject entries = root.getAsJsonObject("entries");
                synchronized (filtersByKey) {
                    for (FilterEntry e : filtersByKey.values())
                        if (entries.has(e.key()) && entries.get(e.key()).isJsonPrimitive())
                            e.enabled = entries.get(e.key()).getAsBoolean();
                }
            }
            if (root.has("anarchyRanges") && root.get("anarchyRanges").isJsonObject()) {
                JsonObject rs = root.getAsJsonObject("anarchyRanges");
                synchronized (ranges) {
                    for (String key : DEFAULT_RANGES.keySet())
                        if (rs.has(key)) {
                            String v = rs.get(key).getAsString();
                            if (isValidAnarchyRange(v)) ranges.put(key, normalizeRange(v));
                        }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void saveState() {
        Path file = configFile();
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject(), entries = new JsonObject(), rs = new JsonObject();
            synchronized (filtersByKey) {
                for (FilterEntry e : filtersByKey.values()) entries.addProperty(e.key(), e.enabled);
            }
            synchronized (ranges) {
                for (String key : DEFAULT_RANGES.keySet())
                    rs.addProperty(key, ranges.getOrDefault(key, DEFAULT_RANGES.get(key)));
            }
            root.add("entries", entries);
            root.add("anarchyRanges", rs);
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception ignored) {
        }
    }

    private static Path configFile() {
        try {
            return FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME);
        } catch (Exception e) {
            return null;
        }
    }

    private static String stringOrEmpty(JsonObject o, String key) {
        try {
            return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean isNewAnarchyEvent(String apiKey, String id) {
        return containsNewToken(apiKey) || containsNewToken(id);
    }

    private static boolean containsNewToken(String value) {
        String c = normalize(value);
        if (c.isEmpty()) return false;
        c = c.replaceAll("[^\\p{L}0-9]+", "_").replaceAll("^_+|_+$", "").replaceAll("_+", "_");
        return c.equals("new") || c.startsWith("new_") || c.endsWith("_new") || c.contains("_new_");
    }

    private static String normalizeRange(String v) {
        return v == null ? "" : v.replace(" ", "").trim();
    }

    private static String normalize(String v) {
        return v == null ? "" : v.trim().toLowerCase(Locale.ROOT);
    }

    private static int parseApiNumber(String key) {
        if (key == null) return -1;
        int u = key.lastIndexOf('_');
        if (u < 0 || u == key.length() - 1) return -1;
        try {
            return Integer.parseInt(key.substring(u + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int colorFor(String apiKey, String id, String display, String rare) {
        String k = normalize(apiKey + " " + id + " " + display + " " + rare);
        if (k.contains("лихорад") || k.contains("fever")) return 0xFFF1C24A;
        if (k.contains("кораб") || k.contains("ship") || k.contains("посыл") || k.contains("parcel")) return 0xFF8A5A2B;
        if (k.contains("груз") || k.contains("cargo")) return 0xFFF34B4B;
        if (k.contains("шахт") || k.contains("mine")) return 0xFF8B1E1E;
        if (k.contains("полян") || k.contains("glade") || k.contains("snowquarry")) return 0xFF4CAF50;
        if (k.contains("контейн") || k.contains("container")) return 0xFF5DBBFF;
        if (k.contains("кубик") || k.contains("cube")) return 0xFF5DAEFF;
        if (k.contains("типо") || k.contains("тыпо") || k.contains("jaycob")) return 0xFF7CFF3A;
        return 0xFFF3F6FA;
    }

    private static int brighten(int color, float amount) {
        amount = Math.max(0, Math.min(1, amount));
        int a = color >>> 24 & 255, r = color >>> 16 & 255, g = color >>> 8 & 255, b = color & 255;
        r = (int) (r + (255 - r) * amount);
        g = (int) (g + (255 - g) * amount);
        b = (int) (b + (255 - b) * amount);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static String normalizeRare(String displayName, String rare) {
        String v = normalize(rare).replaceAll("[^\\p{L}0-9]+", "_").replaceAll("^_+|_+$", "").replaceAll("_+", "_");
        if (v.isEmpty()) return "";
        boolean parcel = normalize(displayName).contains("посылка") || normalize(displayName).contains("parcel");
        return switch (v) {
            case "обычный", "обычная", "default", "normal", "normal_plains", "normal_desert" ->
                    parcel ? "обычная" : "обычный";
            case "редкий", "редкая", "rare_desert", "rare_plains" -> "редкая";
            case "эпический", "эпическая", "epic" -> "эпический";
            case "легендарный", "легендарная", "legendary", "legendary_desert", "legendary_plains" ->
                    parcel ? "легендарная" : "легендарный";
            case "мифический", "мифическая", "mythical" -> "мифическая";
            case "взрывной", "взрывная", "explosive" -> "взрывной";
            case "смертельный", "смертельная", "deadly" -> "смертельный";
            case "мирный", "мирная", "peaceful" -> "мирный";
            case "ship_default", "ship_default_f", "container_default", "container_normal", "cargo_peaceful" ->
                    "обычный";
            case "ship_zajit", "ship_zajit_f" -> "зажиточный";
            case "ship_roskoshni", "ship_roskoshni_f" -> "роскошный";
            case "rare" -> "обычный";
            default -> rare;
        }
                ;
    }

    private record FilterDefinition(String label, int color, String[] aliases) {
    }

    private static final class FilterEntry {
        final String label;
        final int color;
        final String[] aliases;
        boolean enabled = true;

        FilterEntry(String l, int c, String[] a) {
            label = l;
            color = c;
            aliases = a == null ? new String[0] : a;
        }

        String key() {
            return normalize(label);
        }

        boolean matches(String d) {
            if (key().equals(d)) return true;
            for (String a : aliases) if (normalize(a).equals(d)) return true;
            return false;
        }
    }

    private static final class LiveEvent {
        final String apiKey, id, displayName, rare;
        final int color, apiNumber;

        LiveEvent(String apiKey, String id, String displayName, String rare, int color) {
            this.apiKey = apiKey == null ? "" : apiKey;
            this.id = id == null ? "" : id;
            this.displayName = displayName == null ? "" : displayName;
            this.rare = rare == null ? "" : rare;
            this.color = color;
            this.apiNumber = parseApiNumber(this.apiKey);
        }
    }

    public record EventFilterSnapshot(String key, String label, boolean enabled) {
    }

    public record EventSnapshot(String id, String displayName, String rarity, int anarchyNumber, int color) {
    }
}
