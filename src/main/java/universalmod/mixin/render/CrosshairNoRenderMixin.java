package universalmod.mixin.render;

import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.misc.CustomCrosshair;
import universalmod.module.render.NoRender;

@Mixin(InGameHud.class)
public abstract class CrosshairNoRenderMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void universalmod$crosshair(CallbackInfo ci) {
        CustomCrosshair c = CustomCrosshair.getInstance();
        if (NoRender.isActive("Vanilla Effects") || (c != null && c.isEnabled())) ci.cancel();
    }
}
