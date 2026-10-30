package universalmod.mixin.render;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.module.misc.Fullbright;
import universalmod.util.render.world.GlowPropagator;

@Mixin(WorldRenderer.class)
public abstract class DynamicLightWorldRendererMixin {
    @Inject(method = "getLightmapCoordinates(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;)I", at = @At("RETURN"), cancellable = true)
    private static void universalmod$dynamicBlockLight(BlockRenderView world, BlockState state, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        Fullbright fullbright = Fullbright.getInstance();
        if (fullbright != null && fullbright.isDynamicMode()) cir.setReturnValue(GlowPropagator.applyBlockLight(pos, cir.getReturnValueI()));
    }
}
