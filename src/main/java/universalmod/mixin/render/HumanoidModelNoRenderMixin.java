package universalmod.mixin.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import universalmod.module.render.NoRender;

@Mixin(BipedEntityModel.class)
public abstract class HumanoidModelNoRenderMixin {
    @WrapMethod(method = "setAngles(Lnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V")
    private void universalmod$noRenderPlayerAnimations(
            BipedEntityRenderState state, Operation<Void> original
    ) {
        if (!(state instanceof PlayerEntityRenderState)) {
            original.call(state);
            return;
        }
        boolean stopWalk = NoRender.isActive("Player Walk");
        boolean stopSwing = NoRender.isActive("Player Swing");
        if (!stopWalk && !stopSwing) {
            original.call(state);
            return;
        }
        float limbAmplitudeMultiplier = state.limbAmplitudeMultiplier;
        float handSwingProgress = state.handSwingProgress;
        try {
            if (stopWalk) state.limbAmplitudeMultiplier = 0.0F;
            if (stopSwing) state.handSwingProgress = 0.0F;
            original.call(state);
        } finally {
            state.limbAmplitudeMultiplier = limbAmplitudeMultiplier;
            state.handSwingProgress = handSwingProgress;
        }
    }
}
