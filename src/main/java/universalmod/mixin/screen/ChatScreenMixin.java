package universalmod.mixin.screen;

import net.minecraft.text.Text;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.module.render.Hud;
import universalmod.feature.savekt.SaveKtManager;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
    @Inject(
            method = "keyPressed",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/ChatScreen;sendMessage(Ljava/lang/String;Z)V",
                    shift = At.Shift.AFTER
            ),
            cancellable = true,
            require = 0
    )
    private void universalUtils$saveKtKeyPressed(
            int keyCode,
            int scanCode,
            int modifiers,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (SaveKtManager.openScreen) {
            cir.setReturnValue(true);
        }
    }

    protected ChatScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void universalHud$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        Hud hud = Hud.getInstance();
        if (hud != null && hud.handleMouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
            return;
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void universalHud$renderEditor(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Hud hud = Hud.getInstance();
        if (hud != null) {
            hud.renderEditorLayer(context, mouseX, mouseY);
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void universalHud$removed(CallbackInfo ci) {
        Hud hud = Hud.getInstance();
        if (hud != null) {
            hud.closeEditorState();
        }
    }


    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        Hud hud = Hud.getInstance();
        if (hud != null && hud.handleMouseDragged(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        Hud hud = Hud.getInstance();
        if (hud != null && hud.handleMouseScrolled(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void close() {
        Hud hud = Hud.getInstance();
        if (hud != null) {
            hud.closeEditorState();
        }
        super.close();
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        Hud hud = Hud.getInstance();
        boolean handled = hud != null && hud.handleMouseReleased(mouseX, mouseY, button);
        return handled || super.mouseReleased(mouseX, mouseY, button);
    }
}
