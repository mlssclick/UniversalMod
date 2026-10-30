package universalmod.mixin.render;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.access.FireGlowEntityStateCarrier;

@Mixin(EntityRenderer.class)
public abstract class FireGlowEntityRendererMixin {
    @Inject(method="updateRenderState",at=@At("RETURN"))
    private void universalmod$captureOutlineColor(Entity entity,EntityRenderState state,float tickDelta,CallbackInfo ci) {
        ((FireGlowEntityStateCarrier)state).universalmod$setFireOutlineColor(entity.isGlowing()?0xFF000000|entity.getTeamColorValue():0);
    }
}
