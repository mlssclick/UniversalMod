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
    @Inject(method = "hasPrecipitation", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$overrideHasPrecipitation(CallbackInfoReturnable<Boolean> cir) {
        if (NoRender.isActive("Weather")) {
            cir.setReturnValue(false);
            return;
        }
        Ambience ambience = Ambience.getInstance();
        Biome.Precipitation precipitation = ambience == null ? null : ambience.getForcedPrecipitation();
        if (precipitation != null) {
            cir.setReturnValue(precipitation != Biome.Precipitation.NONE);
        }
    }

    @Inject(method = "getPrecipitation", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$overridePrecipitation(
            BlockPos pos,
            int seaLevel,
            CallbackInfoReturnable<Biome.Precipitation> cir
    ) {
        if (NoRender.isActive("Weather")) {
            cir.setReturnValue(Biome.Precipitation.NONE);
            return;
        }
        Ambience ambience = Ambience.getInstance();
        Biome.Precipitation precipitation = ambience == null ? null : ambience.getForcedPrecipitation();
        if (precipitation != null) {
            cir.setReturnValue(precipitation);
        }
    }
}
