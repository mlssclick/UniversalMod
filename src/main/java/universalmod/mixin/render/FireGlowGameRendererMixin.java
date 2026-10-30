package universalmod.mixin.render;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.FireGlow;
import universalmod.render.fireglow.FireGlowFramebuffer;

@Mixin(GameRenderer.class)
public abstract class FireGlowGameRendererMixin {
    @Shadow
    @Final
    private CrossFrameResourcePool resourcePool;

    @Inject(method = "render", at = @At("RETURN"))
    private void universalmod$compositeFireGlow(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        if (FireGlow.isActive()) {
            FireGlowFramebuffer.composite(resourcePool);
        }
    }
}
