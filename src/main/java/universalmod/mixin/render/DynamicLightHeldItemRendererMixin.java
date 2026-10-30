package universalmod.mixin.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import universalmod.module.misc.Fullbright;
import universalmod.util.render.world.GlowPropagator;

@Mixin(HeldItemRenderer.class)
public abstract class DynamicLightHeldItemRendererMixin {
    @ModifyVariable(method = "renderFirstPersonItem", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int universalmod$dynamicHeldItemLight(int light) {
        Fullbright fullbright = Fullbright.getInstance();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (fullbright != null && fullbright.isDynamicMode() && mc.player != null) {
            return GlowPropagator.applyBlockLight(BlockPos.ofFloored(mc.player.getEyePos()), light);
        }
        return light;
    }
}
