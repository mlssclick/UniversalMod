package universalmod.feature.waypoint;

import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.util.math.Vector2f;
import universalmod.event.type.DrawEvent;
import universalmod.render.color.ColorUtil;
import universalmod.render.font.FontType;
import universalmod.render.Render2D;
import universalmod.theme.ThemeColors;
import universalmod.module.render.Hud;
import universalmod.render.ProjectionUtil;
import universalmod.storage.RepositoryStorage;

import java.util.*;

public final class WaypointManager {
    private static final WaypointManager INSTANCE = new WaypointManager();
    private static final String STORAGE_FILE = "waypoints", STORAGE_KEY = "waypoints";
    private static final double MAX_DISTANCE = 131_072d;
    private static final float SCREEN_MARGIN = 8f;
    private static final UUID AUTO_EVENT_ID = UUID.fromString("e314b7ef-b916-4e1d-8dcb-5d76f3ac4014");
    private final Map<UUID, WaypointDefinition> manualWaypoints = new LinkedHashMap<>(), automaticWaypoints = new LinkedHashMap<>();
    private final Map<UUID, ScreenPoint> smoothedScreenPoints = new LinkedHashMap<>();
    private boolean loaded;

    private WaypointManager() {
    }

    public static WaypointManager getInstance() {
        return INSTANCE;
    }

    public synchronized void initialize() {
        if (loaded) return;
        loaded = true;
        loadManualWaypoints();
    }

    public synchronized WaypointDefinition addManual(double x, double y, double z, String name, MinecraftClient client) {
        return addPersistent(x, y, z, name, WaypointDefinition.Source.MANUAL, client);
    }

    public synchronized WaypointDefinition addDeath(double x, double y, double z, MinecraftClient client) {
        return addPersistent(x, y, z, "Death", WaypointDefinition.Source.DEATH, client);
    }

    private synchronized WaypointDefinition addPersistent(double x, double y, double z, String name, WaypointDefinition.Source source, MinecraftClient client) {
        initialize();
        WaypointDefinition wp = new WaypointDefinition(UUID.randomUUID(), name, x, y, z, currentDimension(client), currentServerKey(client), source, System.currentTimeMillis());
        manualWaypoints.put(wp.id(), wp);
        persist();
        return wp.copy();
    }

    public synchronized int removeManual(String query) {
        initialize();
        if (query == null || query.isBlank()) return 0;
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        int removed = 0;
        Iterator<Map.Entry<UUID, WaypointDefinition>> it = manualWaypoints.entrySet().iterator();
        while (it.hasNext()) {
            WaypointDefinition wp = it.next().getValue();
            if (wp.id().toString().equalsIgnoreCase(normalized) || wp.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) persist();
        return removed;
    }

    public synchronized int clearManual() {
        initialize();
        int count = manualWaypoints.size();
        if (count > 0) {
            manualWaypoints.clear();
            persist();
        }
        return count;
    }

    public synchronized List<WaypointDefinition> manualWaypoints() {
        initialize();
        return manualWaypoints.values().stream().map(WaypointDefinition::copy).sorted(Comparator.comparingLong(WaypointDefinition::createdAtMillis)).toList();
    }

    public synchronized void setAutoEvent(String name, double x, double y, double z, MinecraftClient client) {
        automaticWaypoints.put(AUTO_EVENT_ID, new WaypointDefinition(AUTO_EVENT_ID, name, x, y, z, currentDimension(client), currentServerKey(client), WaypointDefinition.Source.AUTO_EVENT, System.currentTimeMillis()));
    }

    public synchronized void addAutoTreasure(String name, double x, double y, double z, MinecraftClient client) {
        String dimension = currentDimension(client), serverKey = currentServerKey(client);
        automaticWaypoints.values().removeIf(wp -> wp.source() == WaypointDefinition.Source.AUTO_TREASURE && wp.dimension().equals(dimension) && wp.serverKey().equals(serverKey) && Math.round(wp.x()) == Math.round(x) && Math.round(wp.z()) == Math.round(z));
        UUID id = UUID.randomUUID();
        automaticWaypoints.put(id, new WaypointDefinition(id, name, x, y, z, dimension, serverKey, WaypointDefinition.Source.AUTO_TREASURE, System.currentTimeMillis()));
    }

    public synchronized void clearAutoEvents() {
        automaticWaypoints.values().removeIf(wp -> wp.source() == WaypointDefinition.Source.AUTO_EVENT);
        smoothedScreenPoints.keySet().removeIf(id -> !manualWaypoints.containsKey(id) && !automaticWaypoints.containsKey(id));
    }

    public synchronized int clearAutomatic() {
        int count = automaticWaypoints.size();
        automaticWaypoints.clear();
        smoothedScreenPoints.keySet().removeIf(id -> !manualWaypoints.containsKey(id));
        return count;
    }

    public void render(DrawEvent event, boolean renderAutoEvents, boolean renderAutoTreasures, boolean renderDeaths) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (event == null || event.getLayer() != DrawEvent.Layer.GAME || client.player == null || client.world == null || client.options.hudHidden || (client.currentScreen != null && !(client.currentScreen instanceof ChatScreen)))
            return;
        List<WaypointDefinition> visible = visibleWaypoints(client, renderAutoEvents, renderAutoTreasures, renderDeaths);
        if (visible.isEmpty()) return;
        Vec3d playerPos = client.player.getPos();
        int viewportWidth = Render2D.getFixedScaledWidth(), viewportHeight = Render2D.getFixedScaledHeight();
        Render2D.beginFrame(event.getGraphics());
        try {
            for (WaypointDefinition wp : visible) {
                Vec3d anchor = wp.anchorPosition();
                if (client.player.getEyePos().squaredDistanceTo(anchor) > MAX_DISTANCE * MAX_DISTANCE) continue;
                ScreenPoint point = project(anchor);
                if (point == null) continue;
                renderLabel(wp, playerPos, smoothPoint(wp.id(), point), viewportWidth, viewportHeight);
            }
        } finally {
            Render2D.flush();
        }
    }

