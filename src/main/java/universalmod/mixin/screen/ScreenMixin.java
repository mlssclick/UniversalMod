package universalmod.mixin.screen;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.command.CommandManager;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "handleTextClick", at = @At("HEAD"), cancellable = true)
    private void universalmod$clientCommand(Style style, CallbackInfoReturnable<Boolean> cir) {
        if (style == null || style.getClickEvent() == null) return;
        ClickEvent e = style.getClickEvent();
        if (e.getAction() != ClickEvent.Action.RUN_COMMAND) return;
        String value = e.getValue();
        CommandManager cm = CommandManager.getInstance();
        if (cm != null && value != null && value.startsWith(cm.getPrefix())) {
            cm.execute(value.substring(cm.getPrefix().length()));
            cir.setReturnValue(true);
        }
    }
}
