package universalmod.mixin.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.misc.CustomCrosshair;
import universalmod.module.render.NoRender;
import universalmod.render.crosshair.CustomCrosshairRenderer;
import universalmod.render.crosshair.CrosshairRenderState;
import universalmod.lifecycle.HudRenderLayer;
import universalmod.gui.clickgui.ClickGui;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(method = "render", at = @At("RETURN"))
    private void universalmod$renderHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        HudRenderLayer.renderGame(context, tickCounter.getTickDelta(false));

        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.currentScreen == null
                && HudRenderLayer.shouldRender(client)
                && ClickGui.shouldRenderOverlay()) {
            ClickGui.renderPanels(context, context.getScaledWindowWidth(), context.getScaledWindowHeight());
        }
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$renderCustomCrosshair(
            DrawContext context,
            RenderTickCounter tickCounter,
            CallbackInfo ci
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        CustomCrosshair module = CustomCrosshair.getInstance();
        CrosshairRenderState state = module == null ? null : module.renderState();
        if (!CustomCrosshairRenderer.shouldRender(client, state)) {
            return;
        }

        CustomCrosshairRenderer.render(context, client, state);
        ci.cancel();
    }

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$hideVanillaEffects(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (NoRender.isActive("Vanilla Effects")) {
            ci.cancel();
        }
    }

}
