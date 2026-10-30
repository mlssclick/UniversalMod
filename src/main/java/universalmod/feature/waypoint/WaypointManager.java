package universalmod.feature.waypoint;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import universalmod.event.type.DrawEvent;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.render.Camera;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import universalmod.render.hud.HudAppearance;
import universalmod.mixin.accessor.GameRendererAccessor;
import universalmod.render.color.ColorUtil;
import universalmod.render.Render2D;
import universalmod.render.font.FontType;
import universalmod.storage.RepositoryStorage;
import universalmod.theme.ThemeColors;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class WaypointManager {
    private static final WaypointManager INSTANCE = new WaypointManager();
    private static final String STORAGE_FILE = "waypoints", STORAGE_KEY = "waypoints";
    private static final double MAX_DISTANCE = 131_072D;
    private static final float SCREEN_MARGIN = 8F;
    private static final UUID AUTO_EVENT_ID = UUID.fromString("e314b7ef-b916-4e1d-8dcb-5d76f3ac4014");
    private final Map<UUID, WaypointDefinition> manualWaypoints = new LinkedHashMap<>();
    private final Map<UUID, WaypointDefinition> automaticWaypoints = new LinkedHashMap<>();
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
        return manualWaypoints.values().stream().map(WaypointDefinition::copy)
                .sorted(Comparator.comparingLong(WaypointDefinition::createdAtMillis)).toList();
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
        if (event == null || event.layer() != DrawEvent.Layer.GAME) return;
        render(event.graphics(), event.partialTicks(), renderAutoEvents, renderAutoTreasures, renderDeaths);
    }

    public void render(DrawContext context, float tickDelta, boolean renderAutoEvents, boolean renderAutoTreasures) {
        render(context, tickDelta, renderAutoEvents, renderAutoTreasures, true);
    }

    public void render(DrawContext context, float tickDelta, boolean renderAutoEvents, boolean renderAutoTreasures, boolean renderDeaths) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (context == null || client.player == null || client.world == null || client.options.hudHidden || (client.currentScreen != null && !(client.currentScreen instanceof ChatScreen)))
            return;
        List<WaypointDefinition> visible = visibleWaypoints(client, renderAutoEvents, renderAutoTreasures, renderDeaths);
        if (visible.isEmpty()) return;
        Vec3d playerPos = client.player.getPos();
        int viewportWidth = Render2D.getFixedScaledWidth(), viewportHeight = Render2D.getFixedScaledHeight();
        Render2D.beginFrame(context);
        try {
            for (WaypointDefinition wp : visible) {
                Vec3d anchor = wp.anchorPosition();
                if (client.player.getEyePos().squaredDistanceTo(anchor) > MAX_DISTANCE * MAX_DISTANCE) continue;
                ScreenPoint point = project(anchor, viewportWidth, viewportHeight, tickDelta);
                if (point == null) continue;
                renderLabel(wp, playerPos, smoothPoint(wp.id(), point), viewportWidth, viewportHeight);
            }
        } finally {
            Render2D.flush();
        }
    }

    private synchronized ScreenPoint smoothPoint(UUID id, ScreenPoint target) {
        ScreenPoint prev = smoothedScreenPoints.get(id);
        if (prev == null || prev.distanceSquared(target) > 1600F) {
            smoothedScreenPoints.put(id, target);
            return target.pixelSnap();
        }
        ScreenPoint smoothed = new ScreenPoint(prev.x() + (target.x() - prev.x()) * .35F, prev.y() + (target.y() - prev.y()) * .35F);
        smoothedScreenPoints.put(id, smoothed);
        return smoothed.pixelSnap();
    }

    private synchronized List<WaypointDefinition> visibleWaypoints(MinecraftClient client, boolean renderAutoEvents, boolean renderAutoTreasures, boolean renderDeaths) {
        initialize();
        String dimension = currentDimension(client), serverKey = currentServerKey(client);
        List<WaypointDefinition> result = new ArrayList<>();
        for (WaypointDefinition wp : manualWaypoints.values()) {
            if (!renderDeaths && wp.source() == WaypointDefinition.Source.DEATH) continue;
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
        float screenX = clamp(point.x(), SCREEN_MARGIN, viewportWidth - SCREEN_MARGIN);
        float screenY = clamp(point.y(), SCREEN_MARGIN, viewportHeight - SCREEN_MARGIN);
        double dx = playerPos.x - wp.x(), dy = playerPos.y - wp.y(), dz = playerPos.z - wp.z();
        String label = wp.name() + " [" + Math.max(0, Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz))) + "m]";
        float textSize = 7.7F, iconSize = 9.5F, paddingX = 5.5F, paddingY = 3.5F, gap = 4F;
        float textWidth = Render2D.textWidth(FontType.SEMIBOLD, label, textSize);
        float textHeight = Math.max(textSize, Render2D.textHeight(FontType.SEMIBOLD, label, textSize));
        float width = paddingX * 2F + iconSize + gap + textWidth;
        float height = Math.max(iconSize + paddingY * 2F, textHeight + paddingY * 2F);
        float x = clamp(screenX - width * .5F, SCREEN_MARGIN, Math.max(SCREEN_MARGIN, viewportWidth - width - SCREEN_MARGIN));
        float y = clamp(screenY - height - 6F, SCREEN_MARGIN, Math.max(SCREEN_MARGIN, viewportHeight - height - SCREEN_MARGIN));
        int accent = switch (wp.source()) {
            case AUTO_EVENT -> ColorUtil.rgba(255, 214, 102, 235);
            case AUTO_TREASURE -> ColorUtil.rgba(92, 221, 255, 235);
            case DEATH -> ColorUtil.rgba(255, 84, 84, 235);
            case MANUAL -> ThemeColors.hudAccentColor(235);
        };
        HudAppearance.background(x, y, width, height, 5F, 8F, 1F, ColorUtil.rgba(0, 0, 0, 238));
        float pinX = x + paddingX + iconSize * .5F, pinY = y + height * .5F;
        Render2D.rect(pinX - iconSize * .32F, pinY - iconSize * .58F, iconSize * .64F, iconSize * .64F, iconSize * .22F, accent);
        Render2D.rect(pinX - 1F, pinY - iconSize * .02F, 2F, iconSize * .54F, 1F, accent);
        Render2D.text(FontType.SEMIBOLD, label, x + paddingX + iconSize + gap, y + (height - textHeight) * .5F, textSize, ThemeColors.hudTextColor(245));
    }

    private static ScreenPoint project(Vec3d position, int viewportWidth, int viewportHeight, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null || position == null) return null;
        Camera camera = client.gameRenderer.getCamera();
        if (camera == null) return null;
        Vec3d cameraPos = camera.getPos();
        double fov = ((GameRendererAccessor) client.gameRenderer).universalmod$getFov(camera, tickDelta, true);
        Matrix4f projection = client.gameRenderer.getBasicProjectionMatrix(fov);
        Matrix4f view = new Matrix4f()
                .identity()
                .rotateX((float) Math.toRadians(camera.getPitch()))
                .rotateY((float) Math.toRadians(camera.getYaw()))
                .translate((float) -cameraPos.x, (float) -cameraPos.y, (float) -cameraPos.z);
        Vector4f clip = new Vector4f((float) position.x, (float) (position.y - 2.0D * (position.y - cameraPos.y)), (float) position.z, 1.0F);
        view.transform(clip);
        projection.transform(clip);
        if (!Float.isFinite(clip.x) || !Float.isFinite(clip.y) || !Float.isFinite(clip.w) || clip.w >= 0.0F)
            return null;
        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        if (!Float.isFinite(ndcX) || !Float.isFinite(ndcY)) return null;
        return new ScreenPoint((ndcX * 0.5F + 0.5F) * viewportWidth, (1.0F - (ndcY * 0.5F + 0.5F)) * viewportHeight);
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
            double x = readDouble(o, "x", 0D), y = readDouble(o, "y", 64D), z = readDouble(o, "z", 0D);
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

    public static boolean handleGpsCommand(String raw) {
        if (raw == null) return false;
        String command = raw.trim();
        if (!command.equalsIgnoreCase(".gps") && !command.toLowerCase(Locale.ROOT).startsWith(".gps ")) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        String rest = command.length() <= 4 ? "" : command.substring(4).trim();
        if (rest.isEmpty() || rest.equalsIgnoreCase("help")) {
            usage(mc);
            return true;
        }
        String[] split = rest.split("\\s+");
        String action = split[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "add" -> {
                if (split.length < 4) {
                    local(mc, Text.literal("Usage: .gps add <x> <y> <z> [name]").formatted(Formatting.RED));
                    return true;
                }
                Double x = parseCoordinate(split[1]), y = parseCoordinate(split[2]), z = parseCoordinate(split[3]);
                if (x == null || y == null || z == null) {
                    local(mc, Text.literal("Coordinates must be numbers.").formatted(Formatting.RED));
                    return true;
                }
                String name = "Waypoint";
                if (split.length > 4) {
                    StringBuilder b = new StringBuilder();
                    for (int i = 4; i < split.length; i++) {
                        if (i > 4) b.append(' ');
                        b.append(split[i]);
                    }
                    name = b.toString().trim();
                    if (name.isBlank()) name = "Waypoint";
                }
                WaypointDefinition wp = INSTANCE.addManual(x, y, z, name, mc);
                local(mc, Text.literal("Waypoint added: ").formatted(Formatting.GREEN).append(formatWaypoint(wp)));
            }
            case "list" -> {
                List<WaypointDefinition> wps = INSTANCE.manualWaypoints();
                if (wps.isEmpty()) {
                    local(mc, Text.literal("No waypoints.").formatted(Formatting.RED));
                    return true;
                }
                local(mc, Text.literal("Waypoints:").formatted(Formatting.GREEN));
                for (WaypointDefinition wp : wps)
                    local(mc, Text.literal("- ").formatted(Formatting.DARK_GRAY).append(formatWaypoint(wp)));
            }
            case "remove", "delete", "del" -> {
                String query = rest.length() > action.length() ? rest.substring(action.length()).trim() : "";
                if (query.isBlank()) {
                    local(mc, Text.literal("Usage: .gps remove <name|uuid>").formatted(Formatting.RED));
                    return true;
                }
                int removed = INSTANCE.removeManual(query);
                local(mc, Text.literal(removed == 0 ? "Waypoint not found." : "Removed " + removed + " waypoint(s).").formatted(removed == 0 ? Formatting.RED : Formatting.GREEN));
            }
            case "clear" -> {
                boolean auto = split.length > 1 && "auto".equalsIgnoreCase(split[1]);
                int removed = auto ? INSTANCE.clearAutomatic() : INSTANCE.clearManual();
                local(mc, Text.literal("Cleared " + removed + (auto ? " auto waypoint(s)." : " waypoint(s).")).formatted(Formatting.GREEN));
            }
            default -> usage(mc);
        }
        return true;
    }

    private static Double parseCoordinate(String value) {
        try {
            double v = Double.parseDouble(value.replace(',', '.'));
            return Double.isFinite(v) ? v : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void usage(MinecraftClient mc) {
        local(mc, Text.literal("Usage: .gps add <x> <y> <z> [name]"));
        local(mc, Text.literal("Also: .gps list, .gps remove <name|uuid>, .gps clear, .gps clear auto"));
    }

    private static void local(MinecraftClient mc, Text text) {
        if (mc != null && mc.player != null) mc.player.sendMessage(text, false);
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
            return new ScreenPoint(Math.round(x * 2F) * .5F, Math.round(y * 2F) * .5F);
        }
    }
}
