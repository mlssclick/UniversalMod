package universalmod.mixin.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import universalmod.module.render.NoRender;

 







@Mixin(HumanoidModel.class)
public abstract class HumanoidModelNoRenderMixin {
    @WrapMethod(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V")
    private void universalmod$noRenderPlayerAnimations(HumanoidRenderState state, Operation<Void> original) {
         
        if (!(state instanceof AvatarRenderState)) {
            original.call(state);
            return;
        }

        boolean stopWalk = NoRender.isActive("Player Walk");
        boolean stopSwing = NoRender.isActive("Player Swing");
        if (!stopWalk && !stopSwing) {
            original.call(state);
            return;
        }

        float walkSpeed = state.walkAnimationSpeed;
        float attackTime = state.attackTime;
        try {
             
             
             
            if (stopWalk) state.walkAnimationSpeed = 0.0F;

             
             
             
            if (stopSwing) state.attackTime = 0.0F;
            original.call(state);
        } finally {
             
             
            state.walkAnimationSpeed = walkSpeed;
            state.attackTime = attackTime;
        }
    }
}
