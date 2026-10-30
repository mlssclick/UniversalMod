package universalmod.mixin.render;

import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.module.misc.Fullbright;
import universalmod.util.render.world.GlowPropagator;

@Mixin(EntityRenderDispatcher.class)
public abstract class DynamicLightEntityRenderDispatcherMixin {
    @Inject(method = "getLight", at = @At("RETURN"), cancellable = true)
    private <E extends Entity> void universalmod$dynamicEntityLight(E entity, float tickDelta, CallbackInfoReturnable<Integer> cir) {
        Fullbright fullbright = Fullbright.getInstance();
        if (fullbright != null && fullbright.isDynamicMode()) {
            BlockPos pos = BlockPos.ofFloored(entity.getClientCameraPosVec(tickDelta));
            cir.setReturnValue(GlowPropagator.applyBlockLight(pos, cir.getReturnValueI()));
        }
    }
}
