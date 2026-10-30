package universalmod.mixin.render;

import java.util.Locale;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.utility.HotbarCooldowns;
import universalmod.feature.cooldown.CooldownStateStorage;
import universalmod.module.misc.ServerHelper;

@Mixin(InGameHud.class)
public abstract class HotbarCooldownMixin {
    private static final float TICKS_PER_SECOND = 20.0F;
    private static final float MAX_SCALE = 0.87F;
    private static final float MIN_SCALE = 0.62F;
    private static final float MAX_TEXT_WIDTH = 14.0F;

    @Inject(
            method = "renderHotbarItem(Lnet/minecraft/client/gui/DrawContext;IILnet/minecraft/client/render/RenderTickCounter;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/item/ItemStack;I)V",
            at = @At("TAIL")
    )
    private void universalmod$renderCooldownText(
            DrawContext context,
            int x,
            int y,
            RenderTickCounter tickCounter,
            PlayerEntity player,
            ItemStack stack,
            int seed,
            CallbackInfo ci
    ) {
        if (!HotbarCooldowns.isActive()
                || stack == null
                || stack.isEmpty()
                || player == null
                || ServerHelper.ItemInfo.isCompassCooldownOverlayStack(stack)) {
            return;
        }

        ItemCooldownManager cooldowns = player.getItemCooldownManager();
        if (!cooldowns.isCoolingDown(stack)) {
            return;
        }

        float tickDelta = tickCounter == null ? 0.0F : tickCounter.getTickDelta(false);
        float remaining = MathHelper.clamp(cooldowns.getCooldownProgress(stack, tickDelta), 0.0F, 1.0F);
        if (remaining <= 0.0F) {
            return;
        }

        Identifier group = cooldowns.getGroup(stack);
        int totalTicks = CooldownStateStorage.getDuration(group);
        if (totalTicks <= 0) {
            return;
        }

        String text = formatCooldownSeconds(remaining * totalTicks / TICKS_PER_SECOND);
        MinecraftClient client = MinecraftClient.getInstance();
        int textWidth = client.textRenderer.getWidth(text);
        float scale = textWidth <= 0
                ? MAX_SCALE
                : MathHelper.clamp(MAX_TEXT_WIDTH / textWidth, MIN_SCALE, MAX_SCALE);

        context.getMatrices().push();
        try {
            context.getMatrices().translate(x + 1.0F, y + 1.0F, 300.0F);
            context.getMatrices().scale(scale, scale, 1.0F);
            context.drawText(client.textRenderer, text, 0, 0, getCooldownColor(remaining), true);
        } finally {
            context.getMatrices().pop();
        }
    }

    private static String formatCooldownSeconds(float seconds) {
        float clamped = Math.max(0.0F, seconds);
        return clamped >= 9.95F
                ? (int) Math.ceil(clamped) + "s"
                : String.format(Locale.US, "%.1fs", clamped);
    }

    private static int getCooldownColor(float percent) {
        float scaled = MathHelper.clamp(percent, 0.0F, 1.0F) * 100.0F;
        if (scaled <= 50.0F) {
            return lerpColor(0xFF55FF55, 0xFFFFFF55, scaled / 50.0F);
        }
        return lerpColor(0xFFFFFF55, 0xFFFF5555, (scaled - 50.0F) / 50.0F);
    }

    private static int lerpColor(int from, int to, float t) {
        float clamped = MathHelper.clamp(t, 0.0F, 1.0F);
        int a = Math.round(((from >>> 24) & 255) + (((to >>> 24) & 255) - ((from >>> 24) & 255)) * clamped);
        int r = Math.round(((from >>> 16) & 255) + (((to >>> 16) & 255) - ((from >>> 16) & 255)) * clamped);
        int g = Math.round(((from >>> 8) & 255) + (((to >>> 8) & 255) - ((from >>> 8) & 255)) * clamped);
        int b = Math.round((from & 255) + ((to & 255) - (from & 255)) * clamped);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
