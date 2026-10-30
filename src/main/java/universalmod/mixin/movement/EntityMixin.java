package universalmod.mixin.movement;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.entity.Entity;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({Entity.class})
public abstract class EntityMixin {
    @ModifyExpressionValue(
            method = {"move"},
            at = {@At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;isControlledByPlayer()Z"
            )}
    )
    private boolean fixFallDistanceCalculation(boolean original) {
        return (Object) this == MinecraftClient.getInstance().player ? false : original;
    }
}
