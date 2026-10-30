package universalmod.mixin.render;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.render.hand.HandsRenderer;

@Mixin(GameRenderer.class)
public abstract class GameRendererHandsMixin {
    @Inject(method = "renderHand(Lnet/minecraft/client/render/Camera;FLorg/joml/Matrix4f;)V", at = @At("HEAD"))
    private void universalmod$beforeHands(Camera camera, float tickDelta, Matrix4f projectionMatrix, CallbackInfo ci) {
        HandsRenderer.captureBeforeHands();
    }

    @Inject(method = "renderHand(Lnet/minecraft/client/render/Camera;FLorg/joml/Matrix4f;)V", at = @At("RETURN"))
    private void universalmod$afterHands(Camera camera, float tickDelta, Matrix4f projectionMatrix, CallbackInfo ci) {
        HandsRenderer.captureAfterHands();
    }
}
