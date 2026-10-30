package universalmod.mixin.client;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.module.utility.CustomDonate;
import universalmod.module.utility.TotemCounter;
import universalmod.module.render.ping.PingNametagHelper;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityDisplayNameMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void universalmod$decoratePlayerName(CallbackInfoReturnable<Text> cir) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        Text current = cir.getReturnValue();
        Text donated = CustomDonate.replaceDonate(self, current);
        Text counter = TotemCounter.appendCounter(self, donated);
        cir.setReturnValue(PingNametagHelper.appendPing(self, counter));
    }
}
