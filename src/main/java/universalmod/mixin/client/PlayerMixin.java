package universalmod.mixin.client;

import net.minecraft.text.Text;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.module.utility.CustomDonate;
import universalmod.module.utility.TotemCounter;
import universalmod.module.render.ping.PingNametagHelper;

@Mixin(PlayerEntity.class)
public abstract class PlayerMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void universalmod$appendTotemCounter(CallbackInfoReturnable<Text> cir) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        Text customDonate = CustomDonate.replaceDonate(self, cir.getReturnValue());
        Text modified = TotemCounter.appendCounter(self, customDonate);
        cir.setReturnValue(PingNametagHelper.appendPing(self, modified));
    }
}
