package universalmod.mixin.render;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.HitColor;
import universalmod.render.HitColorArmorRenderContext;

@Mixin(ArmorFeatureRenderer.class)
public abstract class ArmorFeatureRendererHitColorMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$captureArmorHitColor(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            BipedEntityRenderState state,
            float limbAngle,
            float limbDistance,
            CallbackInfo ci
    ) {

        HitColor hitColor = HitColor.getInstance();
        HitColorArmorRenderContext.setActive(
                hitColor != null
                        && hitColor.isEnabled()
                        && hitColor.shouldTintArmor()
                        && state != null
                        && state.hurt
        );
    }

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void universalmod$clearArmorHitColor(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            BipedEntityRenderState state,
            float limbAngle,
            float limbDistance,
            CallbackInfo ci
    ) {
        HitColorArmorRenderContext.clear();
    }
}
