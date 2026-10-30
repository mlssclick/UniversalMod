package universalmod.mixin.render;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.Camera;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.SkyRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.Ambience;
import universalmod.module.render.Atmosphere;
import universalmod.render.world.ambience.SkyShaderRenderer;

@Mixin(SkyRendering.class)
public abstract class SkyRendererMixin {
    @Inject(method = "renderSky(FFF)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$renderSkyShader(CallbackInfo ci) {
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

    @Inject(method = "renderStars", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$hideVanillaStars(CallbackInfo ci) {
        
        if (SkyShaderRenderer.wasAnySkyRendered()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderCelestialBodies", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$hideVanillaCelestials(CallbackInfo ci) {
        Atmosphere atmosphere = Atmosphere.getInstance();
        if (atmosphere != null && (atmosphere.isMoodActive() || atmosphere.isWinterActive())
                && SkyShaderRenderer.wasAtmosphereSkyRendered()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSkyDark", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$hideVanillaDarkDisc(CallbackInfo ci) {
        Atmosphere atmosphere = Atmosphere.getInstance();
        if (atmosphere != null && (atmosphere.isMoodActive() || atmosphere.isWinterActive())
                && SkyShaderRenderer.wasAtmosphereSkyRendered()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderGlowingSky", at = @At("HEAD"), cancellable = true)
    private void universalmod$hideSunriseGlow(CallbackInfo ci) {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        Camera camera = minecraft != null && minecraft.gameRenderer != null
                ? minecraft.gameRenderer.getCamera()
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
