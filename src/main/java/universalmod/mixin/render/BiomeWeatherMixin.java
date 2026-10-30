package universalmod.mixin.render;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.module.render.Ambience;
import universalmod.module.render.NoRender;

@Mixin(Biome.class)
public abstract class BiomeWeatherMixin {
    @Inject(method = "hasPrecipitation", at = @At("HEAD"), cancellable = true)
    private void universalmod$has(CallbackInfoReturnable<Boolean> cir) {
        if (NoRender.isActive("Weather")) {
            cir.setReturnValue(false);
            return;
        }
        Ambience a = Ambience.getInstance();
        Biome.Precipitation p = a == null ? null : a.getForcedPrecipitation();
        if (p != null) cir.setReturnValue(p != Biome.Precipitation.NONE);
    }

    @Inject(method = "getPrecipitation", at = @At("HEAD"), cancellable = true)
    private void universalmod$type(BlockPos pos, CallbackInfoReturnable<Biome.Precipitation> cir) {
        if (NoRender.isActive("Weather")) {
            cir.setReturnValue(Biome.Precipitation.NONE);
            return;
        }
        Ambience a = Ambience.getInstance();
        Biome.Precipitation p = a == null ? null : a.getForcedPrecipitation();
        if (p != null) cir.setReturnValue(p);
    }
}
