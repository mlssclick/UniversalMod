package universalmod.mixin.network;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.Waypoints;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPacketListenerEventMixin {
    @Inject(method = "onGameMessage", at = @At("HEAD"))
    private void universalmod$message(GameMessageS2CPacket packet, CallbackInfo ci) {
        if (packet != null) Waypoints.handleServerMessage(packet.content().getString());
    }
}
