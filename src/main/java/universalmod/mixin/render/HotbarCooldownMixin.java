package universalmod.mixin.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.utility.HotbarCooldowns;
import universalmod.feature.cooldown.CooldownStateStorage;
import universalmod.module.misc.serverhelper.ServerItemMetadataService;

import java.util.Locale;

@Mixin(InGameHud.class)
public abstract class HotbarCooldownMixin {
    @Inject(method = "renderHotbarItem", at = @At("TAIL"))
    private void universalmod$cooldownText(DrawContext context, int x, int y, float tickDelta, PlayerEntity player, ItemStack stack, int seed, CallbackInfo ci) {
        if (!HotbarCooldowns.isActive() || stack == null || stack.isEmpty() || player == null || ServerItemMetadataService.isCompassCooldownOverlayStack(stack))
            return;
        float remaining = MathHelper.clamp(player.getItemCooldownManager().getCooldownProgress(stack.getItem(), tickDelta), 0.0F, 1.0F);
        if (remaining <= 0.0F) return;
        int total = CooldownStateStorage.getDuration(stack.getItem());
        if (total <= 0) return;
        String text = format(remaining * total / 20.0F);
        MinecraftClient mc = MinecraftClient.getInstance();
        int width = mc.textRenderer.getWidth(text);
        float scale = width <= 0 ? 0.87F : MathHelper.clamp(14.0F / width, 0.62F, 0.87F);
        context.getMatrices().push();
        context.getMatrices().translate(x + 1.0F, y + 1.0F, 300.0F);
        context.getMatrices().scale(scale, scale, 1.0F);
        context.drawText(mc.textRenderer, text, 0, 0, color(remaining), true);
        context.getMatrices().pop();
    }

    private static String format(float sec) {
        float v = Math.max(0, sec);
        return v >= 9.95F ? (int) Math.ceil(v) + "s" : String.format(Locale.US, "%.1fs", v);
    }

    private static int color(float p) {
        float s = MathHelper.clamp(p, 0, 1) * 100F;
        return s <= 50 ? lerp(0xFF55FF55, 0xFFFFFF55, s / 50F) : lerp(0xFFFFFF55, 0xFFFF5555, (s - 50F) / 50F);
    }

    private static int lerp(int a, int b, float t) {
        t = MathHelper.clamp(t, 0, 1);
        int aa = Math.round(((a >>> 24) & 255) + (((b >>> 24) & 255) - ((a >>> 24) & 255)) * t);
        int r = Math.round(((a >>> 16) & 255) + (((b >>> 16) & 255) - ((a >>> 16) & 255)) * t);
        int g = Math.round(((a >>> 8) & 255) + (((b >>> 8) & 255) - ((a >>> 8) & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return aa << 24 | r << 16 | g << 8 | bl;
    }
}
