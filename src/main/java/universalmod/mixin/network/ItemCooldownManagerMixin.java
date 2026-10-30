package universalmod.mixin.network;

import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.feature.cooldown.CooldownStateStorage;

@Mixin(ItemCooldownManager.class)
public abstract class ItemCooldownManagerMixin {
    @Inject(method = "set", at = @At("TAIL"))
    private void universalHud$storeCooldown(Item item, int duration, CallbackInfo ci) {
        CooldownStateStorage.setDuration(item, duration);
    }

    @Inject(method = "remove", at = @At("TAIL"))
    private void universalHud$removeCooldown(Item item, CallbackInfo ci) {
        CooldownStateStorage.remove(item);
    }
}
