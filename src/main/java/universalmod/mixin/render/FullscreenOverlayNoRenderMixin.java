package universalmod.mixin.render;

import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.NoRender;

@Mixin(InGameHud.class)
public abstract class FullscreenOverlayNoRenderMixin {
    @Inject(method = "renderSpyglassOverlay", at = @At("HEAD"), cancellable = true)
    private void universalmod$overlay(CallbackInfo ci) {
        if (NoRender.isActive("Vanilla Effects")) ci.cancel();
    }
}
