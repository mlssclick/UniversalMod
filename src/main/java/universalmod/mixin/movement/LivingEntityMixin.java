package universalmod.mixin.movement;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.feature.cooldown.HolyWorldHealingCooldown;
import universalmod.module.misc.serverhelper.TrapDetectionService;

@Mixin(LivingEntity.class)
public final class LivingEntityMixin {
    @Inject(method = "tickActiveItemStack", at = @At("HEAD"), require = 0)
    private void universalmod$serverHelperUsingItemTick(CallbackInfo ci) {
        if ((Object) this instanceof PlayerEntity player) TrapDetectionService.recordFinishedUse(player);
    }

    @Inject(method = "consumeItem", at = @At("HEAD"), require = 0)
    private void universalHud$recordDrunkHealing(CallbackInfo ci) {
        if ((Object) this instanceof ClientPlayerEntity player) {
            ItemStack stack = player.getActiveItem();
            if (stack != null && !stack.isEmpty()) HolyWorldHealingCooldown.recordDrunk(player, stack.copy());
        }
    }
}
