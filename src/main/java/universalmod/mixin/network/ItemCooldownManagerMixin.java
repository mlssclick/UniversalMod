package universalmod.mixin.network;

import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.feature.cooldown.CooldownStateStorage;

@Mixin(ItemCooldownManager.class)
public abstract class ItemCooldownManagerMixin {
    @Inject(method = "set(Lnet/minecraft/item/ItemStack;I)V", at = @At("TAIL"), require = 0)
    private void universalHud$storeStackCooldown(ItemStack stack, int ticks, CallbackInfo ci) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        Identifier group = ((ItemCooldownManager) (Object) this).getGroup(stack);
        CooldownStateStorage.setDuration(group, ticks);
    }

    @Inject(method = "set(Lnet/minecraft/util/Identifier;I)V", at = @At("TAIL"), require = 0)
    private void universalHud$storeGroupCooldown(Identifier group, int ticks, CallbackInfo ci) {
        CooldownStateStorage.setDuration(group, ticks);
    }

    @Inject(method = "remove", at = @At("TAIL"), require = 0)
    private void universalHud$removeCooldown(Identifier group, CallbackInfo ci) {
        CooldownStateStorage.remove(group);
    }
}
