package universalmod.mixin.render;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.module.misc.Fullbright;
import universalmod.util.GlowPropagator;

@Mixin(LevelRenderer.class)
public abstract class DynamicLightWorldRendererMixin {
    @Inject(method = "getLightColor(Lnet/minecraft/client/renderer/LevelRenderer$BrightnessGetter;Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I", at = @At("RETURN"), cancellable = true)
    private static void universalmod$dynamicBlockLight(LevelRenderer.BrightnessGetter brightnessGetter, BlockAndTintGetter world, BlockState state, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        Fullbright fullbright = Fullbright.getInstance();
        if (fullbright != null && fullbright.isDynamicMode()) cir.setReturnValue(GlowPropagator.applyBlockLight(pos, cir.getReturnValueI()));
    }
}
