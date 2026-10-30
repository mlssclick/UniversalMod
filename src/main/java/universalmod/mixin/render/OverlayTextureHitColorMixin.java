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
import universalmod.render.hitcolor.HitColorOverlayRegistry;
import universalmod.render.hitcolor.HitColorOverlayReloadable;

@Mixin(OverlayTexture.class)
public abstract class OverlayTextureHitColorMixin implements HitColorOverlayReloadable {
    @Shadow
    @Final
    private NativeImageBackedTexture texture;
    @Unique
    private int[] universalmod$vanillaPixels;

    @Inject(method = "<init>", at = @At("TAIL"), require = 0)
    private void universalmod$initHitColorOverlay(CallbackInfo ci) {
        NativeImage image = texture.getImage();
        if (image != null) {
            universalmod$captureVanillaPixels(image);
        }
        HitColorOverlayRegistry.register(this);
        universalmod$reloadHitColorOverlay();
    }

    @Override
    @Unique
    public void universalmod$reloadHitColorOverlay() {
        NativeImage image = texture.getImage();
        if (image == null) {
            return;
        }
        if (universalmod$vanillaPixels == null) {
            universalmod$captureVanillaPixels(image);
        }
        universalmod$restoreVanillaPixels(image);
        HitColor hitColor = HitColor.getInstance();
        if (hitColor != null && hitColor.isEnabled()) {
            int[] rgba = hitColor.getHitColorRGBA();
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 16; x++) {
                    image.setColor(x, y, universalmod$hitColorToNativeImage(rgba[0], rgba[1], rgba[2], rgba[3]));
                }
            }
        }
        texture.upload();
    }

    @Unique
    private void universalmod$captureVanillaPixels(NativeImage image) {
        universalmod$vanillaPixels = new int[16 * 16];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                universalmod$vanillaPixels[y * 16 + x] = image.getColor(x, y);
            }
        }
    }

    @Unique
    private void universalmod$restoreVanillaPixels(NativeImage image) {
        if (universalmod$vanillaPixels == null || universalmod$vanillaPixels.length != 16 * 16) {
            return;
        }
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                image.setColor(x, y, universalmod$vanillaPixels[y * 16 + x]);
            }
        }
    }

    @Unique
    private static int universalmod$hitColorToNativeImage(int red, int green, int blue, int alpha) {
        int correctedAlpha = 255 - Math.max(0, Math.min(255, alpha));
        return (correctedAlpha << 24)
                | ((blue & 0xFF) << 16)
                | ((green & 0xFF) << 8)
                | (red & 0xFF);
    }
}
