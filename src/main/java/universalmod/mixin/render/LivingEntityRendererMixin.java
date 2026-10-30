package universalmod.mixin.render;

import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.module.render.InvisibleTags;
import universalmod.integration.minecraft.IMinecraft;

@Mixin({LivingEntityRenderer.class})
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>>
        extends EntityRenderer<T, S>
        implements FeatureRendererContext<S, M>,
        IMinecraft {
    @Shadow
    private static float clampBodyYaw(LivingEntity entity, float degrees, float tickDelta) {
        return 0.0F;
    }

    @Shadow
    public static boolean shouldFlipUpsideDown(LivingEntity entity) {
        return false;
    }

    protected LivingEntityRendererMixin(EntityRendererFactory.Context context) {
        super(context);
    }

    @Overwrite
    public void updateRenderState(T livingEntity, S livingEntityRenderState, float f) {
        super.updateRenderState(livingEntity, livingEntityRenderState, f);
        float yaw = MathHelper.lerpAngleDegrees(f, livingEntity.prevHeadYaw, livingEntity.headYaw);
        livingEntityRenderState.bodyYaw = clampBodyYaw(livingEntity, yaw, f);
        livingEntityRenderState.yawDegrees = MathHelper.wrapDegrees(yaw - livingEntityRenderState.bodyYaw);
        livingEntityRenderState.pitch = livingEntity.getLerpedPitch(f);
        livingEntityRenderState.customName = livingEntity.getCustomName();
        livingEntityRenderState.flipUpsideDown = shouldFlipUpsideDown(livingEntity);
        if (livingEntityRenderState.flipUpsideDown) {
            livingEntityRenderState.pitch *= -1.0F;
            livingEntityRenderState.yawDegrees *= -1.0F;
        }

        if (!livingEntity.hasVehicle() && livingEntity.isAlive()) {
            livingEntityRenderState.limbFrequency = livingEntity.limbAnimator.getPos(f);
            livingEntityRenderState.limbAmplitudeMultiplier = livingEntity.limbAnimator.getSpeed(f);
        } else {
            livingEntityRenderState.limbFrequency = 0.0F;
            livingEntityRenderState.limbAmplitudeMultiplier = 0.0F;
        }

        if (livingEntity.getVehicle() instanceof LivingEntity livingEntity2) {
            livingEntityRenderState.headItemAnimationProgress = livingEntity2.limbAnimator.getPos(f);
        } else {
            livingEntityRenderState.headItemAnimationProgress = livingEntityRenderState.limbFrequency;
        }

        livingEntityRenderState.baseScale = livingEntity.getScale();
        livingEntityRenderState.ageScale = livingEntity.getScaleFactor();
        livingEntityRenderState.pose = livingEntity.getPose();
        livingEntityRenderState.sleepingDirection = livingEntity.getSleepingDirection();
        if (livingEntityRenderState.sleepingDirection != null) {
            livingEntityRenderState.standingEyeHeight = livingEntity.getEyeHeight(EntityPose.STANDING);
        }

        livingEntityRenderState.shaking = livingEntity.isFrozen();
        livingEntityRenderState.baby = livingEntity.isBaby();
        livingEntityRenderState.touchingWater = livingEntity.isTouchingWater();
        livingEntityRenderState.usingRiptide = livingEntity.isUsingRiptide();
        livingEntityRenderState.hurt = livingEntity.hurtTime > 0 || livingEntity.deathTime > 0;
        livingEntityRenderState.deathTime = livingEntity.deathTime > 0 ? (float) livingEntity.deathTime + f : 0.0F;
        livingEntityRenderState.invisibleToPlayer = livingEntityRenderState.invisible && livingEntity.isInvisibleTo(MinecraftClient.getInstance().player);
        livingEntityRenderState.hasOutline = MinecraftClient.getInstance().hasOutline(livingEntity);
    }

    @Inject(
            method = "hasLabel(Lnet/minecraft/entity/LivingEntity;D)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void universalmod$showInvisiblePlayerLabel(
            T livingEntity,
            double squaredDistanceToCamera,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (livingEntity instanceof net.minecraft.entity.player.PlayerEntity player
                && InvisibleTags.shouldShowVanillaInvisibleTag(player)) {
            cir.setReturnValue(true);
        }
    }

}
