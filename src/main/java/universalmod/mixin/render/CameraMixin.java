package universalmod.mixin.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import universalmod.module.render.Animations;
import universalmod.module.utility.FreeLook;
import universalmod.util.Angle;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @ModifyReturnValue(method = "getMaxZoom", at = @At("RETURN"))
    private float universalmod$animatePerspective(float collisionLimitedDistance, float desiredDistance) {
        if (!Animations.active(Animations.PERSPECTIVE)) {
            return collisionLimitedDistance;
        }
        float animatedDistance = Math.max(0.0F, desiredDistance * Animations.getInstance().perspectiveProgress());
        return Math.min(collisionLimitedDistance, animatedDistance);
    }

    @ModifyExpressionValue(
            method = "setup",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F")
    )
    private float universalmod$freeLookYaw(float original) {
        Angle angle = FreeLook.getActiveAngle();
        return angle != null ? angle.getYaw() : original;
    }

    @ModifyExpressionValue(
            method = "setup",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F")
    )
    private float universalmod$freeLookPitch(float original) {
        Angle angle = FreeLook.getActiveAngle();
        return angle != null ? angle.getPitch() : original;
    }
}
