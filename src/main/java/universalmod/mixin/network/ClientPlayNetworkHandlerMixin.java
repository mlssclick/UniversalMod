package universalmod.mixin.network;


import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.event.type.ChatEvent;
import universalmod.lifecycle.Manager;
import universalmod.module.utility.SaveKtLeave;
import universalmod.module.utility.TotemCounter;
import universalmod.module.render.Waypoints;
import universalmod.feature.savekt.SaveKtManager;
import universalmod.feature.savekt.SaveKtLeaveService;
import universalmod.module.misc.ServerHelper.Detection;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {
    @Inject(method = "sendChatMessage(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void universalmod$sendChatMessage(String message, CallbackInfo ci) {
        ChatEvent event = Manager.postEvent(new ChatEvent(message));
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "sendChatCommand(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void universalmod$sendChatCommand(String command, CallbackInfo ci) {
        if (!SaveKtManager.bypassCommandHook) {
            SaveKtLeave saveKt = SaveKtLeave.getInstance();
            if (saveKt != null
                    && saveKt.isEnabled()
                    && SaveKtManager.COMMANDS_FOR_LEAVE.contains(command)) {
                ci.cancel();
                SaveKtLeaveService.getInstance().requestLeaveCommand(command);
                return;
            }
        }

    }

    @Inject(method = "onPlaySound", at = @At("HEAD"), require = 0)
    private void universalmod$serverHelperSound(PlaySoundS2CPacket packet, CallbackInfo ci) {
        Detection.handleSoundPacket(packet);
    }

    @Inject(method = "onEntityStatus", at = @At("HEAD"), require = 1)
    private void universalmod$recordTotemPop(EntityStatusS2CPacket packet, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || packet.getStatus() != EntityStatuses.USE_TOTEM_OF_UNDYING) {
            return;
        }
        Entity entity = packet.getEntity(client.world);
        if (entity instanceof OtherClientPlayerEntity player) {
            TotemCounter.recordPop(player);
        }
    }

    @Inject(method = "onHealthUpdate", at = @At("RETURN"), require = 0)
    private void universalmod$resetSaveKtOnDeath(HealthUpdateS2CPacket packet, CallbackInfo ci) {
        if (packet.getHealth() <= 0.0F) {
            SaveKtManager.resetAll();
            Waypoints.handleDeath();
        }
    }

}
