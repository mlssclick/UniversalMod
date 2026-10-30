package universalmod.mixin.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.module.render.AspectRatio;

@Mixin(GameRenderer.class)
public abstract class CameraAspectRatioHookMixin {
    @Inject(method = "getBasicProjectionMatrix", at = @At("RETURN"), cancellable = true)
    private void universalmod$aspect(double fov, CallbackInfoReturnable<Matrix4f> cir) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int w = mc.getWindow().getFramebufferWidth(), h = mc.getWindow().getFramebufferHeight();
        if (w <= 0 || h <= 0) return;
        float target = AspectRatio.resolveAnimatedWidth(w, h) / h;
        float vanilla = (float) w / h;
        if (Math.abs(target - vanilla) < .0005f) return;
        Matrix4f matrix = new Matrix4f(cir.getReturnValue());
        matrix.scale(vanilla / target, 1f, 1f);
        cir.setReturnValue(matrix);
    }
}
