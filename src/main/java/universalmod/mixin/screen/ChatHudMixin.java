package universalmod.mixin.screen;

import java.util.List;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.misc.BetterChat;
import universalmod.module.render.Animations;
import universalmod.module.misc.BetterChat.ProcessedChatMessage;
import universalmod.module.render.Waypoints;
import universalmod.module.utility.CustomDonate;
import universalmod.module.utility.TotemCounter;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    @Unique
    private static final List<String> UNIVERSALMOD_ROUND_END_MESSAGES = List.of("Winners:", "has won the round.", "has won the game!", "Winner: NONE!", "Match Complete");

    @Shadow
    public abstract void addMessage(Text message, MessageSignatureData signatureData, MessageIndicator indicator);

    @Shadow
    @Final
    private List<ChatHudLine> messages;

    @Shadow
    private void refresh() {
    }

    @Unique
    private boolean universalmod$chatBypass;
    @Unique
    private boolean universalmod$smoothChatPushed;

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At("HEAD"), cancellable = true)
    private void universalmod$processBetterChat(Text message, MessageSignatureData signatureData, MessageIndicator indicator, CallbackInfo ci) {
        if (universalmod$chatBypass) return;
        String raw = message == null ? "" : message.getString();
        Waypoints.handleServerMessage(raw);
        if (Waypoints.shouldHideChatMessage(raw)) {
            ci.cancel();
            return;
        }
        Text replaced = CustomDonate.replaceChatMessage(message);
        ProcessedChatMessage processed = BetterChat.prepareIncomingMessage(replaced);
        if (processed == null || processed.text() == null) return;
        boolean changedByDonate = replaced != message;
        boolean shouldReAdd = changedByDonate || processed.moduleActive() || processed.replacePrevious();
        if (!shouldReAdd) return;
        if (processed.replacePrevious()) universalmod$removePreviousMessage();
        universalmod$chatBypass = true;
        try {
            addMessage(processed.text(), signatureData, indicator);
        } finally {
            universalmod$chatBypass = false;
        }
        ci.cancel();
    }

    @Unique
    private void universalmod$removePreviousMessage() {
        if (messages.isEmpty()) return;
        messages.remove(0);
        refresh();
    }

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At("HEAD"))
    private void universalmod$clearPopsOnRoundEnd(Text message, MessageSignatureData signatureData, MessageIndicator indicator, CallbackInfo ci) {
        String text = message == null ? "" : message.getString();
        for (String marker : UNIVERSALMOD_ROUND_END_MESSAGES) {
            if (text.contains(marker)) {
                TotemCounter.clearAll();
                return;
            }
        }
    }

    @Inject(method = "clear", at = @At("HEAD"), cancellable = true)
    private void universalmod$preventChatClear(boolean clearHistory, CallbackInfo ci) {
        if (BetterChat.shouldCancelChatClear()) ci.cancel();
    }

    @Inject(method = "clear", at = @At("TAIL"))
    private void universalmod$resetBetterChat(boolean clearHistory, CallbackInfo ci) {
        BetterChat.resetSpamState();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void universalmod$beginSmoothChat(DrawContext context, int currentTick, int mouseX, int mouseY, CallbackInfo ci) {
        if (Animations.active(Animations.MESSAGES)) {
            universalmod$smoothChatPushed = false;
            return;
        }
        float offset = BetterChat.getSmoothOffset();
        universalmod$smoothChatPushed = offset > 0.01F;
        if (!universalmod$smoothChatPushed) return;
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, offset, 0.0F);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void universalmod$endSmoothChat(DrawContext context, int currentTick, int mouseX, int mouseY, CallbackInfo ci) {
        if (!universalmod$smoothChatPushed) return;
        context.getMatrices().pop();
        universalmod$smoothChatPushed = false;
    }
}
