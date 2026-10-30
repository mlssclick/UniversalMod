package universalmod.module.misc.serverhelper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import universalmod.mixin.accessor.BossBarHudAccessor;
import universalmod.module.misc.ServerHelper;
import universalmod.service.language.LanguageCode;
import universalmod.service.language.LanguageManager;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;









public final class TrapDetectionService {
    private static final long PENDING_CONFIRM_MS = 2_000L;
    private static final double TELEPORT_DISTANCE_SQUARED = 64.0D * 64.0D;
    private static final int TELEPORT_GRACE_TICKS = 20;

    private static final List<TrackedZone> ACTIVE_ZONES = new ArrayList<>();
    private static final List<PendingUse> PENDING_USES = new ArrayList<>();
    private static final Map<TrapType, Integer> LAST_ITEM_COUNTS = new EnumMap<>(TrapType.class);

    private static Vec3d lastPlayerPos;
    private static RegistryKey<World> lastWorldKey;
    private static int inventoryUsePauseTicks;
    private static boolean initialized;

    private TrapDetectionService() {}


    public static void init() {
        if (initialized) return;
        initialized = true;
        ClientTickEvents.END_CLIENT_TICK.register(TrapDetectionService::tick);
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            recordItemUse(player, stack, entityPos(player));
            return TypedActionResult.pass(stack);
        });
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            Vec3d position = hitResult == null ? entityPos(player) : hitResult.getPos();
            recordItemUse(player, player.getStackInHand(hand), position);
            return ActionResult.PASS;
        });
    }

    public static void reset() {
        removeAllBossbars(MinecraftClient.getInstance());
        ACTIVE_ZONES.clear();
        PENDING_USES.clear();
        LAST_ITEM_COUNTS.clear();
        lastPlayerPos = null;
        lastWorldKey = null;
        inventoryUsePauseTicks = 0;
    }


    public static void tick(MinecraftClient client) {
        pruneExpired();
        detectInventoryUses(client);
        updateBossbars(client);
    }


    public static void recordItemUse(PlayerEntity player, ItemStack stack, Vec3d position) {
        rememberPendingUse(player, stack, position);
    }

    public static void recordFinishedUse(PlayerEntity player) {
        if (player == null) return;
        rememberPendingUse(player, player.getActiveItem(), entityPos(player));
    }





    public static void handleSoundPacket(PlaySoundS2CPacket packet) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (packet == null || !ServerHelper.isTrackerEnabled() || !isHolyWorld(client) || client.player == null) {
            return;
        }

        String sound = packet.getSound().value().getId().toString();
        float volume = packet.getVolume();
        float pitch = packet.getPitch();
        Vec3d packetBlock = new Vec3d((int) packet.getX(), (int) packet.getY(), (int) packet.getZ());

        if (sound.contains("minecraft:entity.generic.explode") && (volume == 1.0F || pitch == 1.0F)) {
            PendingUse localUse = takeMostRecentPendingNear(packetBlock, 100.0D, TrapType.STUN, TrapType.EXPLOSION_TRAP);
            if (localUse == null) return;
            if (localUse.type == TrapType.STUN) {
                recordDetected(TrapType.STUN, packetBlock);
            } else {
                recordDetected(TrapType.EXPLOSION_TRAP, packetBlock.add(0.0D, 1.0D, 0.0D));
            }
            return;
        }

        if (sound.contains("minecraft:block.beacon.deactivate") && (volume == 1.5F || pitch == 1.0F)) {
            PendingUse stunUse = takePendingNear(TrapType.STUN, packetBlock, 100.0D);
            if (stunUse != null) recordDetected(TrapType.STUN, packetBlock);
        }
    }

    public static Box createPreviewBox(Vec3d position, TrapType type) {
        if (position == null || type == null) return null;
        double baseY = Math.floor(position.y);
        return new Box(
                position.x - type.previewRadius, baseY, position.z - type.previewRadius,
                position.x + type.previewRadius, baseY + type.previewHeight, position.z + type.previewRadius
        );
    }

    public static TrapType detectType(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        String liteTrapsType = readLiteTrapsType(stack);
        if (liteTrapsType != null) {
            return switch (liteTrapsType) {
                case "default" -> TrapType.TRAPKA;
                case "custom" -> TrapType.EXPLOSION_TRAP;
                case "stun" -> TrapType.STUN;
                default -> null;
            };
        }

        if (stack.isOf(Items.POPPED_CHORUS_FRUIT)) return TrapType.TRAPKA;
        if (stack.isOf(Items.PRISMARINE_SHARD)) return TrapType.EXPLOSION_TRAP;
        if (stack.isOf(Items.NETHER_STAR)) return TrapType.STUN;
        return null;
    }

    private static String readLiteTrapsType(ItemStack stack) {
        NbtCompound root = stack.getNbt();
        if (root == null || !root.contains("PublicBukkitValues")) return null;
        NbtCompound values = root.getCompound("PublicBukkitValues");
        String value = values.getString("litetraps:item");
        return value == null || value.isBlank() ? null : value.toLowerCase(Locale.ROOT);
    }

    public static List<TrackedZone> getActiveZones() {
        pruneExpired();
        return new ArrayList<>(ACTIVE_ZONES);
    }

    private static void rememberPendingUse(PlayerEntity player, ItemStack stack, Vec3d position) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (player == null || player != client.player || stack == null || stack.isEmpty() || position == null
                || !ServerHelper.isTrackerEnabled() || !isHolyWorld(client)) return;

        TrapType type = detectType(stack);
        if (type == null || isOnCooldown(player, stack)) return;
        long now = System.currentTimeMillis();
        PENDING_USES.add(new PendingUse(type, position, now, now + PENDING_CONFIRM_MS));
    }

    private static boolean isOnCooldown(PlayerEntity player, ItemStack stack) {
        return player != null && stack != null && !stack.isEmpty()
                && player.getItemCooldownManager().isCoolingDown(stack.getItem());
    }

    private static Box createActiveBox(Vec3d position, TrapType type) {
        return new Box(
                position.x - type.activeHalfSize, position.y - type.activeHalfSize, position.z - type.activeHalfSize,
                position.x + type.activeHalfSize, position.y + type.activeHalfSize, position.z + type.activeHalfSize
        );
    }

    private static void pruneExpired() {
        long now = System.currentTimeMillis();
        for (Iterator<TrackedZone> iterator = ACTIVE_ZONES.iterator(); iterator.hasNext();) {
            TrackedZone zone = iterator.next();
            if (zone.expiresAt <= now) {
                removeBossbar(MinecraftClient.getInstance(), zone);
                iterator.remove();
            }
        }
        PENDING_USES.removeIf(use -> use.expiresAt < now);
    }

    private static void detectInventoryUses(MinecraftClient client) {
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
            if (previousCount == null || inventoryUsePauseTicks > 0) continue;

            int used = previousCount - currentCount;
            if (used <= 0) continue;
            for (int i = 0; i < used; i++) {
                PendingUse pending = takeOldestPending(type, now);
                if (pending == null) break;
                recordDetected(type, pending.position);
            }
        }
    }

    private static void updateInventoryUsePause(PlayerEntity player) {
        RegistryKey<World> currentWorldKey = player.getWorld() == null ? null : player.getWorld().getRegistryKey();
        Vec3d currentPos = entityPos(player);
        if (lastWorldKey != null && currentWorldKey != null && !lastWorldKey.equals(currentWorldKey)) {
            inventoryUsePauseTicks = TELEPORT_GRACE_TICKS;
            LAST_ITEM_COUNTS.clear();
        } else if (lastPlayerPos != null && currentPos.squaredDistanceTo(lastPlayerPos) > TELEPORT_DISTANCE_SQUARED) {
            inventoryUsePauseTicks = TELEPORT_GRACE_TICKS;
            LAST_ITEM_COUNTS.clear();
        } else if (inventoryUsePauseTicks > 0) {
            inventoryUsePauseTicks--;
        }
        lastWorldKey = currentWorldKey;
        lastPlayerPos = currentPos;
    }

    private static int countType(PlayerEntity player, TrapType type) {
        int count = 0;
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack != null && detectType(stack) == type) count += stack.getCount();
        }
        ItemStack offhand = player.getOffHandStack();
        if (offhand != null && detectType(offhand) == type) count += offhand.getCount();
        return count;
    }

    private static PendingUse takeMostRecentPendingNear(Vec3d soundPosition, double maxDistanceSquared, TrapType... allowedTypes) {
        long now = System.currentTimeMillis();
        PendingUse best = null;
        double bestDistance = Double.MAX_VALUE;
        for (PendingUse use : PENDING_USES) {
            if (use.expiresAt < now || !containsType(allowedTypes, use.type)) continue;
            double distance = use.position.squaredDistanceTo(soundPosition);
            if (distance > maxDistanceSquared) continue;
            if (best == null || use.createdAt > best.createdAt || (use.createdAt == best.createdAt && distance < bestDistance)) {
                best = use;
                bestDistance = distance;
            }
        }
        if (best != null) PENDING_USES.remove(best);
        return best;
    }

    private static boolean containsType(TrapType[] types, TrapType type) {
        if (types == null) return false;
        for (TrapType candidate : types) if (candidate == type) return true;
        return false;
    }

    private static PendingUse takePendingNear(TrapType type, Vec3d soundPosition, double maxDistanceSquared) {
        long now = System.currentTimeMillis();
        PendingUse best = null;
        double bestDistance = Double.MAX_VALUE;
        for (PendingUse use : PENDING_USES) {
            if (use.type != type || use.expiresAt < now) continue;
            double distance = use.position.squaredDistanceTo(soundPosition);
            if (distance <= maxDistanceSquared && distance < bestDistance) {
                best = use;
                bestDistance = distance;
            }
        }
        if (best != null) PENDING_USES.remove(best);
        return best;
    }

    private static PendingUse takeOldestPending(TrapType type, long now) {
        PendingUse best = null;
        for (PendingUse use : PENDING_USES) {
            if (use.type != type || use.expiresAt < now) continue;
            if (best == null || use.createdAt < best.createdAt) best = use;
        }
        if (best != null) PENDING_USES.remove(best);
        return best;
    }

    private static void recordDetected(TrapType type, Vec3d position) {
        if (type == null || position == null || !ServerHelper.isTrackerEnabled()) return;
        if (type == TrapType.STUN && !ServerHelper.isStunMemoryActive()) return;

        long now = System.currentTimeMillis();
        for (TrackedZone zone : ACTIVE_ZONES) {
            if (zone.type == type && zone.origin.squaredDistanceTo(position) <= 2.25D && now - zone.createdAt <= 750L) {
                zone.expiresAt = now + type.durationMs;
                zone.box = createActiveBox(position, type);
                zone.origin = position;
                return;
            }
        }
        ACTIVE_ZONES.add(new TrackedZone(type, position, createActiveBox(position, type), now, now + type.durationMs));
    }

    private static void updateBossbars(MinecraftClient client) {
        BossBarHudAccessor accessor = bossAccessor(client);
        if (accessor == null) return;
        Map<UUID, ClientBossBar> events = accessor.briskwalk$getBossBars();
        if (events == null) return;

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
            Text title = buildBossbarTitle(zone.type, remainingSeconds, danger);
            BossBar.Color desiredColor = zone.type.barColor;

            ClientBossBar event = events.get(zone.bossbarId);
            if (event == null || event.getColor() != desiredColor) {
                event = new ClientBossBar(zone.bossbarId, title, progress, desiredColor, BossBar.Style.PROGRESS,
                        false, false, false);
                events.put(zone.bossbarId, event);
            } else {
                event.setName(title);
                event.setPercent(progress);
            }
        }
    }


    private static Text buildBossbarTitle(TrapType type, int seconds, boolean danger) {
        LanguageCode language = LanguageManager.current();
        String actionLabel = localizedActionLabel(type, language);
        String secondWord = localizedSecondWord(seconds, language);
        Text countdown = Text.literal(seconds + " " + secondWord);
        if (danger) countdown = countdown.copy().formatted(Formatting.RED);
        return Text.empty().append(Text.literal(actionLabel + " — ")).append(countdown);
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
        if (language == LanguageCode.EN_US) return abs == 1 ? "second" : "seconds";
        int lastTwo = abs % 100;
        if (lastTwo >= 11 && lastTwo <= 14) return "секунд";
        int last = abs % 10;
        if (language == LanguageCode.UK_UA) {
            return switch (last) { case 1 -> "секунда"; case 2, 3, 4 -> "секунди"; default -> "секунд"; };
        }
        return switch (last) { case 1 -> "секунда"; case 2, 3, 4 -> "секунды"; default -> "секунд"; };
    }

    private static boolean shouldDisplay(TrapType type) {
        ServerHelper helper = ServerHelper.getInstance();
        if (helper == null || !helper.isEnabled() || !ServerHelper.isTrackerEnabled()) return false;
        return switch (type) {
            case TRAPKA -> helper.rendersTrapka();
            case EXPLOSION_TRAP -> helper.rendersExplosionTrap();
            case STUN -> helper.rendersStun() && helper.remembersStun();
        };
    }

    private static void removeAllBossbars(MinecraftClient client) {
        BossBarHudAccessor accessor = bossAccessor(client);
        if (accessor == null || accessor.briskwalk$getBossBars() == null) return;
        for (TrackedZone zone : ACTIVE_ZONES) accessor.briskwalk$getBossBars().remove(zone.bossbarId);
    }

    private static void removeBossbar(MinecraftClient client, TrackedZone zone) {
        BossBarHudAccessor accessor = bossAccessor(client);
        if (accessor != null && accessor.briskwalk$getBossBars() != null && zone != null) {
            accessor.briskwalk$getBossBars().remove(zone.bossbarId);
        }
    }

    private static BossBarHudAccessor bossAccessor(MinecraftClient client) {
        if (client == null || client.inGameHud == null) return null;
        BossBarHud overlay = client.inGameHud.getBossBarHud();
        if (overlay == null) return null;
        try { return (BossBarHudAccessor) (Object) overlay; }
        catch (ClassCastException ignored) { return null; }
    }

    private static boolean isHolyWorld(MinecraftClient client) {
        if (client == null) return false;
        if (client.getCurrentServerEntry() != null && client.getCurrentServerEntry().address != null) {
            String address = client.getCurrentServerEntry().address.toLowerCase(Locale.ROOT);
            return address.contains("holy") || address.contains("holly") || address.contains("playhw");
        }
        return false;
    }

    private static Vec3d entityPos(PlayerEntity player) {
        return player.getLerpedPos(1.0F);
    }

    public enum TrapType {
        TRAPKA(1.0D, 3.0D, 1.5D, 15_000L, BossBar.Color.PURPLE),
        EXPLOSION_TRAP(3.0D, 3.0D, 2.5D, 11_000L, BossBar.Color.RED),
        STUN(15.0D, 15.0D, 15.0D, 15_000L, BossBar.Color.YELLOW);

        private final double previewRadius;
        private final double previewHeight;
        private final double activeHalfSize;
        private final long durationMs;
        private final BossBar.Color barColor;

        TrapType(double previewRadius, double previewHeight, double activeHalfSize, long durationMs, BossBar.Color barColor) {
            this.previewRadius = previewRadius;
            this.previewHeight = previewHeight;
            this.activeHalfSize = activeHalfSize;
            this.durationMs = durationMs;
            this.barColor = barColor;
        }
    }

    private static final class PendingUse {
        final TrapType type;
        final Vec3d position;
        final long createdAt;
        final long expiresAt;
        PendingUse(TrapType type, Vec3d position, long createdAt, long expiresAt) {
            this.type = type; this.position = position; this.createdAt = createdAt; this.expiresAt = expiresAt;
        }
    }

    public static final class TrackedZone {
        private final TrapType type;
        private final UUID bossbarId;
        private final long createdAt;
        private Vec3d origin;
        private Box box;
        private long expiresAt;

        private TrackedZone(TrapType type, Vec3d origin, Box box, long createdAt, long expiresAt) {
            this.type = type; this.origin = origin; this.box = box; this.createdAt = createdAt;
            this.expiresAt = expiresAt; this.bossbarId = UUID.randomUUID();
        }

        public TrapType getType() { return type; }
        public Box getBox() { return box; }
        public long getExpiresAt() { return expiresAt; }
    }
}
