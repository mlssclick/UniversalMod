package universalmod.mixin.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.Ambience;
import universalmod.render.world.ambience.SkyShaderRenderer;

@Mixin(SkyRendering.class)
public abstract class SkyRenderingMixin {
    @Inject(method = "renderSky(FFF)V", at = @At("HEAD"), cancellable = true)
    private void universalmod$renderUniversalSky(float red, float green, float blue, CallbackInfo ci) {
        Ambience ambience = Ambience.getInstance();
        if (ambience != null && ambience.isSkyShaderEnabled() && SkyShaderRenderer.render(ambience.skyShaderState())) {
            ci.cancel();
        }
    }

    @Inject(method = "renderStars(Lnet/minecraft/client/render/Fog;FLnet/minecraft/client/util/math/MatrixStack;)V", at = @At("HEAD"), cancellable = true)
    private void universalmod$hideVanillaStars(Fog fog, float color, MatrixStack matrices, CallbackInfo ci) {
        Ambience ambience = Ambience.getInstance();
        if (ambience != null && ambience.isSkyShaderEnabled() && SkyShaderRenderer.lastRenderSucceeded()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderGlowingSky(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;FI)V", at = @At("HEAD"), cancellable = true)
    private void universalmod$hideSunriseGlow(
            MatrixStack matrices,
            VertexConsumerProvider.Immediate vertexConsumers,
            float angleRadians,
            int color,
            CallbackInfo ci
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        Camera camera = client != null && client.gameRenderer != null ? client.gameRenderer.getCamera() : null;
        Ambience ambience = Ambience.getInstance();
        if (ambience != null && ambience.shouldApplyCustomFog(camera)) {
            ci.cancel();
        }
    }
}
