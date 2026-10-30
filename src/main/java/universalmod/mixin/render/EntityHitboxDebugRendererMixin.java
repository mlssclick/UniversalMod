package universalmod.mixin.render;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.CustomHitBox;




@Mixin(EntityRenderDispatcher.class)
public abstract class EntityHitboxDebugRendererMixin {
    @Inject(method = "renderHitbox", at = @At("HEAD"), cancellable = true, require = 0)
    private static void universalmod$replaceVanillaHitbox(
            MatrixStack matrices,
            VertexConsumer vertices,
            Entity entity,
            float tickDelta,
            float red,
            float green,
            float blue,
            CallbackInfo ci
    ) {
        if (CustomHitBox.shouldReplaceVanilla()) {
            ci.cancel();
        }
    }
}
