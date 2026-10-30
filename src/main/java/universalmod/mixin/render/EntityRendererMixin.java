package universalmod.mixin.render;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.module.render.InvisibleTags;

@Mixin(LivingEntityRenderer.class)
public abstract class EntityRendererMixin<T extends LivingEntity> {
    @Inject(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void universalmod$invisibleTag(T entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof PlayerEntity player && InvisibleTags.shouldShowVanillaInvisibleTag(player)) {
            cir.setReturnValue(true);
        }
    }
}
