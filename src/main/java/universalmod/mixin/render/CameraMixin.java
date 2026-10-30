package universalmod.mixin.render;

import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import universalmod.module.render.Animations;
import universalmod.module.utility.FreeLook;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @ModifyVariable(method = "clipToSpace", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double universalmod$animatePerspective(double desiredDistance) {
        if (!Animations.active(Animations.PERSPECTIVE)) return desiredDistance;
        Animations a = Animations.getInstance();
        return desiredDistance * (a == null ? 1.0 : a.perspectiveProgress());
    }

    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw(F)F"))
    private float universalmod$freeLookYaw(Entity entity, float tickDelta) {
        FreeLook freeLook = FreeLook.getInstance();
        return freeLook != null && freeLook.isActive() ? freeLook.getCameraYaw() : entity.getYaw(tickDelta);
    }

    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getPitch(F)F"))
    private float universalmod$freeLookPitch(Entity entity, float tickDelta) {
        FreeLook freeLook = FreeLook.getInstance();
        return freeLook != null && freeLook.isActive() ? freeLook.getCameraPitch() : entity.getPitch(tickDelta);
    }
}