    private synchronized ScreenPoint smoothPoint(UUID id, ScreenPoint target) {
        ScreenPoint prev = smoothedScreenPoints.get(id);
        if (prev == null || prev.distanceSquared(target) > 1600f) {
            smoothedScreenPoints.put(id, target);
            return target.pixelSnap();
        }
        ScreenPoint s = new ScreenPoint(prev.x() + (target.x() - prev.x()) * .35f, prev.y() + (target.y() - prev.y()) * .35f);
        smoothedScreenPoints.put(id, s);
        return s.pixelSnap();
    }

    private synchronized List<WaypointDefinition> visibleWaypoints(MinecraftClient client, boolean renderAutoEvents, boolean renderAutoTreasures, boolean renderDeaths) {
        initialize();
        String dimension = currentDimension(client), serverKey = currentServerKey(client);
        List<WaypointDefinition> result = new ArrayList<>();
        for (WaypointDefinition wp : manualWaypoints.values()) {
            if (wp.source() == WaypointDefinition.Source.DEATH && !renderDeaths) continue;
            if (isVisibleInScope(wp, dimension, serverKey)) result.add(wp.copy());
        }
        for (WaypointDefinition wp : automaticWaypoints.values()) {
            if (wp.source() == WaypointDefinition.Source.AUTO_EVENT && !renderAutoEvents) continue;
            if (wp.source() == WaypointDefinition.Source.AUTO_TREASURE && !renderAutoTreasures) continue;
            if (wp.source() == WaypointDefinition.Source.DEATH && !renderDeaths) continue;
            if (isVisibleInScope(wp, dimension, serverKey)) result.add(wp.copy());
        }
        result.sort(Comparator.comparingLong(WaypointDefinition::createdAtMillis));
        return result;
    }

    private static boolean isVisibleInScope(WaypointDefinition wp, String dimension, String serverKey) {
        return wp.dimension().equals(dimension) && wp.serverKey().equals(serverKey);
    }

