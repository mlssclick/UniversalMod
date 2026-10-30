package universalmod.mixin.render;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.module.misc.Fullbright;
import universalmod.util.GlowPropagator;

@Mixin(EntityRenderDispatcher.class)
public abstract class DynamicLightEntityRenderDispatcherMixin {
    @Inject(method = "getPackedLightCoords", at = @At("RETURN"), cancellable = true)
    private <E extends Entity> void universalmod$dynamicEntityLight(E entity, float tickProgress, CallbackInfoReturnable<Integer> cir) {
        Fullbright fullbright = Fullbright.getInstance();
        if (fullbright != null && fullbright.isDynamicMode()) {
            BlockPos pos = BlockPos.containing(entity.getLightProbePosition(tickProgress));
            cir.setReturnValue(GlowPropagator.applyBlockLight(pos, cir.getReturnValueI()));
        }
    }
}
