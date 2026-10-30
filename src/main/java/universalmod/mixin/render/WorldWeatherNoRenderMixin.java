package universalmod.mixin.render;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.NoRender;

@Mixin(WorldRenderer.class)
public abstract class WorldWeatherNoRenderMixin {
    @Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
    private void universalmod$weather(LightmapTextureManager lightmap, float tickDelta, double cameraX, double cameraY, double cameraZ, CallbackInfo ci) {
        if (NoRender.isActive("Weather")) ci.cancel();
    }
}
