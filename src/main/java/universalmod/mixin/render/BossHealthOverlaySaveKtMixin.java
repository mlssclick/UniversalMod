package universalmod.mixin.render;

import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.feature.savekt.SaveKtLeaveService;
import universalmod.feature.savekt.SaveKtManager;

import java.util.Locale;
import java.util.UUID;


@Mixin(BossHealthOverlay.class)
public class BossHealthOverlaySaveKtMixin {
    @Inject(method = "update", at = @At("HEAD"))
    private void universalmod$detectBossbar(ClientboundBossEventPacket packet, CallbackInfo ci) {
        packet.dispatch(new ClientboundBossEventPacket.Handler() {
            @Override
            public void add(UUID uuid, Component name, float percent, BossEvent.BossBarColor color,
                            BossEvent.BossBarOverlay style, boolean darkenSky, boolean dragonMusic,
                            boolean thickenFog) {
                String lowered = name.getString().toLowerCase(Locale.ROOT);
                if (SaveKtManager.PVP_TYPES.stream().anyMatch(lowered::contains)) {
                    SaveKtLeaveService.getInstance().onPvpBossbarSeen(uuid);
                }
            }

            @Override
            public void remove(UUID uuid) {
                SaveKtLeaveService.getInstance().onPvpBossbarRemoved(uuid);
            }

            @Override
            public void updateProgress(UUID uuid, float percent) {
            }

            @Override
            public void updateName(UUID uuid, Component name) {
            }

            @Override
            public void updateStyle(UUID uuid, BossEvent.BossBarColor color, BossEvent.BossBarOverlay style) {
            }

            @Override
            public void updateProperties(UUID uuid, boolean darkenSky, boolean dragonMusic, boolean thickenFog) {
            }
        });
    }

    @Inject(method = "reset", at = @At("RETURN"))
    private void universalmod$clear(CallbackInfo ci) {
        SaveKtLeaveService.getInstance().onBossbarReset();
    }
}
