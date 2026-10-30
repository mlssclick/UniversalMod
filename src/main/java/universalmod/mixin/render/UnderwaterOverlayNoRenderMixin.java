package universalmod.mixin.render;

import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.NoRender;

@Mixin(InGameOverlayRenderer.class)
public abstract class UnderwaterOverlayNoRenderMixin {
    @Inject(method = "renderUnderwaterOverlay", at = @At("HEAD"), cancellable = true)
    private static void universalmod$water(CallbackInfo ci) {
        if (NoRender.isActive("Vanilla Effects")) ci.cancel();
    }
}
