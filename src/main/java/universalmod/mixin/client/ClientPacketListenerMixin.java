package universalmod.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.ProfilelessChatMessageS2CPacket;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.event.type.ChatEvent;
import universalmod.feature.savekt.SaveKtLeaveService;
import universalmod.feature.savekt.SaveKtManager;
import universalmod.module.misc.ServerHelper;
import universalmod.integration.minecraft.ClientPlayNetworkHandlerHelper;
import universalmod.lifecycle.Manager;
import universalmod.module.render.Waypoints;
import universalmod.module.utility.SaveKtLeave;
import universalmod.module.utility.TotemCounter;
import universalmod.service.AnarchySwitcher;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPacketListenerMixin implements ClientPlayNetworkHandlerHelper {
    @Unique private boolean universalmod$sendingFinalCommand;

    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$onSendChat(String message, CallbackInfo ci) {
        ChatEvent event = Manager.postEvent(new ChatEvent(message));
        if (event.isCancelled()) ci.cancel();
    }

    @Inject(method = "sendChatCommand", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$onSendChatCommand(String command, CallbackInfo ci) {
        SaveKtLeave module = SaveKtLeave.getInstance();
        if (!SaveKtManager.bypassCommandHook && !universalmod$sendingFinalCommand
                && module != null && module.isEnabled()
                && SaveKtManager.COMMANDS_FOR_LEAVE.contains(command)) {
            ci.cancel();
            SaveKtLeaveService.getInstance().requestLeaveCommand(command);
        }
    }

    @Inject(method = "onPlaySound", at = @At("HEAD"), require = 0)
    private void universalmod$onSoundEvent(PlaySoundS2CPacket packet, CallbackInfo ci) {
        ServerHelper.Detection.handleSoundPacket(packet);
    }

    @Inject(method = "onHealthUpdate", at = @At("RETURN"), require = 0)
    private void universalmod$onHealthUpdate(HealthUpdateS2CPacket packet, CallbackInfo ci) {
        if (packet.getHealth() <= 0.0F) {
            SaveKtManager.resetAll();
            Waypoints.handleDeath();
        }
    }

    @Inject(method = "onGameMessage", at = @At("TAIL"), require = 0)
    private void universalmod$onGameMessage(GameMessageS2CPacket packet, CallbackInfo ci) {
        if (packet != null && packet.content() != null && !packet.overlay()) {
            universalmod$handleIncoming(packet.content());
        }
    }

    @Inject(method = "onProfilelessChatMessage", at = @At("TAIL"), require = 0)
    private void universalmod$onProfilelessChat(ProfilelessChatMessageS2CPacket packet, CallbackInfo ci) {
        if (packet != null) universalmod$handleIncoming(packet.message());
    }

    @Inject(method = "onChatMessage", at = @At("TAIL"), require = 0)
    private void universalmod$onPlayerChat(ChatMessageS2CPacket packet, CallbackInfo ci) {
        if (packet != null) universalmod$handleIncoming(packet.unsignedContent());
    }

    @Unique
    private static void universalmod$handleIncoming(Text text) {
        if (text == null) return;
        String message = text.getString();
        Waypoints.handleServerMessage(message);
        AnarchySwitcher.getInstance().handleServerMessage(message);
    }

    @Inject(method = "onEntityStatus", at = @At("HEAD"), require = 0)
    private void universalmod$onEntityEvent(EntityStatusS2CPacket packet, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || packet.getStatus() != EntityStatuses.USE_TOTEM_OF_UNDYING) return;
        Entity entity = packet.getEntity(client.world);
        if (entity instanceof OtherClientPlayerEntity player) TotemCounter.recordPop(player);
    }

    @Override
    public void sendFinalCommand(String command) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            universalmod$sendingFinalCommand = true;
            boolean oldBypass = SaveKtManager.bypassCommandHook;
            SaveKtManager.bypassCommandHook = true;
            try {
                if (client.getNetworkHandler() != null) {
                    client.getNetworkHandler().sendChatCommand(command);
                }
            } finally {
                SaveKtManager.bypassCommandHook = oldBypass;
                universalmod$sendingFinalCommand = false;
            }
        });
    }
}
