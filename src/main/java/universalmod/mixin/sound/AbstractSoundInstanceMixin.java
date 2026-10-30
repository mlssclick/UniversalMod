package universalmod.mixin.sound;

import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.module.utility.SoundReducer;

@Mixin(AbstractSoundInstance.class)
public abstract class AbstractSoundInstanceMixin {
    @Inject(method = "getVolume", at = @At("RETURN"), cancellable = true)
    private void universalmod$soundReducer(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(SoundReducer.adjustVolume((SoundInstance) (Object) this, cir.getReturnValueF()));
    }
}
