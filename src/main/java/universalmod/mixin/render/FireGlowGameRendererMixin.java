package universalmod.mixin.render;

import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.FireGlow;
import universalmod.render.fireglow.FireGlowFramebuffer;

@Mixin(GameRenderer.class)
public abstract class FireGlowGameRendererMixin {
    @Inject(method="render",at=@At("RETURN"))
    private void universalmod$compositeFireGlow(CallbackInfo ci) {
        if(FireGlow.isActive())FireGlowFramebuffer.composite();
    }
}
