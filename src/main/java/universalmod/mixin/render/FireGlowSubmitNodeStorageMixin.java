package universalmod.mixin.render;

import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.access.FireGlowEntityStateCarrier;
import universalmod.module.render.FireGlow;
import universalmod.render.fireglow.LegacyFireOverlayRenderer;

@Mixin(EntityRenderDispatcher.class)
public abstract class FireGlowSubmitNodeStorageMixin {
    @Inject(method="renderFire",at=@At("HEAD"),cancellable=true)
    private void universalmod$submitFireGlow(MatrixStack matrices,VertexConsumerProvider consumers,EntityRenderState state,Quaternionf rotation,CallbackInfo ci) {
        if(!FireGlow.isActive() || ((FireGlowEntityStateCarrier)state).universalmod$getFireOutlineColor()==0)return;
        LegacyFireOverlayRenderer.submit(matrices,consumers,state,rotation);
        ci.cancel();
    }
}
