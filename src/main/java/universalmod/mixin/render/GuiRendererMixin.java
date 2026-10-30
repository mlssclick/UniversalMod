package universalmod.mixin.render;

import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.access.GuiGraphicsExtractorAccessor;
import universalmod.access.ChatGraphicsAccessInvoker;
import universalmod.render.gpu.GuiStateRenderer;

@Mixin(DrawContext.class)
public abstract class GuiRendererMixin implements GuiGraphicsExtractorAccessor, ChatGraphicsAccessInvoker {
    @Override public GuiStateRenderer universalmod$getGuiRenderState() { return GuiStateRenderer.getInstance(); }
    @Inject(method="draw()V",at=@At("HEAD"))
    private void universalmod$flushExtractedStates(CallbackInfo ci) {
        GuiStateRenderer.getInstance().flush();
    }
}
