package universalmod.mixin.network;

import java.util.List;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.module.misc.serverhelper.ServerItemMetadataService;

@Mixin(ItemStack.class)
public abstract class ItemStackServerHelperMixin {
    @Inject(method = "getTooltip", at = @At("RETURN"), require = 0)
    private void universalmod$appendServerHelperTooltip(PlayerEntity player, TooltipContext context, CallbackInfoReturnable<List<Text>> cir) {
        ServerItemMetadataService.appendTooltip((ItemStack) (Object) this, cir.getReturnValue());
    }

    @Inject(method = "getName", at = @At("RETURN"), cancellable = true, require = 0)
    private void universalmod$appendCompassCooldownToName(CallbackInfoReturnable<Text> cir) {
        cir.setReturnValue(ServerItemMetadataService.appendCompassCooldownToName((ItemStack) (Object) this, cir.getReturnValue()));
    }
}
