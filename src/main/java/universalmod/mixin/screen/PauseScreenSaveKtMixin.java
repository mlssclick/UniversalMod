package universalmod.mixin.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.utility.SaveKtLeave;
import universalmod.mixin.accessor.ButtonAccessor;
import universalmod.feature.savekt.SaveKtManager;

@Mixin(PauseScreen.class)
public abstract class PauseScreenSaveKtMixin {
    @Shadow
    private Button disconnectButton;

    @Inject(method = "init", at = @At("TAIL"))
    private void universalmod$onInit(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.isLocalServer()) {
            return;
        }

        SaveKtLeave module = SaveKtLeave.getInstance();
        if (module != null && module.isEnabled()) {
            ((ButtonAccessor) (Object) disconnectButton).universalmod$setOnPress(button -> {
                SaveKtManager.sendConfirmScreenIfNeeded(null);
            });
        }
    }
}
