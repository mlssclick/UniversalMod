package universalmod.mixin.render;

import java.util.Locale;
import java.util.UUID;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.network.packet.s2c.play.BossBarS2CPacket;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.Hud;
import universalmod.module.render.NoRender;
import universalmod.feature.savekt.SaveKtManager;
import universalmod.feature.savekt.SaveKtLeaveService;

@Mixin(BossBarHud.class)
public class BossHealthOverlaySaveKtMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void universalmod$replaceBossbar(DrawContext context, CallbackInfo ci) {
        if (NoRender.isActive("Bossbar") || Hud.shouldReplaceVanillaBossbar()) {
            ci.cancel();
        }
    }

    @Inject(method = "handlePacket", at = @At("HEAD"), require = 0)
    private void universalmod$detectPvpBossbar(BossBarS2CPacket packet, CallbackInfo ci) {
        packet.accept(new BossBarS2CPacket.Consumer() {
            @Override
            public void add(
                    UUID uuid,
                    Text name,
                    float percent,
                    BossBar.Color color,
                    BossBar.Style style,
                    boolean darkenSky,
                    boolean dragonMusic,
                    boolean thickenFog
            ) {
                String lowered = name.getString().toLowerCase(Locale.ROOT);
                if (SaveKtManager.PVP_TYPES.stream().anyMatch(lowered::contains)) {
                    SaveKtLeaveService.getInstance().onPvpBossbarSeen(uuid);
                }
            }

            @Override
            public void remove(UUID uuid) {
                SaveKtLeaveService.getInstance().onPvpBossbarRemoved(uuid);
            }
        });
    }

    @Inject(method = "clear", at = @At("RETURN"), require = 0)
    private void universalmod$clearPvpBossbar(CallbackInfo ci) {
        SaveKtLeaveService.getInstance().onBossbarReset();
    }

}
