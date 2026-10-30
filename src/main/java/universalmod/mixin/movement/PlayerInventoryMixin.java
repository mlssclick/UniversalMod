package universalmod.mixin.movement;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.module.utility.LockSlot;

@Mixin(PlayerInventory.class)
public abstract class PlayerInventoryMixin {
    @Inject(method = "dropSelectedItem(Z)Lnet/minecraft/item/ItemStack;", at = @At("HEAD"), cancellable = true)
    private void universalmod$lockSelectedSlot(boolean entireStack, CallbackInfoReturnable<ItemStack> cir) {
        if (LockSlot.shouldCancelCurrentSlotDrop()) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
