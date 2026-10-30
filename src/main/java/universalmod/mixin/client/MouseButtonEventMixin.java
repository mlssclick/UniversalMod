package universalmod.mixin.client;

import net.minecraft.client.Mouse;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.event.type.KeyEvent;
import universalmod.setting.bind.InputType;
import universalmod.lifecycle.ClientRuntime;

@Mixin(Mouse.class)
public abstract class MouseButtonEventMixin {
    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void universalmod$button(long window, int button, int action, int mods, CallbackInfo ci) {
        ClientRuntime.postEvent(new KeyEvent(MinecraftClient.getInstance().currentScreen, InputType.MOUSE, button, action));
    }
}
