package universalmod.module.misc.serverhelper;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import universalmod.mixin.accessor.BossHealthOverlayAccessor;
import universalmod.module.misc.ServerHelper;
import universalmod.service.language.LanguageCode;
import universalmod.service.language.LanguageManager;

public final class TrapDetectionService {
    private static final long PENDING_CONFIRM_MS = 2_000L;
    private static final double TELEPORT_DISTANCE_SQUARED = 64.0D * 64.0D;
    private static final int TELEPORT_GRACE_TICKS = 20;

    private static final List<TrackedZone> ACTIVE_ZONES = new ArrayList<>();
    private static final List<PendingUse> PENDING_USES = new ArrayList<>();
    private static final Map<TrapType, Integer> LAST_ITEM_COUNTS = new java.util.EnumMap<>(TrapType.class);

    private static boolean initialized;
    private static Vec3 lastPlayerPos;
    private static ResourceKey<Level> lastWorldKey;
    private static int inventoryUsePauseTicks;

    private TrapDetectionService() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            pruneExpired();
            detectInventoryUses(client);
            updateBossbars(client);
        });

        UseItemCallback.EVENT.register((player, level, hand) -> {
            rememberPendingUse(player, player.getItemInHand(hand), entityPos(player));
            return InteractionResult.PASS;
        });

        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            Vec3 position = hitResult == null ? entityPos(player) : hitResult.getLocation();
            rememberPendingUse(player, player.getItemInHand(hand), position);
            return InteractionResult.PASS;
        });
    }

    public static void reset() {
        removeAllBossbars(Minecraft.getInstance());
        ACTIVE_ZONES.clear();
        PENDING_USES.clear();
        LAST_ITEM_COUNTS.clear();
        lastPlayerPos = null;
        lastWorldKey = null;
        inventoryUsePauseTicks = 0;
    }

    public static void recordFinishedUse(Player player) {
        if (player == null) {
            return;
        }
        rememberPendingUse(player, player.getUseItem(), entityPos(player));
    }


    public static void handleSoundPacket(ClientboundSoundPacket packet) {
        Minecraft client = Minecraft.getInstance();
        if (packet == null || !ServerHelper.isTrackerEnabled() || !isHolyWorld(client) || client.player == null) {
            return;
        }

        String sound = packet.getSound().value().location().toString();
        float volume = packet.getVolume();
        float pitch = packet.getPitch();


        Vec3 packetBlock = new Vec3((int) packet.getX(), (int) packet.getY(), (int) packet.getZ());

        if (sound.contains("minecraft:entity.generic.explode") && (volume == 1.0F || pitch == 1.0F)) {
            PendingUse localUse = takeMostRecentPendingNear(packetBlock, 100.0D, TrapType.STUN, TrapType.EXPLOSION_TRAP);
            if (localUse == null) {
                return;
            }

            if (localUse.type == TrapType.STUN) {
                recordDetected(TrapType.STUN, packetBlock);
            } else if (localUse.type == TrapType.EXPLOSION_TRAP) {

                recordDetected(TrapType.EXPLOSION_TRAP, packetBlock.add(0.0D, 1.0D, 0.0D));
            }
            return;
        }

        if (sound.contains("minecraft:block.beacon.deactivate") && (volume == 1.5F || pitch == 1.0F)) {
            PendingUse stunUse = takePendingNear(TrapType.STUN, packetBlock, 100.0D);
            if (stunUse != null) {
                recordDetected(TrapType.STUN, packetBlock);
            }
        }
    }

    public static AABB createPreviewBox(Vec3 position, TrapType type) {
        if (position == null || type == null) {
            return null;
        }


        double baseY = Math.floor(position.y);
        return new AABB(
                position.x - type.previewRadius,
                baseY,
                position.z - type.previewRadius,
                position.x + type.previewRadius,
                baseY + type.previewHeight,
                position.z + type.previewRadius
        );
    }

    public static TrapType detectType(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }


        String liteTrapsType = readLiteTrapsType(stack);
        if (liteTrapsType != null) {
            return switch (liteTrapsType) {
                case "default" -> TrapType.TRAPKA;
                case "custom" -> TrapType.EXPLOSION_TRAP;
                case "stun" -> TrapType.STUN;
                default -> null;
            };
        }


        if (stack.getItem() == Items.POPPED_CHORUS_FRUIT) {
            return TrapType.TRAPKA;
        }
        if (stack.getItem() == Items.PRISMARINE_SHARD) {
            return TrapType.EXPLOSION_TRAP;
        }
        if (stack.getItem() == Items.NETHER_STAR) {
            return TrapType.STUN;
        }
        return null;
    }

    private static String readLiteTrapsType(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            return null;
        }
        CompoundTag root = customData.copyTag();
        if (!root.contains("PublicBukkitValues")) {
            return null;
        }
        CompoundTag values = root.getCompoundOrEmpty("PublicBukkitValues");
        String value = values.getString("litetraps:item").orElse(null);
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    public static List<TrackedZone> getActiveZones() {
        pruneExpired();
        return new ArrayList<>(ACTIVE_ZONES);
    }

    private static void rememberPendingUse(Player player, ItemStack stack, Vec3 position) {
        Minecraft client = Minecraft.getInstance();
        if (player == null || player != client.player || stack == null || stack.isEmpty() || position == null
                || !ServerHelper.isTrackerEnabled() || !isHolyWorld(client)) {
            return;
        }

        TrapType type = detectType(stack);
        if (type == null || isOnCooldown(player, stack)) {
            return;
        }

        long now = System.currentTimeMillis();

        PENDING_USES.add(new PendingUse(type, position, now, now + PENDING_CONFIRM_MS));
    }

    private static boolean isOnCooldown(Player player, ItemStack stack) {
        return player != null && stack != null && !stack.isEmpty() && player.getCooldowns().isOnCooldown(stack);
    }

    private static AABB createActiveBox(Vec3 position, TrapType type) {


        return new AABB(
                position.x - type.activeHalfSize,
                position.y - type.activeHalfSize,
                position.z - type.activeHalfSize,
                position.x + type.activeHalfSize,
                position.y + type.activeHalfSize,
                position.z + type.activeHalfSize
        );
    }

    private static void pruneExpired() {
        long now = System.currentTimeMillis();
        for (Iterator<TrackedZone> iterator = ACTIVE_ZONES.iterator(); iterator.hasNext(); ) {
            TrackedZone zone = iterator.next();
            if (zone.expiresAt <= now) {
                removeBossbar(Minecraft.getInstance(), zone);
                iterator.remove();
            }
        }
        PENDING_USES.removeIf(use -> use.expiresAt < now);
    }

    private static void detectInventoryUses(Minecraft client) {
        if (client == null || client.player == null || !ServerHelper.isTrackerEnabled()) {
            LAST_ITEM_COUNTS.clear();
            PENDING_USES.clear();
            return;
        }

        updateInventoryUsePause(client.player);
        long now = System.currentTimeMillis();

        for (TrapType type : TrapType.values()) {
            int currentCount = countType(client.player, type);
            Integer previousCount = LAST_ITEM_COUNTS.put(type, currentCount);
            if (previousCount == null || inventoryUsePauseTicks > 0) {
                continue;
            }

            int used = previousCount - currentCount;
            if (used <= 0) {
                continue;
            }


            for (int index = 0; index < used; index++) {
                PendingUse pending = takeOldestPending(type, now);
                if (pending == null) {
                    break;
                }


                recordDetected(type, pending.position);
            }
        }
    }

    private static void updateInventoryUsePause(Player player) {
        ResourceKey<Level> currentWorldKey = player.level() == null ? null : player.level().dimension();
        Vec3 currentPos = entityPos(player);

        if (lastWorldKey != null && currentWorldKey != null && !lastWorldKey.equals(currentWorldKey)) {
            inventoryUsePauseTicks = TELEPORT_GRACE_TICKS;
            LAST_ITEM_COUNTS.clear();
        } else if (lastPlayerPos != null && currentPos.distanceToSqr(lastPlayerPos) > TELEPORT_DISTANCE_SQUARED) {
            inventoryUsePauseTicks = TELEPORT_GRACE_TICKS;
            LAST_ITEM_COUNTS.clear();
        } else if (inventoryUsePauseTicks > 0) {
            inventoryUsePauseTicks--;
        }

        lastWorldKey = currentWorldKey;
        lastPlayerPos = currentPos;
    }

    private static int countType(Player player, TrapType type) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack != null && detectType(stack) == type) {
                count += stack.getCount();
            }
        }
        ItemStack offhand = player.getOffhandItem();
        if (offhand != null && detectType(offhand) == type) {
            count += offhand.getCount();
        }
        return count;
    }

    private static PendingUse takeMostRecentPendingNear(Vec3 soundPosition, double maxDistanceSquared, TrapType... allowedTypes) {
        long now = System.currentTimeMillis();
        PendingUse best = null;
        double bestDistance = Double.MAX_VALUE;
        for (PendingUse use : PENDING_USES) {
            if (use.expiresAt < now || !containsType(allowedTypes, use.type)) {
                continue;
            }
            double distance = use.position.distanceToSqr(soundPosition);
            if (distance > maxDistanceSquared) {
                continue;
            }
            if (best == null || use.createdAt > best.createdAt || (use.createdAt == best.createdAt && distance < bestDistance)) {
                best = use;
                bestDistance = distance;
            }
        }
        if (best != null) {
            PENDING_USES.remove(best);
        }
        return best;
    }

    private static boolean containsType(TrapType[] types, TrapType type) {
        if (types == null) {
            return false;
        }
        for (TrapType candidate : types) {
            if (candidate == type) {
                return true;
            }
        }
        return false;
    }

    private static PendingUse takePendingNear(TrapType type, Vec3 soundPosition, double maxDistanceSquared) {
        long now = System.currentTimeMillis();
        PendingUse best = null;
        double bestDistance = Double.MAX_VALUE;
        for (PendingUse use : PENDING_USES) {
            if (use.type != type || use.expiresAt < now) {
                continue;
            }
            double distance = use.position.distanceToSqr(soundPosition);
            if (distance <= maxDistanceSquared && distance < bestDistance) {
                best = use;
                bestDistance = distance;
            }
        }
        if (best != null) {
            PENDING_USES.remove(best);
        }
        return best;
    }

    private static PendingUse takeOldestPending(TrapType type, long now) {
        PendingUse best = null;
        for (PendingUse use : PENDING_USES) {
            if (use.type != type || use.expiresAt < now) {
                continue;
            }
            if (best == null || use.createdAt < best.createdAt) {
                best = use;
            }
        }
        if (best != null) {
            PENDING_USES.remove(best);
        }
        return best;
    }

    private static void recordDetected(TrapType type, Vec3 position) {
        if (type == null || position == null || !ServerHelper.isTrackerEnabled()) {
            return;
        }
        if (type == TrapType.STUN && !ServerHelper.isStunMemoryActive()) {
            return;
        }

        long now = System.currentTimeMillis();


        for (TrackedZone zone : ACTIVE_ZONES) {
            if (zone.type == type && zone.origin.distanceToSqr(position) <= 2.25D && now - zone.createdAt <= 750L) {
                zone.expiresAt = now + type.durationMs;
                zone.box = createActiveBox(position, type);
                zone.origin = position;
                return;
            }
        }

        ACTIVE_ZONES.add(new TrackedZone(type, position, createActiveBox(position, type), now, now + type.durationMs));
    }

    private static void updateBossbars(Minecraft client) {
        BossHealthOverlayAccessor accessor = bossAccessor(client);
        if (accessor == null) {
            return;
        }
        Map<UUID, LerpingBossEvent> events = accessor.universalmod$getEvents();
        if (events == null) {
            return;
        }

        long now = System.currentTimeMillis();
        for (TrackedZone zone : ACTIVE_ZONES) {
            if (!shouldDisplay(zone.type)) {
                events.remove(zone.bossbarId);
                continue;
            }

            long remainingMs = Math.max(0L, zone.expiresAt - now);
            int remainingSeconds = (int) Math.ceil(remainingMs / 1000.0D);
            float progress = Math.max(0.0F, Math.min(1.0F, remainingMs / (float) zone.type.durationMs));
            boolean danger = remainingSeconds <= 5;
            Component title = buildBossbarTitle(zone.type, remainingSeconds, danger);


            BossEvent.BossBarColor desiredColor = zone.type.barColor;

            LerpingBossEvent event = events.get(zone.bossbarId);
            if (event == null || event.getColor() != desiredColor) {


                event = new LerpingBossEvent(
                        zone.bossbarId,
                        title,
                        progress,
                        desiredColor,
                        BossEvent.BossBarOverlay.PROGRESS,
                        false,
                        false,
                        false
                );
                events.put(zone.bossbarId, event);
            } else {
                event.setName(title);
                event.setProgress(progress);
            }
        }
    }

    private static Component buildBossbarTitle(TrapType type, int seconds, boolean danger) {
        LanguageCode language = LanguageManager.current();
        String actionLabel = localizedActionLabel(type, language);
        String secondWord = localizedSecondWord(seconds, language);

        Component countdown = Component.literal(seconds + " " + secondWord);
        if (danger) {
            countdown = countdown.copy().withStyle(ChatFormatting.RED);
        }
        return Component.empty()
                .append(Component.literal(actionLabel + " — "))
                .append(countdown);
    }

    private static String localizedActionLabel(TrapType type, LanguageCode language) {
        return switch (language) {
            case UK_UA -> switch (type) {
                case TRAPKA -> "Дія трапки";
                case EXPLOSION_TRAP -> "Дія вибухової трапки";
                case STUN -> "Дія стану";
            };
            case EN_US -> switch (type) {
                case TRAPKA -> "Trap duration";
                case EXPLOSION_TRAP -> "Explosive trap duration";
                case STUN -> "Stun duration";
            };
            case RU_RU -> switch (type) {
                case TRAPKA -> "Действие трапки";
                case EXPLOSION_TRAP -> "Действие взрывной трапки";
                case STUN -> "Действие стана";
            };
        };
    }

    private static String localizedSecondWord(int seconds, LanguageCode language) {
        int abs = Math.abs(seconds);
        if (language == LanguageCode.EN_US) {
            return abs == 1 ? "second" : "seconds";
        }

        int lastTwo = abs % 100;
        if (lastTwo >= 11 && lastTwo <= 14) {
            return "секунд";
        }
        int last = abs % 10;
        if (language == LanguageCode.UK_UA) {
            return switch (last) {
                case 1 -> "секунда";
                case 2, 3, 4 -> "секунди";
                default -> "секунд";
            };
        }
        return switch (last) {
            case 1 -> "секунда";
            case 2, 3, 4 -> "секунды";
            default -> "секунд";
        };
    }

    private static boolean shouldDisplay(TrapType type) {
        ServerHelper helper = ServerHelper.getInstance();
        if (helper == null || !helper.isEnabled() || !ServerHelper.isTrackerEnabled()) {
            return false;
        }
        return switch (type) {
            case TRAPKA -> helper.rendersTrapka();
            case EXPLOSION_TRAP -> helper.rendersExplosionTrap();
            case STUN -> helper.rendersStun() && helper.remembersStun();
        };
    }

    private static void removeAllBossbars(Minecraft client) {
        BossHealthOverlayAccessor accessor = bossAccessor(client);
        if (accessor == null) {
            return;
        }
        Map<UUID, LerpingBossEvent> events = accessor.universalmod$getEvents();
        if (events == null) {
            return;
        }
        for (TrackedZone zone : ACTIVE_ZONES) {
            events.remove(zone.bossbarId);
        }
    }

    private static void removeBossbar(Minecraft client, TrackedZone zone) {
        BossHealthOverlayAccessor accessor = bossAccessor(client);
        if (accessor != null && accessor.universalmod$getEvents() != null && zone != null) {
            accessor.universalmod$getEvents().remove(zone.bossbarId);
        }
    }

    private static BossHealthOverlayAccessor bossAccessor(Minecraft client) {
        if (client == null || client.gui == null) {
            return null;
        }
        BossHealthOverlay overlay = client.gui.getBossOverlay();
        if (overlay == null) {
            return null;
        }
        try {
            return (BossHealthOverlayAccessor) (Object) overlay;
        } catch (ClassCastException accessorUnavailable) {
            return null;
        }
    }

    private static boolean isHolyWorld(Minecraft client) {
        if (client == null) {
            return false;
        }
        if (client.getCurrentServer() != null && client.getCurrentServer().ip != null) {
            String address = client.getCurrentServer().ip.toLowerCase(Locale.ROOT);
            if (address.contains("holy") || address.contains("holly") || address.contains("playhw")) {
                return true;
            }
        }
        return false;
    }

    private static Vec3 entityPos(Player player) {
        return player.getPosition(1.0F);
    }

    public enum TrapType {


        TRAPKA(1.0D, 3.0D, 1.5D, 15_000L, BossEvent.BossBarColor.PURPLE),
        EXPLOSION_TRAP(3.0D, 3.0D, 2.5D, 11_000L, BossEvent.BossBarColor.RED),
        STUN(15.0D, 15.0D, 15.0D, 15_000L, BossEvent.BossBarColor.YELLOW);

        private final double previewRadius;
        private final double previewHeight;
        private final double activeHalfSize;
        private final long durationMs;
        private final BossEvent.BossBarColor barColor;

        TrapType(double previewRadius, double previewHeight, double activeHalfSize, long durationMs, BossEvent.BossBarColor barColor) {
            this.previewRadius = previewRadius;
            this.previewHeight = previewHeight;
            this.activeHalfSize = activeHalfSize;
            this.durationMs = durationMs;
            this.barColor = barColor;
        }
    }

    private record PendingUse(TrapType type, Vec3 position, long createdAt, long expiresAt) {
    }

    public static final class TrackedZone {
        private final TrapType type;
        private final UUID bossbarId;
        private final long createdAt;
        private Vec3 origin;
        private AABB box;
        private long expiresAt;

        private TrackedZone(TrapType type, Vec3 origin, AABB box, long createdAt, long expiresAt) {
            this.type = type;
            this.origin = origin;
            this.box = box;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
            this.bossbarId = UUID.randomUUID();
        }

        public TrapType getType() {
            return type;
        }

        public AABB getBox() {
            return box;
        }

        public long getExpiresAt() {
            return expiresAt;
        }
    }
}
