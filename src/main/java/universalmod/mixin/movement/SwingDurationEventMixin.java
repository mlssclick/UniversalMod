package universalmod.mixin.movement;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.event.type.SwingDurationEvent;
import universalmod.lifecycle.ClientRuntime;

@Mixin(LivingEntity.class)
public abstract class SwingDurationEventMixin {
    @Inject(method = "getHandSwingDuration", at = @At("HEAD"), cancellable = true)
    private void universalmod$duration(CallbackInfoReturnable<Integer> cir) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || (Object) this != player) return;
        SwingDurationEvent event = ClientRuntime.postEvent(new SwingDurationEvent());
        if (!event.isCancelled()) return;
        float animation = event.getAnimation();
        if (StatusEffectUtil.hasHaste(player)) animation *= 6 - (1 + StatusEffectUtil.getHasteAmplifier(player));
        else if (player.hasStatusEffect(StatusEffects.MINING_FATIGUE))
            animation *= 6 + (1 + player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier()) * 2;
        else animation *= 6;
        cir.setReturnValue(Math.max(1, (int) animation));
    }
}
