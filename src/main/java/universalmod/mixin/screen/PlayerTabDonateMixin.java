package universalmod.mixin.screen;

import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.module.utility.CustomDonate;

@Mixin(PlayerListHud.class)
public abstract class PlayerTabDonateMixin {
    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void universalmod$name(PlayerListEntry e, CallbackInfoReturnable<Text> cir) {
        Text r = CustomDonate.replaceDonateTab(e, cir.getReturnValue());
        if (r != null) cir.setReturnValue(r);
    }
}
