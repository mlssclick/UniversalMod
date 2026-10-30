package universalmod.render;

import net.minecraft.client.MinecraftClient;

public final class ShaderRefreshGate {
    public enum Channel {
        HANDS,
        BLOCKS,
        SKY
    }

    private static final long[] LAST_UPDATE = new long[Channel.values().length];

    private ShaderRefreshGate() {
    }

    public static boolean shouldUpdate(Channel channel) {
        long now = System.nanoTime();
        int refreshRate = refreshRate();
        long interval = Math.max(1L, 1_000_000_000L / refreshRate);
        int index = channel.ordinal();
        long previous = LAST_UPDATE[index];
        if (previous == 0L || now - previous >= interval) {
            if (previous == 0L || now < previous) {
                LAST_UPDATE[index] = now;
            } else {
                long elapsed = now - previous;
                long steps = Math.max(1L, elapsed / interval);
                LAST_UPDATE[index] = previous + steps * interval;
            }
            return true;
        }
        return false;
    }

    public static void reset(Channel channel) {
        LAST_UPDATE[channel.ordinal()] = 0L;
    }

    public static int refreshRate() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return 60;
        int refreshRate = client.getWindow().getRefreshRate();
        if (refreshRate <= 0) return 60;
        return Math.max(30, Math.min(1000, refreshRate));
    }
}
