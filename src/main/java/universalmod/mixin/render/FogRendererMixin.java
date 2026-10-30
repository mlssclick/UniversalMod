package universalmod.mixin.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.FogShape;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.Ambience;
import universalmod.module.render.Atmosphere;
import universalmod.module.render.NoRender;

@Mixin(BackgroundRenderer.class)
public abstract class FogRendererMixin {
    @Unique
    private static final float NO_FOG_START = 1_000_000.0F;
    @Unique
    private static final float NO_FOG_END = 1_000_001.0F;

    @Inject(method = "applyFog", at = @At("TAIL"))
    private static void universalmod$applyFog(Camera camera, BackgroundRenderer.FogType type, float viewDistance, boolean thickFog, float tickDelta, CallbackInfo ci) {
        if (NoRender.shouldRemoveVanillaFog(camera)) {
            RenderSystem.setShaderFogStart(NO_FOG_START);
            RenderSystem.setShaderFogEnd(NO_FOG_END);
            RenderSystem.setShaderFogShape(FogShape.SPHERE);
            return;
        }
        Ambience ambience = Ambience.getInstance();
        Atmosphere atmosphere = Atmosphere.getInstance();
        boolean winter = atmosphere != null && atmosphere.shouldApplyWinterFog(camera);
        if (!winter && (ambience == null || !ambience.shouldApplyCustomFog(camera))) return;
        float distance = winter ? atmosphere.getFogDistance() : ambience.getFogDistance();
        float start = type == BackgroundRenderer.FogType.FOG_SKY ? 0.0F
                : winter ? atmosphere.getFogStart() : ambience.getFogStart();
        start = Math.max(0.0F, Math.min(start, distance - 0.01F));
        RenderSystem.setShaderFogStart(start);
        RenderSystem.setShaderFogEnd(distance);
        RenderSystem.setShaderFogShape(FogShape.SPHERE);
        universalmod$setFogColor(winter ? atmosphere.getFogColor() : ambience.getCustomFogColor());
    }

    @Inject(method = "render", at = @At("TAIL"))
    private static void universalmod$backgroundColor(Camera camera, float tickDelta, ClientWorld world, int viewDistance, float skyDarkness, CallbackInfo ci) {
        Ambience ambience = Ambience.getInstance();
        Atmosphere atmosphere = Atmosphere.getInstance();
        boolean winter = atmosphere != null && atmosphere.shouldApplyWinterFog(camera);
        if (!winter && (ambience == null || !ambience.shouldApplyCustomFog(camera))) return;
        int color = winter ? atmosphere.getFogColor() : ambience.getCustomFogColor();
        float r = ((color >> 16) & 255) / 255.0F;
        float g = ((color >> 8) & 255) / 255.0F;
        float b = (color & 255) / 255.0F;
        RenderSystem.clearColor(r, g, b, 0.0F);
        RenderSystem.setShaderFogColor(r, g, b);
    }

    @Unique
    private static void universalmod$setFogColor(int color) {
        RenderSystem.setShaderFogColor(((color >> 16) & 255) / 255.0F, ((color >> 8) & 255) / 255.0F, (color & 255) / 255.0F);
    }
}
