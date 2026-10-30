package universalmod.mixin.render;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.module.misc.Zoom;
import universalmod.render.optimize.UiFrameContext;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void universalmod$beginUiFrameScope(float tickDelta, long startTime, boolean tick, CallbackInfo ci) {
        UiFrameContext.begin(null);
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void universalmod$endUiFrameScope(float tickDelta, long startTime, boolean tick, CallbackInfo ci) {
        UiFrameContext.end();
    }
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void universalmod$zoomFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        Zoom zoom = Zoom.getInstance();
        if (zoom != null) {
            cir.setReturnValue(zoom.changeFovBasedOnZoom(cir.getReturnValue()));
        }
    }
}
