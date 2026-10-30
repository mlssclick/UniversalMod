package universalmod.mixin.render;

import net.minecraft.client.model.Model;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.HitColor;
import universalmod.render.hitcolor.HitColorArmorRenderContext;

@Mixin(ArmorFeatureRenderer.class)
public abstract class ArmorFeatureRendererHitColorMixin {
    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void universalmod$captureHitColorArmorState(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, LivingEntity entity, float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        HitColor hitColor = HitColor.getInstance();
        HitColorArmorRenderContext.setActive(hitColor != null && hitColor.isEnabled() && hitColor.shouldTintArmor() && entity != null && (entity.hurtTime > 0 || entity.deathTime > 0));
    }

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void universalmod$clearHitColorArmorState(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, LivingEntity entity, float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        HitColorArmorRenderContext.clear();
    }

    @Redirect(method = "renderArmorParts", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/RenderLayer;getArmorCutoutNoCull(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/render/RenderLayer;"), require = 0)
    private RenderLayer universalmod$useEntityLayerForHitColorArmor(Identifier texture) {
        return universalmod$shouldApplyArmorHitColor()
                ? RenderLayer.getEntityCutoutNoCull(texture)
                : RenderLayer.getArmorCutoutNoCull(texture);
    }

    @ModifyArg(method = "renderArmorParts", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V"), index = 3, require = 0)
    private int universalmod$applyHitColorOverlayToArmorModel(int overlay) {
        return universalmod$shouldApplyArmorHitColor()
                ? OverlayTexture.getUv(0.0F, true)
                : overlay;
    }

    @ModifyArg(method = "renderArmorParts", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/BipedEntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V"), index = 3, require = 0)
    private int universalmod$applyHitColorOverlayToBipedArmorModel(int overlay) {
        return universalmod$shouldApplyArmorHitColor()
                ? OverlayTexture.getUv(0.0F, true)
                : overlay;
    }

    private static boolean universalmod$shouldApplyArmorHitColor() {
        if (!HitColorArmorRenderContext.isActive()) {
            return false;
        }
        HitColor hitColor = HitColor.getInstance();
        return hitColor != null
                && hitColor.isEnabled()
                && hitColor.shouldTintArmor();
    }
}
