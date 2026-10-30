package universalmod.module.render;


import universalmod.feature.waypoint.WaypointDefinition;
import universalmod.feature.waypoint.WaypointManager;
import universalmod.feature.waypoint.WaypointMessageParser;
import universalmod.lifecycle.Manager;
import universalmod.event.SubscribeEvent;
import net.minecraft.client.MinecraftClient;
import universalmod.event.type.DrawEvent;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;
import universalmod.setting.BooleanSetting;

@ModuleInformation(moduleName = "Waypoints", moduleCategory = ModuleCategory.MISC)
public final class Waypoints extends Module {
    private static final long EVENT_RESPONSE_HIDE_WINDOW_MS = 2_000L;
    private static Waypoints instance;
    private final BooleanSetting autoEventWaypoints = new BooleanSetting("Auto Event WayPoints", "Creates automatic waypoints from HolyWorld event messages.", true);
    private final BooleanSetting autoTreasureWaypoints = new BooleanSetting("Auto Treasure WayPoints", "Creates automatic waypoints from HolyWorld treasure messages.", true);
    private final BooleanSetting deathWaypoints = new BooleanSetting("Death WayPoint", "Creates and shows a waypoint at the place where you died.", true);
    private String lastWorldKey = "";
    private int eventRequestDelay = -1;
    private long lastEventRequestMillis, hideEventResponsesUntilMillis;
    private int hideNextEventResponses;
    private boolean deathWaypointRecorded;

    public Waypoints() {
        instance = this;
        setEnabled(true);
        WaypointManager.getInstance().initialize();
    }

    public static Waypoints getInstance() {
        return instance;
    }

    @Override
    public void onTick(net.minecraft.client.MinecraftClient tickClient) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            lastWorldKey = "";
            eventRequestDelay = -1;
            deathWaypointRecorded = false;
            WaypointManager.getInstance().clearAutoEvents();
            return;
        }
        if (client.player.getHealth() > 0f) deathWaypointRecorded = false;
        String worldKey = WaypointManager.currentServerKey(client) + "|" + WaypointManager.currentDimension(client);
        if (!worldKey.equals(lastWorldKey)) {
            lastWorldKey = worldKey;
            WaypointManager.getInstance().clearAutoEvents();
            eventRequestDelay = shouldUseHolyWorldAuto(client) ? 20 : -1;
        }
        if (eventRequestDelay >= 0 && --eventRequestDelay <= 0) {
            eventRequestDelay = -1;
            requestEvent(client);
        }
    }

    @SubscribeEvent
    private void onDraw(DrawEvent event) {
        WaypointManager.getInstance().render(event, autoEventWaypoints.getValue(), autoTreasureWaypoints.getValue(), deathWaypoints.getValue());
    }

    public static void handleServerMessage(String message) {
        Waypoints module = instance;
        MinecraftClient client = MinecraftClient.getInstance();
        if (module == null || !module.isEnabled() || !module.shouldUseHolyWorldAuto(client)) return;
        WaypointMessageParser.ParseResult result = WaypointMessageParser.parse(message, client);
        if (!result.matched()) return;
        if (result.source() == WaypointDefinition.Source.AUTO_EVENT) {
            if (module.autoEventWaypoints.getValue())
                WaypointManager.getInstance().setAutoEvent(result.name(), result.x(), result.y(), result.z(), client);
        } else if (result.source() == WaypointDefinition.Source.AUTO_TREASURE && module.autoTreasureWaypoints.getValue())
            WaypointManager.getInstance().addAutoTreasure(result.name(), result.x(), result.y(), result.z(), client);
    }

    public static boolean shouldHideChatMessage(String message) {
        Waypoints module = instance;
        if (module == null || !module.isEnabled()) return false;
        long now = System.currentTimeMillis();
        if (now > module.hideEventResponsesUntilMillis) {
            module.hideNextEventResponses = 0;
            return false;
        }
        if (module.hideNextEventResponses > 0) {
            module.hideNextEventResponses--;
            return true;
        }
        return WaypointMessageParser.isEventResponse(message);
    }

    public static void handleDeath() {
        Waypoints module = instance;
        MinecraftClient client = MinecraftClient.getInstance();
        if (module == null || !module.isEnabled() || !module.deathWaypoints.getValue() || client.player == null || client.world == null || module.deathWaypointRecorded)
            return;
        module.deathWaypointRecorded = true;
        WaypointManager.getInstance().addDeath(client.player.getX(), client.player.getY(), client.player.getZ(), client);
    }

    private boolean shouldUseHolyWorldAuto(MinecraftClient client) {
        return client != null && client.player != null && client.world != null && WaypointManager.isHolyWorld(client) && (autoEventWaypoints.getValue() || autoTreasureWaypoints.getValue());
    }

    private void requestEvent(MinecraftClient client) {
        if (client == null || client.getNetworkHandler() == null || !shouldUseHolyWorldAuto(client)) return;
        lastEventRequestMillis = System.currentTimeMillis();
        hideEventResponsesUntilMillis = lastEventRequestMillis + EVENT_RESPONSE_HIDE_WINDOW_MS;
        hideNextEventResponses = 1;
        client.getNetworkHandler().sendChatCommand("event");
    }
}
