package universalmod.mixin.render;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.render.hand.HandsRenderer;

@Mixin(GameRenderer.class)
public abstract class GameRendererHandsMixin {
    @Inject(method = "renderHand(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/Camera;F)V", at = @At("HEAD"))
    private void universalmod$beforeHands(MatrixStack matrices, Camera camera, float tickDelta, CallbackInfo ci) {
        HandsRenderer.captureBeforeHands();
    }

    @Inject(method = "renderHand(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/Camera;F)V", at = @At("RETURN"))
    private void universalmod$afterHands(MatrixStack matrices, Camera camera, float tickDelta, CallbackInfo ci) {
        HandsRenderer.captureAfterHands();
    }
}
