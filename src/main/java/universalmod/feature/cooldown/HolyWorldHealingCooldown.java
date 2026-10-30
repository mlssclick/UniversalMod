package universalmod.feature.cooldown;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.PotionUtil;
import universalmod.feature.server.ServerEnvironment;

public final class HolyWorldHealingCooldown {
    public static final int DURATION_SECONDS = 10;
    private static final long DURATION_NANOS = DURATION_SECONDS * 1_000_000_000L;
    private static final long NANOS_PER_TICK = 50_000_000L;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static long expiresAtNanos;
    private static String serverAddress = "";
    private static ItemStack displayStack = ItemStack.EMPTY;
    private static boolean actualCooldownObserved;

    private HolyWorldHealingCooldown() {
    }

    public static void recordThrown(PlayerEntity player, ItemStack stack) {
        if (isLocalPlayer(player) && isThrowableHealingPotion(stack)) start(stack);
    }

    public static void recordDrunk(PlayerEntity player, ItemStack stack) {
        if (isLocalPlayer(player) && isDrinkableHealingPotion(stack)) start(stack);
    }

    public static Snapshot snapshot() {
        MinecraftClient mc = MinecraftClient.getInstance();
        String address = normalizedServerAddress();
        if (mc.player == null || mc.world == null || !isHolyWorldAddress(address)) {
            reset();
            return null;
        }
        if (!serverAddress.isEmpty() && !serverAddress.equals(address)) {
            reset();
            return null;
        }
        if (displayStack.isEmpty()) {
            reset();
            return null;
        }
        PlayerEntity player = mc.player;
        if (player.getItemCooldownManager().isCoolingDown(displayStack.getItem())) {
            actualCooldownObserved = true;
            int totalTicks = CooldownStateStorage.getDuration(displayStack.getItem());
            float progress = clamp(player.getItemCooldownManager().getCooldownProgress(displayStack.getItem(), 0f), 0f, 1f);
            int fallback = DURATION_SECONDS * 20;
            int remaining = Math.max(1, Math.round(progress * (totalTicks > 0 ? totalTicks : fallback)));
            expiresAtNanos = System.nanoTime() + remaining * NANOS_PER_TICK;
            return new Snapshot(displayStack.copy(), displayStack.getName().getString(), remaining, formatSeconds(Math.max(1, (remaining + 19) / 20)), true);
        }
        if (actualCooldownObserved) {
            reset();
            return null;
        }
        long remainingNanos = expiresAtNanos - System.nanoTime();
        if (remainingNanos <= 0L) {
            reset();
            return null;
        }
        int remainingTicks = safeCeilToInt(remainingNanos, NANOS_PER_TICK);
        int remainingSeconds = safeCeilToInt(remainingNanos, NANOS_PER_SECOND);
        return new Snapshot(displayStack.copy(), displayStack.getName().getString(), remainingTicks, formatSeconds(remainingSeconds), false);
    }

    public static void reset() {
        expiresAtNanos = 0L;
        serverAddress = "";
        displayStack = ItemStack.EMPTY;
        actualCooldownObserved = false;
    }

    public static boolean isHealingPotion(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        for (StatusEffectInstance effect : PotionUtil.getPotionEffects(stack)) {
            if (effect != null && effect.getEffectType() == StatusEffects.INSTANT_HEALTH) return true;
        }
        return false;
    }

    private static boolean isDrinkableHealingPotion(ItemStack s) {
        return s != null && s.getItem() == Items.POTION && isHealingPotion(s);
    }

    private static boolean isThrowableHealingPotion(ItemStack s) {
        return s != null && (s.getItem() == Items.SPLASH_POTION || s.getItem() == Items.LINGERING_POTION) && isHealingPotion(s);
    }

    private static boolean isLocalPlayer(PlayerEntity p) {
        return p != null && p == MinecraftClient.getInstance().player;
    }

    private static void start(ItemStack stack) {
        String address = normalizedServerAddress();
        if (!isHolyWorldAddress(address)) {
            reset();
            return;
        }
        ItemStack copy = stack.copy();
        copy.setCount(1);
        displayStack = copy;
        serverAddress = address;
        actualCooldownObserved = false;
        expiresAtNanos = System.nanoTime() + DURATION_NANOS;
    }

    private static String normalizedServerAddress() {
        return ServerEnvironment.serverAddress().trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean isHolyWorldAddress(String address) {
        return address != null && address.contains("holyworld");
    }

    private static int safeCeilToInt(long value, long divisor) {
        long result = (value + divisor - 1L) / divisor;
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, result));
    }

    private static String formatSeconds(int total) {
        int safe = Math.max(0, total), minutes = Math.min(99, safe / 60), seconds = safe % 60;
        return twoDigits(minutes) + ":" + twoDigits(seconds);
    }

    private static String twoDigits(int v) {
        int s = Math.max(0, Math.min(99, v));
        return (s < 10 ? "0" : "") + s;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    public record Snapshot(ItemStack stack, String displayName, int remainingTicks, String remainingText,
                           boolean synchronizedWithItemCooldown) {
    }
}
