package universalmod.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.module.misc.Fullbright;

@Mixin(SimpleOption.class)
public abstract class OptionInstanceFullbrightMixin<T> {
    @Shadow private T value;

    @Inject(method = "getValue", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings("unchecked")
    private void universalmod$fullbrightGamma(CallbackInfoReturnable<T> cir) {
        Fullbright fullbright = Fullbright.getInstance();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (fullbright != null && fullbright.isGammaMode() && mc.options != null && mc.options.getGamma() == (Object)this) {
            cir.setReturnValue((T)(Object)Double.valueOf(1337.0));
        }
    }

    @Inject(method = "setValue", at = @At("HEAD"), cancellable = true)
    private void universalmod$preserveGamma(T newValue, CallbackInfo ci) {
        Fullbright fullbright = Fullbright.getInstance();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (fullbright != null && fullbright.isGammaMode() && mc.options != null && mc.options.getGamma() == (Object)this) {
            this.value = newValue;
            ci.cancel();
        }
    }
}
