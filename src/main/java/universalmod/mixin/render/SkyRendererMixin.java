package universalmod.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.Ambience;
import universalmod.module.render.Atmosphere;
import universalmod.render.world.ambience.SkyShaderRenderer;

@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
    @Inject(method = "renderSkyDisc(I)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$renderSkyShader(int color, CallbackInfo ci) {
        Atmosphere atmosphere = Atmosphere.getInstance();
        if (atmosphere != null && (atmosphere.isMoodActive() || atmosphere.isWinterActive())) {
            if (SkyShaderRenderer.renderAtmosphere(atmosphere)) {
                ci.cancel();
            }
            return;
        }
        Ambience ambience = Ambience.getInstance();
        if (ambience != null && ambience.isSkyShaderEnabled() && SkyShaderRenderer.render(ambience)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderStars(FLcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$hideVanillaStars(float brightness, PoseStack poseStack, CallbackInfo ci) {
        
        if (SkyShaderRenderer.wasAnySkyRendered()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSunMoonAndStars", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$hideVanillaCelestials(CallbackInfo ci) {
        Atmosphere atmosphere = Atmosphere.getInstance();
        if (atmosphere != null && (atmosphere.isMoodActive() || atmosphere.isWinterActive())
                && SkyShaderRenderer.wasAtmosphereSkyRendered()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderDarkDisc", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$hideVanillaDarkDisc(CallbackInfo ci) {
        Atmosphere atmosphere = Atmosphere.getInstance();
        if (atmosphere != null && (atmosphere.isMoodActive() || atmosphere.isWinterActive())
                && SkyShaderRenderer.wasAtmosphereSkyRendered()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true)
    private void universalmod$hideSunriseGlow(
            PoseStack poseStack,
            float tickDelta,
            int color,
            CallbackInfo ci
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft != null && minecraft.gameRenderer != null
                ? minecraft.gameRenderer.getMainCamera()
                : null;
        Ambience ambience = Ambience.getInstance();

        Atmosphere atmosphere = Atmosphere.getInstance();
        if ((ambience != null && ambience.shouldApplyCustomFog(camera))
                || (atmosphere != null && (atmosphere.shouldApplyWinterFog(camera)
                        || atmosphere.isMoodActive()))) {
            ci.cancel();
        }
    }
}
