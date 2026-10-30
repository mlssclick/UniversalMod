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
public abstract class AspectRatioMixin {
    @Inject(method = "getBasicProjectionMatrix(F)Lorg/joml/Matrix4f;", at = @At("RETURN"), cancellable = true)
    private void universalmod$aspectRatio(float fovDegrees, CallbackInfoReturnable<Matrix4f> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return;
        int width = client.getWindow().getWidth();
        int height = client.getWindow().getHeight();
        if (width <= 0 || height <= 0) return;

        float adjustedWidth = AspectRatio.resolveAnimatedWidth(width, height);
        if (Math.abs(adjustedWidth - width) <= 0.0001F) return;
        float currentAspect = (float) width / (float) height;
        float targetAspect = adjustedWidth / (float) height;
        if (targetAspect <= 0.0001F) return;

        Matrix4f matrix = new Matrix4f(cir.getReturnValue());
        matrix.m00(matrix.m00() * (currentAspect / targetAspect));
        cir.setReturnValue(matrix);
    }
}
