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
    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$filterParticle(ParticleEffect parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ, CallbackInfo ci) {
        if (universalmod$shouldCancelParticle(parameters)) {
            ci.cancel();
        }
    }

    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;ZDDDDDD)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$filterForcedParticle(ParticleEffect parameters, boolean alwaysSpawn, double x, double y, double z, double velocityX, double velocityY, double velocityZ, CallbackInfo ci) {
        if (universalmod$shouldCancelParticle(parameters)) {
            ci.cancel();
        }
    }

    @Inject(method = "addImportantParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$filterImportantParticle(ParticleEffect parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ, CallbackInfo ci) {
        if (universalmod$shouldCancelParticle(parameters)) {
            ci.cancel();
        }
    }

    private static boolean universalmod$shouldCancelParticle(ParticleEffect parameters) {
        NoRender noRender = NoRender.getInstance();
        if (noRender == null || parameters == null) {
            return false;
        }
        if (noRender.shouldHideParticles()) {
            return true;
        }
        if (!noRender.shouldHideHitParticles()) {
            return false;
        }
        return parameters.getType() == ParticleTypes.DAMAGE_INDICATOR
                || parameters.getType() == ParticleTypes.CRIT
                || parameters.getType() == ParticleTypes.ENCHANTED_HIT
                || parameters.getType() == ParticleTypes.SWEEP_ATTACK;
    }
}
