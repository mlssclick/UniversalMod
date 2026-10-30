package universalmod.mixin.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.module.render.HitColor;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityHitColorMixin<T extends LivingEntity> {
    @Unique private boolean universalmod$customHitTint;

    @Inject(method="render",at=@At("HEAD"))
    private void universalmod$hitColorBegin(T entity,float yaw,float tickDelta,MatrixStack matrices,VertexConsumerProvider consumers,int light,CallbackInfo ci){
        HitColor module=HitColor.getInstance();
        if(module==null||!module.isEnabled()||entity==null||entity.hurtTime<=0)return;
        int[] c=module.getHitColorRGBA();
        float a=c[3]/255f;
        RenderSystem.setShaderColor(1f-a+(c[0]/255f)*a,1f-a+(c[1]/255f)*a,1f-a+(c[2]/255f)*a,1f);
        universalmod$customHitTint=true;
    }

    @Inject(method="render",at=@At("TAIL"))
    private void universalmod$hitColorEnd(T entity,float yaw,float tickDelta,MatrixStack matrices,VertexConsumerProvider consumers,int light,CallbackInfo ci){
        if(universalmod$customHitTint){RenderSystem.setShaderColor(1f,1f,1f,1f);universalmod$customHitTint=false;}
    }

    @Inject(method="getOverlay",at=@At("HEAD"),cancellable=true)
    private static void universalmod$disableVanillaRedOverlay(LivingEntity entity,float whiteOverlayProgress,CallbackInfoReturnable<Integer> cir){
        HitColor module=HitColor.getInstance();
        if(module!=null&&module.isEnabled()&&entity!=null&&entity.hurtTime>0)cir.setReturnValue(OverlayTexture.DEFAULT_UV);
    }
}
