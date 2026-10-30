package universalmod.mixin.render;

import java.awt.Color;

import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.BackgroundRenderer.FogType;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.FogShape;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.module.render.Ambience;
import universalmod.module.render.Atmosphere;
import universalmod.module.render.NoRender;

@Mixin(BackgroundRenderer.class)
public abstract class FogMixin {
    @Inject(method = "getFogColor", at = @At("RETURN"), cancellable = true, require = 0)
    private static void universalmod$customFogColor(
            Camera camera,
            float tickDelta,
            ClientWorld world,
            int viewDistance,
            float skyDarkness,
            CallbackInfoReturnable<Vector4f> cir
    ) {
        Vector4f finalColor = cir.getReturnValue();
        Atmosphere atmosphere = Atmosphere.getInstance();
        boolean winter = atmosphere != null &&
                (atmosphere.shouldApplyWinterFog(camera));
        Ambience ambience = Ambience.getInstance();

        if (winter || (ambience != null && ambience.shouldApplyCustomFog(camera))) {
            Color color = new Color(winter ? atmosphere.getFogColor() : ambience.getCustomFogColor(), true);
            float alpha = finalColor == null ? 1.0F : finalColor.w;
            finalColor = new Vector4f(
                    color.getRed() / 255.0F,
                    color.getGreen() / 255.0F,
                    color.getBlue() / 255.0F,
                    alpha
            );
            cir.setReturnValue(finalColor);
        }

        if (finalColor != null) {
        }
    }

    @Inject(method = "applyFog", at = @At("RETURN"), cancellable = true, require = 0)
    private static void universalmod$customFogDistance(
            Camera camera,
            FogType fogType,
            Vector4f color,
            float viewDistance,
            boolean thickenFog,
            float tickDelta,
            CallbackInfoReturnable<Fog> cir
    ) {
        if (NoRender.shouldRemoveVanillaFog(camera)) {
            cir.setReturnValue(new Fog(
                    1_000_000.0F,
                    1_000_001.0F,
                    FogShape.SPHERE,
                    color == null ? 1.0F : color.x,
                    color == null ? 1.0F : color.y,
                    color == null ? 1.0F : color.z,
                    color == null ? 1.0F : color.w
            ));
            return;
        }

        Atmosphere atmosphere = Atmosphere.getInstance();
        boolean winter = atmosphere != null &&
                (atmosphere.shouldApplyWinterFog(camera));
        Ambience ambience = Ambience.getInstance();
        if (!winter && (ambience == null || !ambience.shouldApplyCustomFog(camera))) {
            return;
        }

        Color customColor = new Color(winter ? atmosphere.getFogColor() : ambience.getCustomFogColor(), true);
        cir.setReturnValue(new Fog(
                winter ? atmosphere.getFogStart() : ambience.getFogStart(),
                winter ? atmosphere.getFogDistance() : ambience.getFogDistance(),
                FogShape.SPHERE,
                customColor.getRed() / 255.0F,
                customColor.getGreen() / 255.0F,
                customColor.getBlue() / 255.0F,
                color == null ? 1.0F : color.w
        ));
    }
}
