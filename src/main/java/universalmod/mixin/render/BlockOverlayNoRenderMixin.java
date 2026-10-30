package universalmod.mixin.render;

import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.NoRender;

@Mixin(InGameOverlayRenderer.class)
public abstract class BlockOverlayNoRenderMixin {
    @Inject(method = "renderInWallOverlay", at = @At("HEAD"), cancellable = true)
    private static void universalmod$block(CallbackInfo ci) {
        if (NoRender.isActive("Block Overlay")) ci.cancel();
    }
}
