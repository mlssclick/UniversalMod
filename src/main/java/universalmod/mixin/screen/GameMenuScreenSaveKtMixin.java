package universalmod.mixin.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.mixin.accessor.ButtonWidgetAccessor;
import universalmod.module.utility.SaveKtLeave;
import universalmod.feature.savekt.SaveKtManager;

@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenSaveKtMixin {
    @Shadow
    private ButtonWidget exitButton;

    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void universalmod$replaceExitAction(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        SaveKtLeave module = SaveKtLeave.getInstance();
        if (client.isInSingleplayer() || exitButton == null || module == null || !module.isEnabled()) {
            return;
        }

        ((ButtonWidgetAccessor) (Object) exitButton).universalmod$setOnPress(
                button -> SaveKtManager.sendConfirmScreenIfNeeded(null)
        );
    }
}
