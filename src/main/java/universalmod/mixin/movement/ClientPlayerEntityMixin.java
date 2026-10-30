package universalmod.mixin.movement;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.module.utility.LockSlot;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    @Inject(method = "dropSelectedItem", at = @At("HEAD"), cancellable = true)
    private void universalmod$lockCurrentSlot(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        if (LockSlot.shouldCancelCurrentSlotDrop()) cir.setReturnValue(false);
    }
}
