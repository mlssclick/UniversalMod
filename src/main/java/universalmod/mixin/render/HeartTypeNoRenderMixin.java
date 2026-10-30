package universalmod.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import universalmod.module.render.NoRender;

@Mixin(targets = "net.minecraft.client.gui.hud.InGameHud$HeartType")
public abstract class HeartTypeNoRenderMixin {
    @WrapOperation(
            method = "fromPlayerState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;hasStatusEffect(Lnet/minecraft/registry/entry/RegistryEntry;)Z"
            ),
            require = 0
    )
    private static boolean universalmod$hideWitherHeartsLiving(
            LivingEntity player,
            RegistryEntry<StatusEffect> effect,
            Operation<Boolean> original
    ) {
        if (effect == StatusEffects.WITHER && NoRender.isActive("Wither Hearts")) {
            return false;
        }
        return original.call(player, effect);
    }

    @WrapOperation(
            method = "fromPlayerState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;hasStatusEffect(Lnet/minecraft/registry/entry/RegistryEntry;)Z"
            ),
            require = 0
    )
    private static boolean universalmod$hideWitherHeartsPlayer(
            PlayerEntity player,
            RegistryEntry<StatusEffect> effect,
            Operation<Boolean> original
    ) {
        if (effect == StatusEffects.WITHER && NoRender.isActive("Wither Hearts")) {
            return false;
        }
        return original.call(player, effect);
    }
}
