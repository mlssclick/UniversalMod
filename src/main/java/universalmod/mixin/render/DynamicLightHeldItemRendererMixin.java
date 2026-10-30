package universalmod.mixin.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import universalmod.module.misc.Fullbright;
import universalmod.util.GlowPropagator;

@Mixin(ItemInHandRenderer.class)
public abstract class DynamicLightHeldItemRendererMixin {
    @ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int universalmod$dynamicHeldItemLight(int light) {
        Fullbright fullbright = Fullbright.getInstance();
        Minecraft mc = Minecraft.getInstance();
        if (fullbright != null && fullbright.isDynamicMode() && mc.player != null) {
            return GlowPropagator.applyBlockLight(BlockPos.containing(mc.player.getEyePosition()), light);
        }
        return light;
    }
}
