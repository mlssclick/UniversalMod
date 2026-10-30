package universalmod.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.event.type.ChatEvent;
import universalmod.module.render.Waypoints;
import universalmod.module.utility.SaveKtLeave;
import universalmod.module.utility.TotemCounter;
import universalmod.lifecycle.Manager;
import universalmod.service.AnarchySwitcher;
import universalmod.integration.minecraft.ClientPlayNetworkHandlerHelper;
import universalmod.feature.savekt.SaveKtManager;
import universalmod.feature.savekt.SaveKtLeaveService;
import universalmod.module.misc.ServerHelper;


@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin implements ClientPlayNetworkHandlerHelper {
    @Unique
    private boolean universalmod$sendingFinalCommand;

    @Shadow
    public abstract void sendCommand(String command);

    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void universalmod$onSendChat(String message, CallbackInfo ci) {
        ChatEvent event = Manager.postEvent(new ChatEvent(message));
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void universalmod$onSendChatCommand(String command, CallbackInfo ci) {
        SaveKtLeave module = SaveKtLeave.getInstance();
        if (module != null && module.isEnabled()
                && SaveKtManager.COMMANDS_FOR_LEAVE.contains(command)
                && !universalmod$sendingFinalCommand) {
            ci.cancel();
            SaveKtLeaveService.getInstance().requestLeaveCommand(command);
        }
    }

    @Inject(method = "handleSoundEvent", at = @At("HEAD"))
    private void universalmod$onSoundEvent(ClientboundSoundPacket packet, CallbackInfo ci) {
        ServerHelper.Detection.handleSoundPacket(packet);
    }

    @Inject(method = "handleSetHealth", at = @At("RETURN"))
    private void universalmod$onHealthUpdate(ClientboundSetHealthPacket packet, CallbackInfo ci) {
        if (packet.getHealth() <= 0.0F) {
            SaveKtManager.reset();
            Waypoints.handleDeath();
        }
    }

    @Inject(method = "handleSystemChat", at = @At("TAIL"))
    private void universalmod$onSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (packet != null && packet.content() != null) {
            String message = packet.content().getString();
            Waypoints.handleServerMessage(message);
            AnarchySwitcher.getInstance().handleServerMessage(message);
        }
    }

    @Inject(method = "handleDisguisedChat", at = @At("TAIL"))
    private void universalmod$onDisguisedChat(ClientboundDisguisedChatPacket packet, CallbackInfo ci) {
        if (packet != null && packet.message() != null) {
            String message = packet.message().getString();
            Waypoints.handleServerMessage(message);
            AnarchySwitcher.getInstance().handleServerMessage(message);
        }
    }

    @Inject(method = "handlePlayerChat", at = @At("TAIL"))
    private void universalmod$onPlayerChat(ClientboundPlayerChatPacket packet, CallbackInfo ci) {
        if (packet != null && packet.unsignedContent() != null) {
            String message = packet.unsignedContent().getString();
            Waypoints.handleServerMessage(message);
            AnarchySwitcher.getInstance().handleServerMessage(message);
        }
    }

    @Inject(method = "handleEntityEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleEngine;createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V"))
    private void universalmod$onEntityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }
        Entity entity = packet.getEntity(client.level);
        if (entity instanceof RemotePlayer player) {
            TotemCounter.recordPop(player);
        }
    }

    @Override
    public void sendFinalCommand(String command) {
        Minecraft.getInstance().execute(() -> {
            universalmod$sendingFinalCommand = true;
            try {
                sendCommand(command);
            } finally {
                universalmod$sendingFinalCommand = false;
            }
        });
    }
}
