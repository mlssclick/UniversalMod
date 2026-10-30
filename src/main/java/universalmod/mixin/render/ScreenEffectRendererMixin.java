package universalmod.mixin.render;

import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.NoRender;

@Mixin(InGameOverlayRenderer.class)
public abstract class ScreenEffectRendererMixin {
    @Inject(method = "renderOverlays", at = @At("HEAD"), cancellable = true)
    private static void universalmod$effects(CallbackInfo ci) {
        if (NoRender.isActive("Vanilla Effects")) ci.cancel();
    }
}
