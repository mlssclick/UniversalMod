package universalmod.mixin.client;

import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.event.type.KeyEvent;
import universalmod.setting.bind.InputType;
import universalmod.lifecycle.ClientRuntime;

@Mixin(Keyboard.class)
public abstract class KeyboardHandlerMixin {
    @Inject(method = "onKey", at = @At("HEAD"), require = 1)
    private void universalmod$keyEvent(long window, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientRuntime.postEvent(new KeyEvent(client.currentScreen, InputType.KEYBOARD, key, action));
    }
}
