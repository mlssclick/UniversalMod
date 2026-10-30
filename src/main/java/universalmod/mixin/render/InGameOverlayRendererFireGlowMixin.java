package universalmod.mixin.render;

import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.NoRender;
import universalmod.module.render.FireGlow;
import universalmod.render.fireglow.FireGlowRenderer;

@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererFireGlowMixin {
    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private static void universalmod$captureBeforeFire(MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        if (NoRender.isActive("Fire")) {
            ci.cancel();
            return;
        }
        FireGlow module = FireGlow.getInstance();
        FireGlowRenderer.captureBefore(module == null ? null : module.renderState());
    }

    @Inject(method = "renderFireOverlay", at = @At("RETURN"), require = 0)
    private static void universalmod$compositeFireGlow(MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        if (!NoRender.isActive("Fire")) {
            FireGlow module = FireGlow.getInstance();
            if (module != null) {
                module.syncToConfig();
            }
            FireGlowRenderer.compositeAfter(module == null ? null : module.renderState());
        }
    }

    @Inject(method = "renderInWallOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private static void universalmod$hideBlockOverlay(
            net.minecraft.client.texture.Sprite sprite,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            CallbackInfo ci
    ) {
        if (NoRender.isActive("Block Overlay")) {
            ci.cancel();
        }
    }
}
