package universalmod.mixin.render;

import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.NoRender;

 




@Mixin(BipedEntityModel.class)
public abstract class BipedPlayerAnimationsNoRenderMixin {
    @Unique private boolean universalmod$restoreHandSwing;
    @Unique private float universalmod$originalHandSwing;

    @ModifyVariable(
            method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 1
    )
    private float universalmod$hidePlayerWalk(float limbDistance, LivingEntity entity, float limbAngle,
                                               float originalLimbDistance, float animationProgress,
                                               float headYaw, float headPitch) {
        return entity instanceof PlayerEntity && NoRender.isActive("Player Walk") ? 0.0F : limbDistance;
    }

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
    private void universalmod$beforePlayerSwing(LivingEntity entity, float limbAngle, float limbDistance,
                                                  float animationProgress, float headYaw, float headPitch,
                                                  CallbackInfo ci) {
        universalmod$restoreHandSwing = entity instanceof PlayerEntity && NoRender.isActive("Player Swing");
        if (universalmod$restoreHandSwing) {
            BipedEntityModel<?> model = (BipedEntityModel<?>) (Object) this;
            universalmod$originalHandSwing = model.handSwingProgress;
            model.handSwingProgress = 0.0F;
        }
    }

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void universalmod$afterPlayerSwing(LivingEntity entity, float limbAngle, float limbDistance,
                                                 float animationProgress, float headYaw, float headPitch,
                                                 CallbackInfo ci) {
        if (universalmod$restoreHandSwing) {
            ((BipedEntityModel<?>) (Object) this).handSwingProgress = universalmod$originalHandSwing;
            universalmod$restoreHandSwing = false;
        }
    }
}
