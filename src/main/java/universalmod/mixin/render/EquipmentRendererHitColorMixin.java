package universalmod.mixin.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import universalmod.module.render.HitColor;
import universalmod.render.HitColorArmorRenderContext;

@Mixin(EquipmentRenderer.class)
public abstract class EquipmentRendererHitColorMixin {
    @WrapOperation(
            method = "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/util/Identifier;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/RenderLayer;getArmorCutoutNoCull(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/render/RenderLayer;"
            ),
            require = 0
    )
    private RenderLayer universalmod$useOverlayCapableArmorLayer(Identifier texture, Operation<RenderLayer> original) {
        if (shouldApplyArmorHitColor()) {
            return RenderLayer.getEntityCutoutNoCull(texture);
        }
        return original.call(texture);
    }

    @ModifyExpressionValue(
            method = "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/util/Identifier;)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/OverlayTexture;DEFAULT_UV:I"),
            require = 0
    )
    private int universalmod$applyHitOverlayToArmor(int original) {
        return shouldApplyArmorHitColor() ? OverlayTexture.getUv(0.0F, true) : original;
    }

    private static boolean shouldApplyArmorHitColor() {
        if (!HitColorArmorRenderContext.isActive()) {
            return false;
        }
        HitColor hitColor = HitColor.getInstance();
        return hitColor != null && hitColor.isEnabled() && hitColor.shouldTintArmor();
    }
}
