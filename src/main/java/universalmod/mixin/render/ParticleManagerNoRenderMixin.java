package universalmod.mixin.render;

import net.minecraft.block.BlockState;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.NoRender;

@Mixin(ParticleManager.class)
public abstract class ParticleManagerNoRenderMixin {
    @Inject(method = "addBlockBreakParticles", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$hideBlockBreakParticles(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (NoRender.isActive("Block Particles")) {
            ci.cancel();
        }
    }
}
