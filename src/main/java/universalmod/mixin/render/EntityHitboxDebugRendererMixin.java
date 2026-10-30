package universalmod.mixin.render;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.CustomHitBox;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityHitboxDebugRendererMixin {
    @Shadow
    private boolean renderHitboxes;
    @Unique
    private boolean universalmod$previousHitboxState;

    @Inject(method = "render", at = @At("HEAD"))
    private <E extends Entity> void universalmod$beforeRender(E entity, double x, double y, double z, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        universalmod$previousHitboxState = renderHitboxes;
        if (CustomHitBox.shouldReplaceVanilla(net.minecraft.client.MinecraftClient.getInstance()))
            renderHitboxes = false;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private <E extends Entity> void universalmod$afterRender(E entity, double x, double y, double z, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        renderHitboxes = universalmod$previousHitboxState;
    }
}
