package universalmod.mixin.screen;

import java.util.List;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.Animations;

@Mixin(ChatHud.class)
public abstract class ChatLineAnimationsMixin {
    @Shadow
    @Final
    private List<ChatHudLine.Visible> visibleMessages;
    @Unique
    private ChatHudLine.Visible universalmod$currentLine;
    @Unique
    private int universalmod$currentTick;

    @Inject(method = "render", at = @At("HEAD"))
    private void universalmod$beginRender(DrawContext context, int currentTick, int mouseX, int mouseY, CallbackInfo ci) {
        universalmod$currentTick = currentTick;
        universalmod$currentLine = null;
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;"), require = 0)
    private Object universalmod$captureRenderedLine(List<?> list, int index) {
        Object value = list.get(index);
        if (list == visibleMessages) {
            universalmod$currentLine = value instanceof ChatHudLine.Visible visible ? visible : null;
        }
        return value;
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)I"), require = 0)
    private int universalmod$animateMessage(DrawContext context, TextRenderer renderer, OrderedText text, int x, int y, int color) {
        ChatHudLine.Visible line = universalmod$currentLine;
        universalmod$currentLine = null;
        if (!Animations.active(Animations.MESSAGES) || line == null) {
            return context.drawTextWithShadow(renderer, text, x, y, color);
        }
        float progress = universalmod$messageProgress(line);
        int alpha = color >>> 24;
        int animatedAlpha = Math.max(0, Math.min(255, Math.round(alpha * progress)));
        float offset = -(1.0F - progress) * 8.0F;
        int animatedColor = (color & 0x00FFFFFF) | (animatedAlpha << 24);
        context.getMatrices().push();
        context.getMatrices().translate(offset, 0.0F, 0.0F);
        try {
            return context.drawTextWithShadow(renderer, text, x, y, animatedColor);
        } finally {
            context.getMatrices().pop();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void universalmod$endRender(DrawContext context, int currentTick, int mouseX, int mouseY, CallbackInfo ci) {
        universalmod$currentLine = null;
    }

    @Unique
    private float universalmod$messageProgress(ChatHudLine.Visible line) {
        float partialTick = MinecraftClient.getInstance().getTickDelta();
        double raw = Math.max(0.0D, Math.min(1.0D, ((universalmod$currentTick - line.addedTime()) + partialTick) / 9.0D));
        return raw >= 1.0D ? 1.0F : (float) (1.0D - Math.pow(2.0D, -10.0D * raw));
    }
}
