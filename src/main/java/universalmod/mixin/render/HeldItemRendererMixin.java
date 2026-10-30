package universalmod.mixin.render;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Invoker;
import universalmod.event.type.HandAnimationEvent;
import universalmod.event.type.HandOffsetEvent;
import universalmod.lifecycle.ClientRuntime;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Invoker("applyEquipOffset")
    protected abstract void universalmod$invokeEquipOffset(MatrixStack matrices, Arm arm, float equipProgress);

    @Invoker("applySwingOffset")
    protected abstract void universalmod$invokeSwingOffset(MatrixStack matrices, Arm arm, float swingProgress);

    @Unique
    private int universalmod$handScaleDepth;
    @Unique
    private Hand universalmod$currentRenderHand;
    @Unique
    private AbstractClientPlayerEntity universalmod$currentPlayer;
    @Unique
    private float universalmod$currentSwingProgress;
    @Unique
    private boolean universalmod$customSwingActive;

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void universalmod$handOffset(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        universalmod$currentPlayer = player;
        universalmod$currentRenderHand = hand;
        universalmod$currentSwingProgress = swingProgress;
        universalmod$customSwingActive = false;
        HandOffsetEvent event = ClientRuntime.postEvent(new HandOffsetEvent(matrices, item, hand));
        float scale = event.getScale();
        if (Float.isFinite(scale) && scale > 0.0F && Float.compare(scale, 1.0F) != 0) {
            matrices.push();
            matrices.scale(scale, scale, scale);
            universalmod$handScaleDepth++;
        }
    }

    @Redirect(method = "renderFirstPersonItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applyEquipOffset(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/Arm;F)V"))
    private void universalmod$baseSwingAnimation(HeldItemRenderer instance, MatrixStack matrices, Arm arm, float equipProgress) {
        if (universalmod$isUsingCurrentHand()) {
            universalmod$invokeEquipOffset(matrices, arm, equipProgress);
            return;
        }
        Hand hand = universalmod$currentRenderHand != null ? universalmod$currentRenderHand : universalmod$handForArm(arm);
        universalmod$translateVanillaItemSwing(matrices, arm, universalmod$currentSwingProgress, -1.0F);
        HandAnimationEvent event = ClientRuntime.postEvent(new HandAnimationEvent(matrices, hand, universalmod$currentSwingProgress));
        if (event.isCancelled()) {
            universalmod$customSwingActive = true;
            return;
        }
        universalmod$translateVanillaItemSwing(matrices, arm, universalmod$currentSwingProgress, 1.0F);
        universalmod$invokeEquipOffset(matrices, arm, equipProgress);
    }

    @Redirect(method = "renderFirstPersonItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applySwingOffset(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/Arm;F)V"))
    private void universalmod$swingAnimation(HeldItemRenderer instance, MatrixStack matrices, Arm arm, float swingProgress) {
        if (universalmod$isUsingCurrentHand()) {
            universalmod$invokeSwingOffset(matrices, arm, swingProgress);
            return;
        }
        if (universalmod$customSwingActive) {
            universalmod$customSwingActive = false;
            return;
        }
        Hand hand = universalmod$currentRenderHand != null ? universalmod$currentRenderHand : universalmod$handForArm(arm);
        HandAnimationEvent event = ClientRuntime.postEvent(new HandAnimationEvent(matrices, hand, swingProgress));
        if (!event.isCancelled()) universalmod$invokeSwingOffset(matrices, arm, swingProgress);
    }

    @Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
    private void universalmod$handOffsetEnd(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (universalmod$handScaleDepth > 0) {
            universalmod$handScaleDepth--;
            matrices.pop();
        }
        universalmod$currentRenderHand = null;
        universalmod$currentPlayer = null;
        universalmod$currentSwingProgress = 0.0F;
        universalmod$customSwingActive = false;
    }

    @Unique
    private void universalmod$translateVanillaItemSwing(MatrixStack matrices, Arm arm, float swingProgress, float direction) {
        float root = MathHelper.sqrt(Math.max(0.0F, swingProgress));
        float x = -0.4F * MathHelper.sin(root * MathHelper.PI);
        float y = 0.2F * MathHelper.sin(root * (MathHelper.PI * 2.0F));
        float z = -0.2F * MathHelper.sin(swingProgress * MathHelper.PI);
        int side = arm == Arm.RIGHT ? 1 : -1;
        matrices.translate(direction * side * x, direction * y, direction * z);
    }

    @Unique
    private boolean universalmod$isUsingCurrentHand() {
        return universalmod$currentPlayer != null && universalmod$currentRenderHand != null
                && universalmod$currentPlayer.isUsingItem()
                && universalmod$currentPlayer.getActiveHand() == universalmod$currentRenderHand;
    }

    @Unique
    private Hand universalmod$handForArm(Arm arm) {
        return universalmod$currentPlayer != null && arm == universalmod$currentPlayer.getMainArm() ? Hand.MAIN_HAND : Hand.OFF_HAND;
    }
}
