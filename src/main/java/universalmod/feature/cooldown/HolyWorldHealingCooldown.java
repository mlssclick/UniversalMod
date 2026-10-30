package universalmod.feature.cooldown;

import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.util.Identifier;

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
        if (!isLocalPlayer(player) || !isThrowableHealingPotion(stack)) {
            return;
        }
        start(stack);
    }

    public static void recordDrunk(PlayerEntity player, ItemStack stack) {
        if (!isLocalPlayer(player) || !isDrinkableHealingPotion(stack)) {
            return;
        }
        start(stack);
    }

    public static Snapshot snapshot() {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        String currentAddress = normalizedServerAddress();
        if (minecraft.player == null || minecraft.world == null || !isHolyWorldAddress(currentAddress)) {
            reset();
            return null;
        }

        if (!serverAddress.isEmpty() && !serverAddress.equals(currentAddress)) {
            reset();
            return null;
        }

        if (displayStack.isEmpty()) {
            reset();
            return null;
        }

        PlayerEntity player = minecraft.player;
        if (player.getItemCooldownManager().isCoolingDown(displayStack)) {
            actualCooldownObserved = true;
            Identifier group = player.getItemCooldownManager().getGroup(displayStack);
            int totalTicks = CooldownStateStorage.getDuration(group);
            float progress = Math.clamp(player.getItemCooldownManager().getCooldownProgress(displayStack, 0.0F), 0.0F, 1.0F);
            int fallbackTotal = DURATION_SECONDS * 20;
            int remainingTicks = Math.max(1, Math.round(progress * (totalTicks > 0 ? totalTicks : fallbackTotal)));
            expiresAtNanos = System.nanoTime() + remainingTicks * NANOS_PER_TICK;
            return new Snapshot(
                    displayStack.copy(),
                    displayStack.getName().getString(),
                    remainingTicks,
                    formatSeconds(Math.max(1, (remainingTicks + 19) / 20)),
                    true
            );
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
        return new Snapshot(
                displayStack.copy(),
                displayStack.getName().getString(),
                remainingTicks,
                formatSeconds(remainingSeconds),
                false
        );
    }

    public static void reset() {
        expiresAtNanos = 0L;
        serverAddress = "";
        displayStack = ItemStack.EMPTY;
        actualCooldownObserved = false;
    }

    public static boolean isHealingPotion(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null) {
            return false;
        }

        for (StatusEffectInstance effect : contents.getEffects()) {
            if (effect != null && StatusEffects.INSTANT_HEALTH.equals(effect.getEffectType())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDrinkableHealingPotion(ItemStack stack) {
        return stack != null && stack.getItem() == Items.POTION && isHealingPotion(stack);
    }

    private static boolean isThrowableHealingPotion(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        return (stack.getItem() == Items.SPLASH_POTION || stack.getItem() == Items.LINGERING_POTION)
                && isHealingPotion(stack);
    }

    private static boolean isLocalPlayer(PlayerEntity player) {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        return player != null && player == minecraft.player;
    }

    private static void start(ItemStack stack) {
        String currentAddress = normalizedServerAddress();
        if (!isHolyWorldAddress(currentAddress)) {
            reset();
            return;
        }

        ItemStack copy = stack.copy();
        copy.setCount(1);
        displayStack = copy;
        serverAddress = currentAddress;
        actualCooldownObserved = false;
        expiresAtNanos = System.nanoTime() + DURATION_NANOS;
    }

    private static String normalizedServerAddress() {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        String address = minecraft != null && minecraft.getCurrentServerEntry() != null
                ? minecraft.getCurrentServerEntry().address : "";
        return address.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean isHolyWorldAddress(String address) {
        return address != null && address.contains("holyworld");
    }

    private static int safeCeilToInt(long value, long divisor) {
        long result = (value + divisor - 1L) / divisor;
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, result));
    }

    private static String formatSeconds(int totalSeconds) {
        int safeSeconds = Math.max(0, totalSeconds);
        int minutes = Math.min(99, safeSeconds / 60);
        int seconds = safeSeconds % 60;
        return twoDigits(minutes) + ":" + twoDigits(seconds);
    }

    private static String twoDigits(int value) {
        int safe = Math.max(0, Math.min(99, value));
        return Character.toString((char) ('0' + safe / 10))
                + (char) ('0' + safe % 10);
    }

    public record Snapshot(
            ItemStack stack,
            String displayName,
            int remainingTicks,
            String remainingText,
            boolean synchronizedWithItemCooldown
    ) {
    }
}
