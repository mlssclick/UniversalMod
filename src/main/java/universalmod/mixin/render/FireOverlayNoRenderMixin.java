package universalmod.mixin.render;

import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.NoRender;
import universalmod.render.fireglow.FireGlowRenderer;
import universalmod.render.fireglow.FireGlowState;
import universalmod.module.render.FireGlow;

@Mixin(InGameOverlayRenderer.class)
public abstract class FireOverlayNoRenderMixin {
    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void universalmod$fireHead(CallbackInfo ci) {
        if (NoRender.isActive("Fire")) {
            ci.cancel();
            return;
        }
        FireGlow module = FireGlow.getInstance();
        FireGlowRenderer.beforeFire(module == null ? null : module.renderState());
    }

    @Inject(method = "renderFireOverlay", at = @At("TAIL"))
    private static void universalmod$fireTail(CallbackInfo ci) {
        FireGlow module = FireGlow.getInstance();
        FireGlowRenderer.afterFire(module == null ? null : module.renderState());
    }
}
