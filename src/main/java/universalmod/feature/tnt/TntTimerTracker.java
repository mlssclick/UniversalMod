package universalmod.feature.tnt;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;




public final class TntTimerTracker {
    private static final int MAX_DISPLAY = 5;
    private static final long TICK_MILLIS = 50L;
    private static final Map<UUID, TrackedTnt> TRACKED = new LinkedHashMap<>();
    private static final List<Entry> VIEW = new ArrayList<>();
    private static long lastEntityScanTick = Long.MIN_VALUE;

    private TntTimerTracker() {
    }

    public static void tick(MinecraftClient minecraft) {
        if (minecraft == null || minecraft.world == null || minecraft.player == null) {
            reset();
            return;
        }
        long nowNanos = System.nanoTime();
        long gameTick = minecraft.world.getTime();
        if (gameTick != lastEntityScanTick) {
            lastEntityScanTick = gameTick;
            scanVisibleTnt(minecraft, nowNanos);
        }
        rebuildView(minecraft, nowNanos);
    }

    public static List<Entry> getEntries() {
        return List.copyOf(VIEW);
    }

    public static void reset() {
        TRACKED.clear();
        VIEW.clear();
        lastEntityScanTick = Long.MIN_VALUE;
    }

    private static void scanVisibleTnt(MinecraftClient minecraft, long nowNanos) {
        for (Entity entity : minecraft.world.getEntities()) {
            if (!(entity instanceof TntEntity tnt) || !minecraft.player.canSee(tnt)) continue;
            TRACKED.compute(tnt.getUuid(), (id, tracked) -> {
                if (tracked == null) {
                    return new TrackedTnt(id, tnt.getName().getString(), tnt.getX(), tnt.getY(), tnt.getZ(), tnt.getFuse(), nowNanos);
                }
                tracked.refresh(tnt.getName().getString(), tnt.getX(), tnt.getY(), tnt.getZ(), tnt.getFuse(), nowNanos);
                return tracked;
            });
        }
    }

    private static void rebuildView(MinecraftClient minecraft, long nowNanos) {
        VIEW.clear();
        Iterator<Map.Entry<UUID, TrackedTnt>> iterator = TRACKED.entrySet().iterator();
        while (iterator.hasNext()) {
            TrackedTnt tracked = iterator.next().getValue();
            long remainingMillis = tracked.remainingMillis(nowNanos);
            if (remainingMillis <= 0L) {
                iterator.remove();
                continue;
            }
            int remainingFuse = (int) Math.max(1L, (remainingMillis + TICK_MILLIS - 1L) / TICK_MILLIS);
            double distance = Math.sqrt(minecraft.player.squaredDistanceTo(tracked.x, tracked.y, tracked.z));
            VIEW.add(new Entry(tracked.id, tracked.name, remainingFuse, remainingMillis, distance));
        }
        if (VIEW.size() > 1) VIEW.sort(Comparator.comparingDouble(Entry::distance));
        if (VIEW.size() > MAX_DISPLAY) VIEW.subList(MAX_DISPLAY, VIEW.size()).clear();
    }

    private static final class TrackedTnt {
        private final UUID id;
        private String name;
        private double x, y, z;
        private long remainingMillisAtSample;
        private long sampleNanos;

        private TrackedTnt(UUID id, String name, double x, double y, double z, int fuseTicks, long nowNanos) {
            this.id = id;
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.remainingMillisAtSample = Math.max(0L, fuseTicks) * TICK_MILLIS;
            this.sampleNanos = nowNanos;
        }

        private void refresh(String name, double x, double y, double z, int fuseTicks, long nowNanos) {
            long observedMillis = Math.max(0L, fuseTicks) * TICK_MILLIS;
            long runningMillis = remainingMillis(nowNanos);
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.remainingMillisAtSample = Math.min(runningMillis, observedMillis);
            this.sampleNanos = nowNanos;
        }

        private long remainingMillis(long nowNanos) {
            long elapsedMillis = Math.max(0L, (nowNanos - sampleNanos) / 1_000_000L);
            return Math.max(0L, remainingMillisAtSample - elapsedMillis);
        }
    }

    public record Entry(UUID id, String name, int remainingFuse, long remainingMillis, double distance) {
        public String label() {
            long seconds = remainingMillis / 1000L, millis = remainingMillis % 1000L;
            return name + " | " + String.format(Locale.US, "%.1f", distance) + "m | " + String.format(Locale.US, "%d.%03d", seconds, millis);
        }
    }
}
