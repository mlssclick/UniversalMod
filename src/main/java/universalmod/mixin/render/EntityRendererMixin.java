package universalmod.mixin.render;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.InvisibleTags;
import universalmod.module.render.ping.PingNametagHelper;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/entity/state/EntityRenderState;F)V", at = @At("TAIL"), require = 0)
    private void universalmod$updatePlayerNameTag(T entity, S state, float tickDelta, CallbackInfo ci) {
        if (entity instanceof PlayerEntity player) {
            InvisibleTags.applyVanillaInvisibleTag(player, state, tickDelta);
            state.displayName = PingNametagHelper.appendPing(player, state.displayName);
        }
    }
}
