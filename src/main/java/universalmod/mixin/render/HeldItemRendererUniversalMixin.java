package universalmod.mixin.render;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.util.Hand;
import net.minecraft.util.Arm;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.event.type.HandAnimationEvent;
import universalmod.event.type.HandOffsetEvent;
import universalmod.lifecycle.Manager;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererUniversalMixin {
    @Unique
    private MatrixStack universalmod$customSwingMatrices;
    @Unique
    private Hand universalmod$customSwingHand;
    @Unique
    private float universalmod$customSwingProgress;
    @Unique
    private float universalmod$currentItemScale = 1.0F;
    @Unique
    private AbstractClientPlayerEntity universalmod$currentPlayer;
    @Unique
    private Hand universalmod$currentRenderHand;
    @Unique
    private float universalmod$currentSwingProgress;
    @Unique
    private int universalmod$itemScaleDepth;
    @Unique
    private int universalmod$mapScaleDepth;

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void universalmod$beginFirstPersonItem(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                                   float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
                                                   VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        universalmod$clearCustomSwing();
        universalmod$currentPlayer = player;
        universalmod$currentRenderHand = hand;
        universalmod$currentSwingProgress = swingProgress;
        universalmod$currentItemScale = 1.0F;
        universalmod$itemScaleDepth = 0;
        universalmod$mapScaleDepth = 0;
    }

    @Inject(method = "renderFirstPersonItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;push()V", shift = At.Shift.AFTER, ordinal = 0))
    private void universalmod$handOffset(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                         float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
                                         VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        HandOffsetEvent event = Manager.postEvent(new HandOffsetEvent(matrices, item, hand));
        universalmod$currentItemScale = universalmod$sanitizeItemScale(event.getScale());

    }

    @WrapOperation(
            method = "renderFirstPersonItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applyEquipOffset(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/Arm;F)V"
            ),
            require = 1
    )
    private void universalmod$baseSwingAnimation(
            HeldItemRenderer instance,
            MatrixStack matrices,
            Arm arm,
            float equipProgress,
            Operation<Void> original
    ) {
        Hand hand = universalmod$currentRenderHand;
        AbstractClientPlayerEntity player = universalmod$currentPlayer;
        if (hand == null || player == null || (player.isUsingItem() && player.getActiveHand() == hand)) {
            original.call(instance, matrices, arm, equipProgress);
            return;
        }

        HandAnimationEvent event = Manager.postEvent(new HandAnimationEvent(matrices, hand, universalmod$currentSwingProgress));
        if (event.isCancelled()) {
            universalmod$markCustomSwing(matrices, hand, universalmod$currentSwingProgress);
            return;
        }

        original.call(instance, matrices, arm, equipProgress);
    }

    @WrapOperation(
            method = "renderFirstPersonItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;swingArm(FFLnet/minecraft/client/util/math/MatrixStack;ILnet/minecraft/util/Arm;)V"
            ),
            require = 1
    )
    private void universalmod$swingAnimation(
            HeldItemRenderer instance,
            float swingProgress,
            float equipProgress,
            MatrixStack matrices,
            int armX,
            Arm arm,
            Operation<Void> original
    ) {
        Hand hand = universalmod$currentRenderHand;
        AbstractClientPlayerEntity player = universalmod$currentPlayer;
        if (hand == null || player == null || (player.isUsingItem() && player.getActiveHand() == hand)) {
            original.call(instance, swingProgress, equipProgress, matrices, armX, arm);
            return;
        }

        if (universalmod$consumeCustomSwing(matrices, hand, universalmod$currentSwingProgress)) {
            return;
        }

        HandAnimationEvent event = Manager.postEvent(new HandAnimationEvent(matrices, hand, universalmod$currentSwingProgress));
        if (!event.isCancelled()) {
            original.call(instance, swingProgress, equipProgress, matrices, armX, arm);
        }
    }

    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("HEAD"), require = 0)
    private void universalmod$beforeRenderItem(LivingEntity entity, ItemStack stack, ModelTransformationMode renderMode, boolean leftHanded,
                                               MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (universalmod$pushItemScale(matrices, stack)) universalmod$itemScaleDepth++;
    }

    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("RETURN"), require = 0)
    private void universalmod$afterRenderItem(LivingEntity entity, ItemStack stack, ModelTransformationMode renderMode, boolean leftHanded,
                                              MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (universalmod$itemScaleDepth > 0) {
            universalmod$itemScaleDepth--;
            matrices.pop();
        }
    }

    @Inject(method = "renderFirstPersonMap", at = @At("HEAD"), require = 0)
    private void universalmod$beforeRenderMap(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, ItemStack stack, CallbackInfo ci) {
        if (universalmod$pushItemScale(matrices, stack)) universalmod$mapScaleDepth++;
    }

    @Inject(method = "renderFirstPersonMap", at = @At("RETURN"), require = 0)
    private void universalmod$afterRenderMap(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, ItemStack stack, CallbackInfo ci) {
        if (universalmod$mapScaleDepth > 0) {
            universalmod$mapScaleDepth--;
            matrices.pop();
        }
    }

    @Inject(method = "renderFirstPersonItem", at = @At("RETURN"), require = 0)
    private void universalmod$endFirstPersonItem(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                                 float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
                                                 VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        universalmod$clearCustomSwing();
        universalmod$currentItemScale = 1.0F;
        universalmod$currentPlayer = null;
        universalmod$currentRenderHand = null;
        universalmod$currentSwingProgress = 0.0F;
        universalmod$itemScaleDepth = 0;
        universalmod$mapScaleDepth = 0;
    }

    @Unique
    private boolean universalmod$pushItemScale(MatrixStack matrices, ItemStack stack) {
        float scale = universalmod$currentItemScale;
        if (stack == null || stack.isEmpty() || Float.compare(scale, 1.0F) == 0) return false;
        matrices.push();
        matrices.scale(scale, scale, scale);
        return true;
    }

    @Unique
    private void universalmod$markCustomSwing(MatrixStack matrices, Hand hand, float swingProgress) {
        universalmod$customSwingMatrices = matrices;
        universalmod$customSwingHand = hand;
        universalmod$customSwingProgress = swingProgress;
    }

    @Unique
    private boolean universalmod$consumeCustomSwing(MatrixStack matrices, Hand hand, float swingProgress) {
        boolean matches = universalmod$customSwingMatrices == matrices
                && universalmod$customSwingHand == hand
                && Float.compare(universalmod$customSwingProgress, swingProgress) == 0;
        if (matches) {
            universalmod$clearCustomSwing();
        }
        return matches;
    }

    @Unique
    private void universalmod$clearCustomSwing() {
        universalmod$customSwingMatrices = null;
        universalmod$customSwingHand = null;
        universalmod$customSwingProgress = 0.0F;
    }

    @Unique
    private static float universalmod$sanitizeItemScale(float scale) {
        return !Float.isFinite(scale) || scale <= 0.0F ? 1.0F : scale;
    }
}
