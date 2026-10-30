package universalmod.mixin.screen;

import java.util.List;

import net.minecraft.client.MinecraftClient;
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
import universalmod.module.misc.BetterChat.ProcessedChatMessage;
import universalmod.module.render.Animations;
import universalmod.module.render.Waypoints;
import universalmod.module.utility.CustomDonate;
import universalmod.module.utility.TotemCounter;
import universalmod.service.AnarchySwitcher;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    @Shadow
    public abstract void addMessage(Text message, MessageSignatureData signatureData, MessageIndicator indicator);

    @Shadow
    @Final
    private List<ChatHudLine> messages;

    @Shadow
    @Final
    private List<ChatHudLine.Visible> visibleMessages;

    @Shadow
    private void refresh() {
    }

    @Unique
    private static final List<String> UNIVERSALMOD_ROUND_END_MESSAGES = List.of(
            "Winners:",
            "has won the round.",
            "has won the game!",
            "Winner: NONE!",
            "Match Complete"
    );

    @Unique
    private boolean universalmod$chatBypass;

    @Unique
    private boolean universalmod$smoothChatPushed;

    @Inject(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void universalmod$processBetterChatMessage(
            Text message,
            MessageSignatureData signatureData,
            MessageIndicator indicator,
            CallbackInfo ci
    ) {
        if (universalmod$chatBypass) {
            return;
        }
        String universalmod$waypointMessage = message == null ? "" : message.getString();
        AnarchySwitcher.getInstance().handleServerMessage(universalmod$waypointMessage);
        Waypoints.handleServerMessage(universalmod$waypointMessage);
        if (Waypoints.shouldHideChatMessage(universalmod$waypointMessage)) {
            ci.cancel();
            return;
        }

        Text replaced = CustomDonate.replaceChatMessage(message);
        ProcessedChatMessage processed = BetterChat.prepareIncomingMessage(replaced);
        if (processed == null || processed.text() == null) {
            return;
        }

        boolean changedByDonate = replaced != message;
        boolean shouldReAdd = changedByDonate || processed.moduleActive() || processed.replacePrevious();
        if (!shouldReAdd) {
            return;
        }

        if (processed.replacePrevious()) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                ci.cancel();
                client.execute(() -> {
                    universalmod$removePreviousMessage();
                    universalmod$chatBypass = true;
                    try {
                        addMessage(processed.text(), signatureData, indicator);
                    } finally {
                        universalmod$chatBypass = false;
                    }
                });
                return;
            }
            universalmod$removePreviousMessage();
        }

        universalmod$chatBypass = true;
        try {
            addMessage(processed.text(), signatureData, indicator);
        } finally {
            universalmod$chatBypass = false;
        }
        ci.cancel();
    }

    @Inject(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD")
    )
    private void universalmod$clearTotemCounterOnRoundEnd(
            Text message,
            MessageSignatureData signatureData,
            MessageIndicator indicator,
            CallbackInfo ci
    ) {
        String plain = message == null ? "" : message.getString();
        for (String marker : UNIVERSALMOD_ROUND_END_MESSAGES) {
            if (plain.contains(marker)) {
                TotemCounter.clearAll();
                return;
            }
        }
    }

    @Unique
    private void universalmod$removePreviousMessage() {
        if (messages.isEmpty()) {
            return;
        }

        ChatHudLine removed = messages.remove(0);
        int addedTime = removed.creationTick();
        visibleMessages.removeIf(line -> line.addedTime() == addedTime);
        refresh();
    }

    @Inject(method = "clear", at = @At("HEAD"), cancellable = true)
    private void universalmod$preventChatClear(boolean clearHistory, CallbackInfo ci) {
        if (BetterChat.shouldCancelChatClear()) {
            ci.cancel();
        }
    }

    @Inject(method = "clear", at = @At("TAIL"))
    private void universalmod$resetBetterChatState(boolean clearHistory, CallbackInfo ci) {
        BetterChat.resetSpamState();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void universalmod$beginSmoothChat(
            DrawContext context,
            int currentTick,
            int mouseX,
            int mouseY,
            boolean focused,
            CallbackInfo ci
    ) {
        if (Animations.active(Animations.MESSAGES)) {
            universalmod$smoothChatPushed = false;
            return;
        }

        float offset = BetterChat.getSmoothOffset();
        if (offset <= 0.01F) {
            universalmod$smoothChatPushed = false;
            return;
        }

        context.getMatrices().push();
        context.getMatrices().translate(0.0F, offset, 0.0F);
        universalmod$smoothChatPushed = true;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void universalmod$endSmoothChat(
            DrawContext context,
            int currentTick,
            int mouseX,
            int mouseY,
            boolean focused,
            CallbackInfo ci
    ) {
        if (!universalmod$smoothChatPushed) {
            return;
        }

        context.getMatrices().pop();
        universalmod$smoothChatPushed = false;
    }
}
