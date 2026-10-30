package universalmod.render;

import net.minecraft.client.gui.GuiGraphics;
import universalmod.render.blur.BlurFramebuffer;
import universalmod.render.effect.EffectIconRenderer;
import universalmod.render.arc.UiArcRenderer;
import universalmod.render.font.msdf.MsdfIconRenderer;
import universalmod.render.glass.GlassOutlineRenderer;
import universalmod.render.glass.GlassRenderer;
import universalmod.render.hud.HudSurfaceRenderer;
import universalmod.render.image.ImageRenderer;
import universalmod.render.shape.OutlineRenderer;
import universalmod.render.shape.RectangleRenderer;
import universalmod.render.smooth.SmoothRoundedRenderer;
import universalmod.render.surface.GuiSurfaceRenderer;


final class Render2DBackends {
    private Render2DBackends() {
    }

    static void beginFrame(GuiGraphics graphics) {
        BlurFramebuffer.getInstance().beginFrame(graphics);
        GlassRenderer.getInstance().beginFrame(graphics);
        HudSurfaceRenderer.getInstance().beginFrame(graphics);
        GlassOutlineRenderer.getInstance().beginFrame(graphics);
        RectangleRenderer.getInstance().beginFrame(graphics);
        SmoothRoundedRenderer.getInstance().beginFrame(graphics);
        OutlineRenderer.getInstance().beginFrame(graphics);
        MsdfIconRenderer.getInstance().beginFrame(graphics);
        ImageRenderer.getInstance().beginFrame(graphics);
        EffectIconRenderer.getInstance().beginFrame(graphics);
    }

    static void flush() {
        BlurFramebuffer.getInstance().flush();
        GlassRenderer.getInstance().flush();
        HudSurfaceRenderer.getInstance().flush();
        GlassOutlineRenderer.getInstance().flush();
        RectangleRenderer.getInstance().flush();
        SmoothRoundedRenderer.getInstance().flush();
        OutlineRenderer.getInstance().flush();
        MsdfIconRenderer.getInstance().flush();
        ImageRenderer.getInstance().flush();
        EffectIconRenderer.getInstance().flush();
    }

    static void closeAll() {
        closeSafely("blur framebuffer", BlurFramebuffer::closeInstance);
        closeSafely("glass renderer", GlassRenderer::closeInstance);
        closeSafely("HUD surface renderer", HudSurfaceRenderer::closeInstance);
        closeSafely("glass outline renderer", GlassOutlineRenderer::closeInstance);
        closeSafely("rectangle renderer", RectangleRenderer::closeInstance);
        closeSafely("smooth rounded renderer", SmoothRoundedRenderer::closeInstance);
        closeSafely("outline renderer", OutlineRenderer::closeInstance);
        closeSafely("MSDF renderer", MsdfIconRenderer::closeInstance);
        closeSafely("image renderer", ImageRenderer::closeInstance);
        closeSafely("effect icon renderer", EffectIconRenderer::closeInstance);
        closeSafely("cooldown arc renderer", UiArcRenderer::closeInstance);
        closeSafely("ClickGUI surface renderer", GuiSurfaceRenderer::closeInstance);
    }

    private static void closeSafely(String name, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | LinkageError failure) {
            
        }
    }
}
