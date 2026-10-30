package universalmod.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.utility.SaveKtLeave;
import universalmod.feature.savekt.SaveKtManager;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientDisconnectMixin {
    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;)V", at = @At("HEAD"), cancellable = true)
    private void universalmod$saveKtDisconnect(Screen screen, CallbackInfo ci) {
        SaveKtLeave module = SaveKtLeave.getInstance();
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (SaveKtManager.bypassDisconnectHook || client.isInSingleplayer() || module == null || !module.isEnabled() || !SaveKtManager.isPvp())
            return;
        SaveKtManager.sendConfirmScreenIfNeeded(null);
        ci.cancel();
    }
}
