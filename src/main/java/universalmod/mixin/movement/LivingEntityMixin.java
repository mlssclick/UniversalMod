package universalmod.mixin.movement;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.event.type.SwingDurationEvent;
import universalmod.lifecycle.Manager;
import universalmod.feature.cooldown.HolyWorldHealingCooldown;
import universalmod.module.misc.ServerHelper.Detection;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Inject(method = "tickActiveItemStack", at = @At("HEAD"), require = 0)
    private void universalmod$serverHelperUsingItemTick(CallbackInfo ci) {
        if ((Object) this instanceof PlayerEntity player) {
            Detection.recordFinishedUse(player);
        }
    }

    @Inject(method = "consumeItem", at = @At("HEAD"), require = 0)
    private void universalmod$recordDrunkHealing(CallbackInfo ci) {
        if ((Object) this instanceof ClientPlayerEntity player) {
            ItemStack stack = player.getActiveItem();
            if (stack != null && !stack.isEmpty()) {
                HolyWorldHealingCooldown.recordDrunk(player, stack.copy());
            }
        }
    }

    @Inject(method = "getHandSwingDuration", at = @At("HEAD"), cancellable = true)
    private void universalmod$swingDuration(CallbackInfoReturnable<Integer> cir) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || (Object) this != player) {
            return;
        }
        SwingDurationEvent event = Manager.postEvent(new SwingDurationEvent());
        if (!event.isCancelled()) {
            return;
        }
        float animation = event.getAnimation();
        if (StatusEffectUtil.hasHaste(player)) {
            animation *= 6 - (1 + StatusEffectUtil.getHasteAmplifier(player));
        } else {
            var fatigue = player.getStatusEffect(StatusEffects.MINING_FATIGUE);
            animation *= fatigue != null ? 6 + (1 + fatigue.getAmplifier()) * 2 : 6;
        }
        cir.setReturnValue(Math.max(1, (int) animation));
    }

}
