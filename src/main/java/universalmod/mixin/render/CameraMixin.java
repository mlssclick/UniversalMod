package universalmod.mixin.render;

import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.Animations;
import universalmod.module.utility.FreeLook;
import universalmod.lifecycle.Manager;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract float clipToSpace(float desiredDistance);


    @Redirect(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"
            )
    )
    private void universalmod$applyCameraRotation(
            Camera camera,
            float yaw,
            float pitch,
            BlockView area,
            Entity focusedEntity,
            boolean thirdPerson,
            boolean inverseView,
            float tickDelta
    ) {
        FreeLook freeLook = Manager.getModules() == null ? null : Manager.getModules().get(FreeLook.class);
        float finalYaw = freeLook != null && freeLook.isActive() ? freeLook.getCameraYaw() : yaw;
        float finalPitch = freeLook != null && freeLook.isActive() ? freeLook.getCameraPitch() : pitch;
        if (thirdPerson && inverseView) {
            finalYaw += 180.0F;
            finalPitch = -finalPitch;
        }
        this.setRotation(finalYaw, finalPitch);
    }

    @Redirect(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/Camera;clipToSpace(F)F"
            ),
            require = 0
    )
    private float universalmod$animatePerspectiveDistance(Camera camera, float desiredDistance) {
        if (!Animations.active(Animations.PERSPECTIVE)) {
            return this.clipToSpace(desiredDistance);
        }
        return this.clipToSpace(desiredDistance * Animations.getInstance().perspectiveProgress());
    }
}