    private static void renderLabel(WaypointDefinition wp, Vec3d playerPos, ScreenPoint point, int viewportWidth, int viewportHeight) {
        float maxScreenX = Math.max(SCREEN_MARGIN, viewportWidth - SCREEN_MARGIN);
        float maxScreenY = Math.max(SCREEN_MARGIN, viewportHeight - SCREEN_MARGIN);
        float screenX = clamp(point.x(), SCREEN_MARGIN, maxScreenX), screenY = clamp(point.y(), SCREEN_MARGIN, maxScreenY);
        double dx = playerPos.x - wp.x(), dy = playerPos.y - wp.y(), dz = playerPos.z - wp.z();
        String label = wp.name() + " [" + Math.max(0, Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz))) + "m]";
        float textSize = 7.7f, iconSize = 9.5f, paddingX = 5.5f, paddingY = 3.5f, gap = 4f, textWidth = Render2D.textWidth(FontType.SEMIBOLD, label, textSize), textHeight = Math.max(textSize, Render2D.textHeight(FontType.SEMIBOLD, label, textSize)), width = paddingX * 2 + iconSize + gap + textWidth, height = Math.max(iconSize + paddingY * 2, textHeight + paddingY * 2), x = clamp(screenX - width * .5f, SCREEN_MARGIN, Math.max(SCREEN_MARGIN, viewportWidth - width - SCREEN_MARGIN)), y = clamp(screenY - height - 6f, SCREEN_MARGIN, Math.max(SCREEN_MARGIN, viewportHeight - height - SCREEN_MARGIN));
        int accent = switch (wp.source()) {
            case AUTO_EVENT -> ColorUtil.rgba(255, 214, 102, 235);
            case AUTO_TREASURE -> ColorUtil.rgba(92, 221, 255, 235);
            case DEATH -> ColorUtil.rgba(255, 84, 84, 235);
            case MANUAL -> ThemeColors.hudAccentColor(235);
        };
        Hud.renderHudBackground(x, y, width, height, 5f, 8f, 1f, ColorUtil.rgba(0, 0, 0, 238));
        float pinX = x + paddingX + iconSize * .5f, pinY = y + height * .5f;
        Render2D.rect(pinX - iconSize * .32f, pinY - iconSize * .58f, iconSize * .64f, iconSize * .64f, iconSize * .22f, accent);
        Render2D.rect(pinX - 1f, pinY - iconSize * .02f, 2f, iconSize * .54f, 1f, accent);
        Render2D.text(FontType.SEMIBOLD, label, x + paddingX + iconSize + gap, y + (height - textHeight) * .5f, textSize, ThemeColors.hudTextColor(245));
    }

    private static ScreenPoint project(Vec3d position) {
        Vector2f projected = ProjectionUtil.project(position);
        float x = projected == null ? Float.NaN : projected.getX();
        float y = projected == null ? Float.NaN : projected.getY();
        if (!Float.isFinite(x) || !Float.isFinite(y) || x == Float.MAX_VALUE || y == Float.MAX_VALUE) return null;
        return new ScreenPoint(Render2D.guiToFixed(x), Render2D.guiToFixed(y));
    }

    public static String currentDimension(MinecraftClient client) {
        if (client == null || client.world == null) return "minecraft:overworld";
        return WaypointDefinition.sanitizeDimension(client.world.getRegistryKey().getValue().toString());
    }

    public static String currentServerKey(MinecraftClient client) {
        if (client == null) return "singleplayer";
        ServerInfo server = client.getCurrentServerEntry();
        if (server != null && server.address != null && !server.address.isBlank())
            return server.address.trim().toLowerCase(Locale.ROOT);
        return client.isInSingleplayer() ? "singleplayer" : "unknown";
    }

    public static boolean isHolyWorld(MinecraftClient client) {
        return currentServerKey(client).contains("holyworld");
    }

    private void loadManualWaypoints() {
        manualWaypoints.clear();
        JsonObject root = RepositoryStorage.readObject(STORAGE_FILE);
        JsonArray array = root.has(STORAGE_KEY) && root.get(STORAGE_KEY).isJsonArray() ? root.getAsJsonArray(STORAGE_KEY) : new JsonArray();
        for (JsonElement el : array) {
            if (!el.isJsonObject()) continue;
            WaypointDefinition wp = fromJson(el.getAsJsonObject());
            if (wp != null && (wp.source() == WaypointDefinition.Source.MANUAL || wp.source() == WaypointDefinition.Source.DEATH))
                manualWaypoints.put(wp.id(), wp);
        }
    }

    private void persist() {
        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();
        for (WaypointDefinition wp : manualWaypoints.values()) array.add(toJson(wp));
        root.add(STORAGE_KEY, array);
        RepositoryStorage.write(STORAGE_FILE, root);
    }

    private static JsonObject toJson(WaypointDefinition wp) {
        JsonObject o = new JsonObject();
        o.addProperty("id", wp.id().toString());
        o.addProperty("name", wp.name());
        o.addProperty("x", wp.x());
        o.addProperty("y", wp.y());
        o.addProperty("z", wp.z());
        o.addProperty("dimension", wp.dimension());
        o.addProperty("serverKey", wp.serverKey());
        o.addProperty("source", wp.source().name());
        o.addProperty("createdAtMillis", wp.createdAtMillis());
        return o;
    }

    private static WaypointDefinition fromJson(JsonObject o) {
        try {
            UUID id = UUID.fromString(readString(o, "id", UUID.randomUUID().toString()));
            String name = readString(o, "name", "Waypoint");
            double x = readDouble(o, "x", 0), y = readDouble(o, "y", 64), z = readDouble(o, "z", 0);
            String dimension = readString(o, "dimension", "minecraft:overworld"), serverKey = readString(o, "serverKey", "unknown");
            long created = readLong(o, "createdAtMillis", System.currentTimeMillis());
            WaypointDefinition.Source source;
            try {
                source = WaypointDefinition.Source.valueOf(readString(o, "source", WaypointDefinition.Source.MANUAL.name()));
            } catch (RuntimeException ignored) {
                source = WaypointDefinition.Source.MANUAL;
            }
            if (source != WaypointDefinition.Source.DEATH) source = WaypointDefinition.Source.MANUAL;
            return new WaypointDefinition(id, name, x, y, z, dimension, serverKey, source, created);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String readString(JsonObject o, String key, String fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
    }

    private static double readDouble(JsonObject o, String key, double fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsDouble() : fallback;
    }

    private static long readLong(JsonObject o, String key, long fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsLong() : fallback;
    }

    public static Text formatWaypoint(WaypointDefinition wp) {
        MutableText t = Text.literal(wp.name()).formatted(Formatting.AQUA);
        return t.append(Text.literal(" [" + Math.round(wp.x()) + " " + Math.round(wp.y()) + " " + Math.round(wp.z()) + "]").formatted(Formatting.GRAY));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private record ScreenPoint(float x, float y) {
        private float distanceSquared(ScreenPoint other) {
            float dx = x - other.x, dy = y - other.y;
            return dx * dx + dy * dy;
        }

        private ScreenPoint pixelSnap() {
            return new ScreenPoint(Math.round(x * 2f) * .5f, Math.round(y * 2f) * .5f);
        }
    }
}
