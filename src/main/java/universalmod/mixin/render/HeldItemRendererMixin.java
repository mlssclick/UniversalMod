package universalmod.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.Hand;
import net.minecraft.util.Arm;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.event.type.HandAnimationEvent;
import universalmod.event.type.HandOffsetEvent;
import universalmod.lifecycle.Manager;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Unique
    private MatrixStack universalmod$customSwingMatrices;

    @Unique
    private Hand universalmod$customSwingHand;

    @Unique
    private float universalmod$customSwingProgress;

    @Unique
    private float universalmod$currentItemScale = 1.0F;

    @Unique
    private Hand universalmod$currentRenderHand;

    @Unique
    private int universalmod$itemScaleDepth;

    @Unique
    private int universalmod$mapScaleDepth;

    @Inject(method = "renderFirstPersonItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;push()V", shift = At.Shift.AFTER), require = 0)
    private void universalmod$handOffset(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack stack, float equipProgress, MatrixStack matrices, VertexConsumerProvider nodeCollector, int light, CallbackInfo ci) {
        this.universalmod$currentRenderHand = hand;
        HandOffsetEvent event = Manager.postEvent(new HandOffsetEvent(matrices, stack, hand));
        this.universalmod$currentItemScale = universalmod$sanitizeItemScale(event.getScale());
    }

    @Inject(method = "renderItem", at = @At("HEAD"), require = 0)
    private void universalmod$beforeRenderItem(
            LivingEntity entity,
            ItemStack stack,
            ModelTransformationMode displayContext,
            boolean leftHanded,
            MatrixStack matrices,
            VertexConsumerProvider nodeCollector,
            int light,
            CallbackInfo ci
    ) {
        if (universalmod$pushItemScale(matrices, stack)) {
            this.universalmod$itemScaleDepth++;
        }
    }

    @Inject(method = "renderItem", at = @At("RETURN"), require = 0)
    private void universalmod$afterRenderItem(
            LivingEntity entity,
            ItemStack stack,
            ModelTransformationMode displayContext,
            boolean leftHanded,
            MatrixStack matrices,
            VertexConsumerProvider nodeCollector,
            int light,
            CallbackInfo ci
    ) {
        if (this.universalmod$itemScaleDepth > 0) {
            this.universalmod$itemScaleDepth--;
            matrices.pop();
        }
    }

    @Inject(method = "renderFirstPersonMap", at = @At("HEAD"), require = 0)
    private void universalmod$beforeRenderMap(
            MatrixStack matrices,
            VertexConsumerProvider nodeCollector,
            int light,
            ItemStack stack,
            CallbackInfo ci
    ) {
        if (universalmod$pushItemScale(matrices, stack)) {
            this.universalmod$mapScaleDepth++;
        }
    }

    @Inject(method = "renderFirstPersonMap", at = @At("RETURN"), require = 0)
    private void universalmod$afterRenderMap(
            MatrixStack matrices,
            VertexConsumerProvider nodeCollector,
            int light,
            ItemStack stack,
            CallbackInfo ci
    ) {
        if (this.universalmod$mapScaleDepth > 0) {
            this.universalmod$mapScaleDepth--;
            matrices.pop();
        }
    }

    @WrapOperation(method = "renderFirstPersonItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applyEquipOffset(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/Arm;F)V"), require = 0)
    private void universalmod$baseSwingAnimation(HeldItemRenderer instance, MatrixStack matrices, Arm arm, float equipProgress, Operation<Void> original, @Local(argsOnly = true) AbstractClientPlayerEntity player, @Local(argsOnly = true) Hand hand, @Local(argsOnly = true, ordinal = 2) float swingProgress) {
        if (player.isUsingItem() && player.getActiveHand() == hand) {
            original.call(instance, matrices, arm, equipProgress);
            return;
        }

        HandAnimationEvent event = Manager.postEvent(new HandAnimationEvent(matrices, hand, swingProgress));
        if (event.isCancelled()) {
            universalmod$markCustomSwing(matrices, hand, swingProgress);
            return;
        }

        original.call(instance, matrices, arm, equipProgress);
    }

    @WrapOperation(method = "renderFirstPersonItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applySwingOffset(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/Arm;F)V"), require = 0)
    private void universalmod$swingAnimation(HeldItemRenderer instance, MatrixStack matrices, Arm arm, float swingProgress, Operation<Void> original, @Local(argsOnly = true) AbstractClientPlayerEntity player, @Local(argsOnly = true) Hand hand) {
        if (player.isUsingItem() && player.getActiveHand() == hand) {
            original.call(instance, matrices, arm, swingProgress);
            return;
        }

        if (universalmod$consumeCustomSwing(matrices, hand, swingProgress)) {
            return;
        }

        HandAnimationEvent event = Manager.postEvent(new HandAnimationEvent(matrices, hand, swingProgress));
        if (!event.isCancelled()) {
            original.call(instance, matrices, arm, swingProgress);
        }
    }

    @Inject(method = "renderFirstPersonItem", at = @At("RETURN"), require = 0)
    private void universalmod$clearSwingAnimation(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack stack, float equipProgress, MatrixStack matrices, VertexConsumerProvider nodeCollector, int light, CallbackInfo ci) {
        universalmod$clearCustomSwing();
        this.universalmod$currentItemScale = 1.0F;
        this.universalmod$currentRenderHand = null;
        this.universalmod$itemScaleDepth = 0;
        this.universalmod$mapScaleDepth = 0;
    }

    @Unique
    private boolean universalmod$pushItemScale(MatrixStack matrices, ItemStack stack) {
        float scale = this.universalmod$currentItemScale;
        if (stack == null || stack.isEmpty() || Float.compare(scale, 1.0F) == 0) {
            return false;
        }

        matrices.push();
        matrices.scale(scale, scale, scale);
        return true;
    }

    @Unique
    private static float universalmod$sanitizeItemScale(float scale) {
        if (!Float.isFinite(scale) || scale <= 0.0F) {
            return 1.0F;
        }
        return scale;
    }

    @Unique
    private void universalmod$markCustomSwing(MatrixStack matrices, Hand hand, float swingProgress) {
        this.universalmod$customSwingMatrices = matrices;
        this.universalmod$customSwingHand = hand;
        this.universalmod$customSwingProgress = swingProgress;
    }

    @Unique
    private boolean universalmod$consumeCustomSwing(MatrixStack matrices, Hand hand, float swingProgress) {
        boolean matches = this.universalmod$customSwingMatrices == matrices
                && this.universalmod$customSwingHand == hand
                && Float.compare(this.universalmod$customSwingProgress, swingProgress) == 0;
        if (matches) {
            universalmod$clearCustomSwing();
        }
        return matches;
    }

    @Unique
    private void universalmod$clearCustomSwing() {
        this.universalmod$customSwingMatrices = null;
        this.universalmod$customSwingHand = null;
        this.universalmod$customSwingProgress = 0.0F;
    }
}
