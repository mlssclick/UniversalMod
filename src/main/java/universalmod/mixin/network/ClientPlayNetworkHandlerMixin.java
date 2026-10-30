package universalmod.mixin.network;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.Waypoints;
import universalmod.module.utility.SaveKtLeave;
import universalmod.module.utility.TotemCounter;
import universalmod.feature.savekt.SaveKtLeaveService;
import universalmod.feature.savekt.SaveKtManager;
import universalmod.module.misc.serverhelper.TrapDetectionService;

import java.util.Locale;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin implements universalmod.integration.minecraft.ClientPlayNetworkHandlerHelper {

    @Shadow public abstract void sendChatCommand(String command);

    @Override
    public void sendFinalCommand(String command) {
        if (command == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        client.execute(() -> {
            boolean previous = SaveKtManager.bypassCommandHook;
            SaveKtManager.bypassCommandHook = true;
            try {
                sendChatCommand(command);
            } finally {
                SaveKtManager.bypassCommandHook = previous;
            }
        });
    }

    @Inject(method = "sendChatCommand", at = @At("HEAD"), cancellable = true)
    private void universalmod$protectLeaveCommand(String command, CallbackInfo ci) {
        SaveKtLeave module = SaveKtLeave.getInstance();
        if (SaveKtManager.bypassCommandHook || module == null || !module.isEnabled() || command == null) return;
        String normalized = command.trim().toLowerCase(Locale.ROOT);
        if (!SaveKtManager.COMMANDS_FOR_LEAVE.contains(normalized)) return;
        ci.cancel();
        SaveKtLeaveService.getInstance().requestLeaveCommand(command);
    }

    @Inject(method = "onPlaySound", at = @At("HEAD"), require = 0)
    private void universalmod$serverHelperSound(PlaySoundS2CPacket packet, CallbackInfo ci) {
        TrapDetectionService.handleSoundPacket(packet);
    }

    @Inject(method = "onHealthUpdate", at = @At("RETURN"))
    private void universalmod$death(HealthUpdateS2CPacket packet, CallbackInfo ci) {
        if (packet != null && packet.getHealth() <= 0.0F) {
            SaveKtManager.resetAll();
            Waypoints.handleDeath();
            TotemCounter.clearAll();
        }
    }

    @Inject(method = "onEntityStatus", at = @At("TAIL"))
    private void universalmod$totem(EntityStatusS2CPacket packet, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (packet == null || client.world == null || packet.getStatus() != 35) return;
        Entity entity = packet.getEntity(client.world);
        if (entity instanceof PlayerEntity player) TotemCounter.recordPop(player);
    }

}
