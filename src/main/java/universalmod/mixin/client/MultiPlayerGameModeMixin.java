package universalmod.mixin.movement;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.event.type.AttackEntityEvent;
import universalmod.event.type.ClickSlotEvent;
import universalmod.lifecycle.Manager;
import universalmod.feature.cooldown.HolyWorldHealingCooldown;

@Mixin(ClientPlayerInteractionManager.class)
public class MultiPlayerGameModeMixin {
    @Unique
    private ItemStack universalmod$healingUse = ItemStack.EMPTY;

    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void universalmod$attackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
        Manager.postEvent(new AttackEntityEvent(target));
    }


    @Inject(method = "interactItem", at = @At("HEAD"), require = 0)
    private void universalmod$captureItemUse(
            PlayerEntity player,
            Hand hand,
            CallbackInfoReturnable<ActionResult> cir
    ) {
        ItemStack stack = player == null || hand == null
                ? ItemStack.EMPTY
                : player.getStackInHand(hand);



        universalmod$healingUse = HolyWorldHealingCooldown.isHealingPotion(stack)
                ? stack.copy()
                : ItemStack.EMPTY;
    }

    @Inject(method = "interactItem", at = @At("RETURN"), require = 0)
    private void universalmod$recordThrownHealing(
            PlayerEntity player,
            Hand hand,
            CallbackInfoReturnable<ActionResult> cir
    ) {
        ItemStack captured = universalmod$healingUse;
        universalmod$healingUse = ItemStack.EMPTY;

        ActionResult result = cir.getReturnValue();
        if (!captured.isEmpty() && result instanceof ActionResult.Success) {
            HolyWorldHealingCooldown.recordThrown(player, captured);
        }
    }

    @Inject(method = "clickSlot", at = @At("HEAD"), cancellable = true)
    private void universalmod$lockSlot(
            int syncId,
            int slotId,
            int button,
            SlotActionType actionType,
            PlayerEntity player,
            CallbackInfo ci
    ) {
        ClickSlotEvent event = Manager.postEvent(new ClickSlotEvent(syncId, slotId, button, actionType));
        if (event.isCancelled()) {
            ci.cancel();
        }
    }
}
