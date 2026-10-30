package universalmod.mixin.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.misc.CustomCrosshair;
import universalmod.module.utility.CustomDonate;
import universalmod.module.render.NoRender;
import universalmod.module.render.Scoreboard;
import universalmod.render.crosshair.CustomCrosshairRenderer;
import universalmod.render.crosshair.CrosshairRenderState;

@Mixin(InGameHud.class)
public final class InGameHudMixin {
    @ModifyArg(method = "renderScoreboardSidebar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/font/TextRenderer;getWidth(Lnet/minecraft/text/Text;)I"), index = 0, require = 0)
    private Text universalmod$customDonateScoreboardWidth(Text text) {
        return CustomDonate.replaceScoreboardLine(text);
    }

    @ModifyArg(method = "renderScoreboardSidebar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)I"), index = 1, require = 0)
    private Text universalmod$customDonateScoreboardDraw(Text text) {
        return CustomDonate.replaceScoreboardLine(text);
    }

    @Inject(method = "renderScoreboardSidebar", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalHud$replaceScoreboard(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
        if (NoRender.isActive("Scoreboard") || Scoreboard.isActive()) ci.cancel();
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$customCrosshair(DrawContext context, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        CustomCrosshair module = CustomCrosshair.getInstance();
        CrosshairRenderState state = module == null ? null : module.renderState();
        if (!CustomCrosshairRenderer.shouldRender(client, state)) return;
        CustomCrosshairRenderer.render(context, client, state);
        ci.cancel();
    }
}
