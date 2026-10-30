package universalmod.mixin.client;

import net.minecraft.client.util.InputUtil;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.lifecycle.Manager;

@Mixin(Keyboard.class)
public abstract class KeyboardHandlerMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "onKey", at = @At("HEAD"))
    private void universalmod$toggleClickGui(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (key != GLFW.GLFW_KEY_UNKNOWN && window == this.client.getWindow().getHandle() && this.client.currentScreen == null) {
            Manager.postEvent(new universalmod.event.type.KeyEvent(this.client.currentScreen, InputUtil.Type.KEYSYM, key, action));
        }
    }
}
