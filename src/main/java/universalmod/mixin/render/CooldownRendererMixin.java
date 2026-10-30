package universalmod.mixin.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.utility.HotbarCooldowns;
import universalmod.feature.cooldown.CooldownStateStorage;
import universalmod.module.misc.ServerHelper;

import java.util.Locale;

@Mixin(DrawContext.class)
public abstract class CooldownRendererMixin {
    private static final float TICKS_PER_SECOND = 20.0F;
    private static final float MAX_SCALE = 0.87F;
    private static final float MIN_SCALE = 0.62F;
    private static final float MAX_TEXT_WIDTH = 14.0F;

    @Inject(method = "drawStackOverlay(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V", at = @At("TAIL"), require = 0)
    private void universalmod$renderCooldownText(TextRenderer font, ItemStack stack, int x, int y, String text, CallbackInfo ci) {
        if (!HotbarCooldowns.isActive() || stack == null || stack.isEmpty()) {
            return;
        }

        if (ServerHelper.ItemInfo.isCompassCooldownOverlayStack(stack)) {
            return;
        }

        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return;
        }

        if (!minecraft.player.getItemCooldownManager().isCoolingDown(stack)) {
            return;
        }

        Identifier group = minecraft.player.getItemCooldownManager().getGroup(stack);
        int totalTicks = CooldownStateStorage.getDuration(group);
        if (totalTicks <= 0) {
            return;
        }

        float partialTicks = minecraft.getRenderTickCounter().getTickDelta(false);
        float remainingPercent = Math.clamp(minecraft.player.getItemCooldownManager().getCooldownProgress(stack, partialTicks), 0.0F, 1.0F);
        if (remainingPercent <= 0.0F) {
            return;
        }

        String cooldownText = formatCooldownSeconds(remainingPercent * totalTicks / TICKS_PER_SECOND);
        int textWidth = font.getWidth(cooldownText);
        float scale = textWidth <= 0 ? MAX_SCALE : Math.clamp(MAX_TEXT_WIDTH / textWidth, MIN_SCALE, MAX_SCALE);
        DrawContext graphics = (DrawContext) (Object) this;

        graphics.getMatrices().push();
        try {
            graphics.getMatrices().translate(x + 1.0F, y + 1.0F, 0);
            graphics.getMatrices().scale(scale, scale, 1);
            graphics.drawText(font, cooldownText, 0, 0, getCooldownColor(remainingPercent), true);
        } finally {
            graphics.getMatrices().pop();
        }
    }

    private static String formatCooldownSeconds(float seconds) {
        float clamped = Math.max(0.0F, seconds);
        return clamped >= 9.95F ? (int) Math.ceil(clamped) + "s" : String.format(Locale.US, "%.1fs", clamped);
    }

    private static int getCooldownColor(float percent) {
        float clamped = Math.clamp(percent, 0.0F, 1.0F);
        float scaled = clamped * 100.0F;
        if (scaled <= 50.0F) {
            return lerpColor(0xFF55FF55, 0xFFFFFF55, scaled / 50.0F);
        }
        return lerpColor(0xFFFFFF55, 0xFFFF5555, (scaled - 50.0F) / 50.0F);
    }

    private static int lerpColor(int from, int to, float t) {
        float clamped = Math.clamp(t, 0.0F, 1.0F);
        int a = Math.round(((from >>> 24) & 255) + (((to >>> 24) & 255) - ((from >>> 24) & 255)) * clamped);
        int r = Math.round(((from >>> 16) & 255) + (((to >>> 16) & 255) - ((from >>> 16) & 255)) * clamped);
        int g = Math.round(((from >>> 8) & 255) + (((to >>> 8) & 255) - ((from >>> 8) & 255)) * clamped);
        int b = Math.round((from & 255) + ((to & 255) - (from & 255)) * clamped);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
