package universalmod.mixin.render;

import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.NoRender;

@Mixin(GameRenderer.class)
public abstract class GameRendererHurtCameraMixin {
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void universalmod$hurt(CallbackInfo ci) {
        if (NoRender.isActive("Hurt Cam") || NoRender.isActive("Damage")) {
            ci.cancel();
        }
    }
}
