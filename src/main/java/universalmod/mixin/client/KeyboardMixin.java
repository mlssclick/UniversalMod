package universalmod.mixin.client;

import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.event.type.KeyEvent;
import universalmod.lifecycle.Manager;

@Mixin(Keyboard.class)
public class KeyboardMixin {
    @Inject(method = "onKey", at = @At("HEAD"))
    private void universalmod$onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getWindow() == null || window != client.getWindow().getHandle() || key == GLFW.GLFW_KEY_UNKNOWN || action == GLFW.GLFW_REPEAT) {
            return;
        }
        Manager.postEvent(new KeyEvent(client.currentScreen, InputUtil.Type.KEYSYM, key, action));
    }
}
