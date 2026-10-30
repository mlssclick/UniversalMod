package universalmod.mixin.movement;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.feature.cooldown.HolyWorldHealingCooldown;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.entity.Entity;
import universalmod.event.type.ClickSlotEvent;
import universalmod.event.type.AttackEntityEvent;
import universalmod.lifecycle.ClientRuntime;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public final class ClientPlayerInteractionManagerMixin {
    @Inject(method = "clickSlot", at = @At("HEAD"), cancellable = true)
    private void universalmod$lockSlot(int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        ClickSlotEvent event = ClientRuntime.postEvent(new ClickSlotEvent(syncId, slotId, button, actionType));
        if (event.isCancelled()) ci.cancel();
    }

    @Inject(method = "attackEntity", at = @At("HEAD"), require = 1)
    private void universalmod$attackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
        ClientRuntime.postEvent(new AttackEntityEvent(target));
    }

    @Unique
    private ItemStack universalHud$healingUse = ItemStack.EMPTY;

    @Inject(method = "interactItem", at = @At("HEAD"), require = 0)
    private void universalHud$captureItemUse(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        ItemStack stack = player == null || hand == null ? ItemStack.EMPTY : player.getStackInHand(hand);
        universalHud$healingUse = HolyWorldHealingCooldown.isHealingPotion(stack) ? stack.copy() : ItemStack.EMPTY;
    }

    @Inject(method = "interactItem", at = @At("RETURN"), require = 0)
    private void universalHud$recordThrownHealing(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        ItemStack captured = universalHud$healingUse;
        universalHud$healingUse = ItemStack.EMPTY;
        ActionResult result = cir.getReturnValue();
        if (!captured.isEmpty() && result != null && result.isAccepted())
            HolyWorldHealingCooldown.recordThrown(player, captured);
    }
}
