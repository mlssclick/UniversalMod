package universalmod.mixin.client;

import java.util.List;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.module.misc.ServerHelper.ItemInfo;

@Mixin(ItemStack.class)
public abstract class ItemStackServerHelperMixin {
    @Inject(method = "getTooltip", at = @At("RETURN"), require = 0)
    private void universalmod$appendServerHelperTooltip(
            Item.TooltipContext context,
            PlayerEntity player,
            TooltipType type,
            CallbackInfoReturnable<List<Text>> cir
    ) {
        ItemInfo.appendTooltip((ItemStack) (Object) this, cir.getReturnValue());
    }

    @Inject(method = "getName", at = @At("RETURN"), cancellable = true, require = 0)
    private void universalmod$appendCompassCooldownToName(CallbackInfoReturnable<Text> cir) {
        cir.setReturnValue(ItemInfo.appendCompassCooldownToName(
                (ItemStack) (Object) this,
                cir.getReturnValue()
        ));
    }
}
