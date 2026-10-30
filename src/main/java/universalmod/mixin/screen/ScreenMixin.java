package universalmod.mixin.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.command.CommandManager;




@Mixin(Screen.class)
public class ScreenMixin {
    @Inject(method = "handleTextClick", at = @At("HEAD"), cancellable = true, require = 1)
    private void universalmod$handleClientClickEvent(Style style, CallbackInfoReturnable<Boolean> cir) {
        if (style == null) return;
        ClickEvent clickEvent = style.getClickEvent();
        if (clickEvent == null) return;

        if (clickEvent.getAction() == ClickEvent.Action.COPY_TO_CLIPBOARD) {
            String value = clickEvent.getValue();
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getWindow() != null && value != null && !value.isEmpty()) {
                GLFW.glfwSetClipboardString(client.getWindow().getHandle(), value);
                CommandManager manager = CommandManager.getInstance();
                if (manager != null) manager.sendMessage("Copied to clipboard.");
                cir.setReturnValue(true);
            }
            return;
        }

        if (clickEvent.getAction() == ClickEvent.Action.RUN_COMMAND) {
            String command = clickEvent.getValue();
            CommandManager manager = CommandManager.getInstance();
            if (manager != null && command != null && command.startsWith(manager.getPrefix())) {
                manager.execute(command.substring(manager.getPrefix().length()));
                cir.setReturnValue(true);
            }
        }
    }
}
