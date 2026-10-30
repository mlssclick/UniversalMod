package universalmod.mixin.screen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import universalmod.module.render.Animations;

@Mixin(ChatHud.class)
public abstract class ChatLineAnimationsMixin {
    @WrapOperation(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)I"
            ),
            require = 0
    )
    private int universalmod$animateMessageAppearance(
            DrawContext context,
            TextRenderer textRenderer,
            OrderedText text,
            int x,
            int y,
            int color,
            Operation<Integer> original,
            @Local ChatHudLine.Visible line
    ) {
        if (!Animations.active(Animations.MESSAGES) || line == null) {
            return original.call(context, textRenderer, text, x, y, color);
        }

        MinecraftClient client = MinecraftClient.getInstance();
        float progress = messageProgress(client, line);
        int translatedX = Math.round(x - (1.0F - progress) * 8.0F);
        int alpha = (color >>> 24) & 0xFF;
        int animatedAlpha = Math.max(0, Math.min(255, Math.round(alpha * progress)));
        int animatedColor = (animatedAlpha << 24) | (color & 0x00FFFFFF);
        return original.call(context, textRenderer, text, translatedX, y, animatedColor);
    }

    private static float messageProgress(MinecraftClient client, ChatHudLine.Visible line) {
        float tickDelta = client.getRenderTickCounter().getTickDelta(false);
        double raw = Math.max(0.0D, Math.min(
                1.0D,
                ((client.inGameHud.getTicks() - line.addedTime()) + tickDelta) / 9.0D
        ));
        return raw >= 1.0D ? 1.0F : (float) (1.0D - Math.pow(2.0D, -10.0D * raw));
    }
}
