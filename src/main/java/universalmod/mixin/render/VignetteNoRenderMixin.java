package universalmod.mixin.render;

import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.NoRender;

@Mixin(InGameHud.class)
public abstract class VignetteNoRenderMixin {
    @Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true)
    private void universalmod$vignette(CallbackInfo ci) {
        if (NoRender.isActive("Vanilla Effects")) ci.cancel();
    }
}
