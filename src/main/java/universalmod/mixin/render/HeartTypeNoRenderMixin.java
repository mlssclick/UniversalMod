package universalmod.mixin.render;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import universalmod.module.render.NoRender;

@Mixin(targets = "net.minecraft.client.gui.hud.InGameHud$HeartType")
public abstract class HeartTypeNoRenderMixin {
    @Redirect(method = "fromPlayerState", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;hasStatusEffect(Lnet/minecraft/entity/effect/StatusEffect;)Z"), require = 0)
    private static boolean universalmod$hideWitherHeartPlayerOwner(PlayerEntity player, StatusEffect effect) {
        if (effect == StatusEffects.WITHER && NoRender.isActive("Wither Hearts")) {
            return false;
        }
        return player.hasStatusEffect(effect);
    }

    @Redirect(method = "fromPlayerState", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;hasStatusEffect(Lnet/minecraft/entity/effect/StatusEffect;)Z"), require = 0)
    private static boolean universalmod$hideWitherHeartLivingOwner(LivingEntity entity, StatusEffect effect) {
        if (effect == StatusEffects.WITHER && NoRender.isActive("Wither Hearts")) {
            return false;
        }
        return entity.hasStatusEffect(effect);
    }
}
