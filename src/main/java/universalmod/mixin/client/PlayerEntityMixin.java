package universalmod.mixin.client;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.module.utility.CustomDonate;
import universalmod.module.utility.TotemCounter;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void universalmod$replaceDisplayName(CallbackInfoReturnable<Text> cir) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        Text donate = CustomDonate.replaceDonate(self, cir.getReturnValue());
        cir.setReturnValue(TotemCounter.appendCounter(self, donate));
    }

}
