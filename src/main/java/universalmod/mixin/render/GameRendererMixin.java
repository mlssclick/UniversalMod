package universalmod.mixin.render;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.event.type.WorldRenderEvent;
import universalmod.lifecycle.Manager;
import universalmod.module.misc.Zoom;
import universalmod.module.render.Ambience;
import universalmod.module.render.NoRender;
import universalmod.render.Render3D;
import universalmod.render.post.Saturation2D;
import universalmod.render.optimize.UiFrameContext;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {






    @Inject(method = "render", at = @At("HEAD"))
    private void universalmod$beginUiFrameScope(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        UiFrameContext.begin(null);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void universalmod$endUiFrameScope(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        UiFrameContext.end();
    }

    @Inject(method = "renderWorld", at = @At("RETURN"), require = 0)
    private void universalmod$applyWorldSaturation(RenderTickCounter tickCounter, CallbackInfo ci) {
        Ambience ambience = Ambience.getInstance();
        if (ambience != null && ambience.isEnabled()) {
            Saturation2D.applyWithCopy(ambience.getSaturationFactor());
        }

    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void universalmod$applyZoomFov(
            Camera camera,
            float tickDelta,
            boolean changingFov,
            CallbackInfoReturnable<Float> cir
    ) {
        Zoom zoom = Zoom.getInstance();
        if (zoom != null) {
            cir.setReturnValue(zoom.changeFovBasedOnZoom(cir.getReturnValue()));
        }
    }

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$noHurtCamera(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (NoRender.isActive("Hurt Cam") || NoRender.isActive("Damage")) {
            ci.cancel();
        }
    }

    @Inject(method = "showFloatingItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$noTotemAnimation(ItemStack stack, CallbackInfo ci) {
        if (NoRender.isActive("Totem Animation")) {
            ci.cancel();
        }
    }

}
