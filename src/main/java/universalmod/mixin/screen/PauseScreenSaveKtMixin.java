package universalmod.mixin.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.utility.SaveKtLeave;
import universalmod.feature.savekt.SaveKtManager;

@Mixin(GameMenuScreen.class)
public abstract class PauseScreenSaveKtMixin {
    @Inject(method = "disconnect", at = @At("HEAD"), cancellable = true)
    private void universalmod$leave(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        SaveKtLeave m = SaveKtLeave.getInstance();
        if (mc != null && !mc.isInSingleplayer() && m != null && m.isEnabled() && SaveKtManager.isPvp()) {
            SaveKtManager.sendConfirmScreenIfNeeded(null);
            ci.cancel();
        }
    }
}
