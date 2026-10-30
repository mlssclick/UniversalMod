package universalmod.mixin.client;

import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.module.utility.CustomDonate;

@Mixin(PlayerListEntry.class)
public abstract class PlayerListEntryMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, require = 0)
    private void universalmod$replaceDonateInTab(CallbackInfoReturnable<Text> cir) {
        PlayerListEntry self = (PlayerListEntry) (Object) this;
        Text current = cir.getReturnValue();
        Text replaced = CustomDonate.replaceDonateTab(self, current);
        if (replaced != current) {
            cir.setReturnValue(replaced);
        }
    }
}
