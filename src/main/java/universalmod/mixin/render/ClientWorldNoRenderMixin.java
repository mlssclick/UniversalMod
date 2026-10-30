package universalmod.mixin.render;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.NoRender;

@Mixin(ClientWorld.class)
public abstract class ClientWorldNoRenderMixin {
    @Inject(
            method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void universalmod$filterParticle(
            ParticleEffect parameters,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ,
            CallbackInfo ci
    ) {
        if (universalmod$shouldCancelParticle(parameters)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "addParticle(Lnet/minecraft/particle/ParticleEffect;ZZDDDDDD)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void universalmod$filterForcedParticle(
            ParticleEffect parameters,
            boolean force,
            boolean canSpawnOnMinimal,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ,
            CallbackInfo ci
    ) {
        if (universalmod$shouldCancelParticle(parameters)) {
            ci.cancel();
        }
    }

    private static boolean universalmod$shouldCancelParticle(ParticleEffect parameters) {
        if (parameters == null) {
            return false;
        }
        if (NoRender.isActive("Particles")) {
            return true;
        }
        if (!NoRender.isActive("Hit Particles")) {
            return false;
        }
        return parameters.getType() == ParticleTypes.DAMAGE_INDICATOR
                || parameters.getType() == ParticleTypes.CRIT
                || parameters.getType() == ParticleTypes.ENCHANTED_HIT
                || parameters.getType() == ParticleTypes.SWEEP_ATTACK;
    }
}
