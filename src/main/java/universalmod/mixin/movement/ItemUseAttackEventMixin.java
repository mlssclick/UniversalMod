package universalmod.mixin.movement;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.event.type.AttackEntityEvent;
import universalmod.lifecycle.ClientRuntime;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ItemUseAttackEventMixin {
    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void universalmod$attack(PlayerEntity p, Entity e, CallbackInfo ci) {
        ClientRuntime.postEvent(new AttackEntityEvent(e));
    }
}
