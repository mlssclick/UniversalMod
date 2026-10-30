package universalmod.mixin.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.module.render.Hud;
import universalmod.event.type.ChatEvent;
import universalmod.lifecycle.ClientRuntime;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
    protected ChatScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "sendMessage", at = @At("HEAD"), cancellable = true, require = 1)
    private void universalmod$commands(String chatText, boolean addToHistory, CallbackInfoReturnable<Boolean> cir) {
        ChatEvent event = ClientRuntime.postEvent(new ChatEvent(chatText));
        if (!event.isCancelled()) return;
        if (this.client != null && addToHistory) this.client.inGameHud.getChatHud().addToMessageHistory(chatText);
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void universalHud$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        Hud hud = Hud.getInstance();
        if (hud != null && hud.handleMouseClicked(mouseX, mouseY, button)) cir.setReturnValue(true);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void universalHud$renderEditor(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Hud hud = Hud.getInstance();
        if (hud != null) hud.renderEditorLayer(context, mouseX, mouseY);
    }

    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        Hud hud = Hud.getInstance();
        return (hud != null && hud.handleMouseDragged(mouseX, mouseY, button))
                || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        Hud hud = Hud.getInstance();
        return (hud != null && hud.handleMouseReleased(mouseX, mouseY, button))
                || super.mouseReleased(mouseX, mouseY, button);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void universalHud$mouseScrolled(double mouseX, double mouseY, double amount, CallbackInfoReturnable<Boolean> cir) {
        Hud hud = Hud.getInstance();
        if (hud != null && hud.handleMouseScrolled(mouseX, mouseY, amount)) cir.setReturnValue(true);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void universalHud$removed(CallbackInfo ci) {
        Hud hud = Hud.getInstance();
        if (hud != null) hud.closeEditorState();
    }
}
