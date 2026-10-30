package universalmod.mixin.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.misc.CustomCrosshair;
import universalmod.module.render.NoRender;
import universalmod.module.render.Scoreboard;
import universalmod.module.utility.CustomDonate;
import universalmod.render.crosshair.CrosshairRenderState;
import universalmod.render.crosshair.CustomCrosshairRenderer;

@Mixin(InGameHud.class)
public abstract class GuiNoRenderMixin {
    @Inject(method = "renderScoreboardSidebar", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$scoreboard(DrawContext graphics, ScoreboardObjective objective, CallbackInfo ci) {
        if (NoRender.isActive("Scoreboard") || Scoreboard.isActive()) ci.cancel();
    }

    @ModifyArg(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/font/TextRenderer;getWidth(Lnet/minecraft/text/StringVisitable;)I", ordinal = 1),
            index = 0, require = 0
    )
    private StringVisitable universalmod$replaceDonateScoreboardWidth(StringVisitable text) {
        return text instanceof Text component ? CustomDonate.replaceScoreboardLine(component) : text;
    }

    @ModifyArg(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)I", ordinal = 1),
            index = 1, require = 0
    )
    private Text universalmod$replaceDonateScoreboardDraw(Text text) {
        return CustomDonate.replaceScoreboardLine(text);
    }

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$vanillaEffects(DrawContext graphics, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (NoRender.isActive("Vanilla Effects")) ci.cancel();
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$customCrosshair(DrawContext graphics, RenderTickCounter tickCounter, CallbackInfo ci) {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        CustomCrosshair module = CustomCrosshair.getInstance();
        CrosshairRenderState state = module == null ? null : module.renderState();
        if (CustomCrosshairRenderer.shouldRender(minecraft, state)) {
            CustomCrosshairRenderer.render(graphics, minecraft, state);
            ci.cancel();
        }
    }
}
