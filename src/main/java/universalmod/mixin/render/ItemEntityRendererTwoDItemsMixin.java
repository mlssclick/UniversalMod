package universalmod.mixin.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.mixin.accessor.EntityRendererAccessor;
import universalmod.module.render.TwoDItems;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererTwoDItemsMixin {
    @Shadow
    @Final
    private Random random;

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$render2DItems(ItemEntityRenderState state, MatrixStack matrices, VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        if (!TwoDItems.isFeatureEnabled() || state.itemRenderState.isEmpty()) return;
        if (!TwoDItems.shouldAffect3DModels() && state.itemRenderState.hasDepth()) return;
        ((EntityRendererAccessor) (Object) this).universalmod$setShadowRadius(TwoDItems.shouldCastShadows() ? 0.15F : 0.0F);
        matrices.push();
        try {
            float yScale = Math.abs(state.itemRenderState.getTransformation().scale.y());
            if (!Float.isFinite(yScale) || yScale <= 0.0F) yScale = 1.0F;
            float lift = 0.0625F + 0.25F * yScale;
            float bounce = MathHelper.sin(state.age / 10.0F + state.uniqueOffset) * 0.1F + 0.1F;
            matrices.translate(0.0F, bounce + lift, 0.0F);
            matrices.multiply(MinecraftClient.getInstance().gameRenderer.getCamera().getRotation());
            ItemEntityRenderer.renderStack(matrices, consumers, light, state, random);
        } finally {
            matrices.pop();
        }
        ci.cancel();
    }
}
