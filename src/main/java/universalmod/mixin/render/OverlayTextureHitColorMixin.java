package universalmod.mixin.render;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.HitColor;
import universalmod.render.HitColorOverlayRegistry;
import universalmod.render.HitColorOverlayReloadable;

@Mixin(OverlayTexture.class)
public abstract class OverlayTextureHitColorMixin implements HitColorOverlayReloadable {
    @Shadow
    @Final
    private NativeImageBackedTexture texture;

    @Inject(method = "<init>", at = @At("TAIL"), require = 0)
    private void universalmod$initHitColorOverlay(CallbackInfo ci) {
        HitColorOverlayRegistry.register(this);
        universalmod$reloadHitColorOverlay();
    }

    @Override
    @Unique
    public void universalmod$reloadHitColorOverlay() {
        NativeImage image = texture.getImage();
        if (image == null) return;
        HitColor module = HitColor.getInstance();
        boolean useCustomColor = module != null && module.isEnabled();
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (y < 8) {
                    if (useCustomColor) {
                        int[] rgba = module.getHitColorRGBA();
                        image.setColorArgb(x, y, argb(rgba[0], rgba[1], rgba[2], 255 - rgba[3]));
                    } else {
                        image.setColorArgb(x, y, -1291911168);
                    }
                } else {
                    int vanillaAlpha = (int) ((1.0F - (float) x / 15.0F * 0.75F) * 255.0F);
                    image.setColorArgb(x, y, argb(255, 255, 255, vanillaAlpha));
                }
            }
        }
        texture.upload();
    }

    @Unique
    private static int argb(int r, int g, int b, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (r << 16) | (g << 8) | b;
    }
}
