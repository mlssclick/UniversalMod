package universalmod.render;

import net.minecraft.client.gui.DrawContext;
import universalmod.render.shape.RectangleRenderer;
import universalmod.render.shape.OutlineRenderer;
import universalmod.render.glass.GlassOutlineRenderer;
import universalmod.render.hud.HudSurfaceRenderer;
import universalmod.render.smooth.SmoothRoundedRenderer;

 



final class Render2DBackends {
    private Render2DBackends() { }
    static void beginFrame(DrawContext context) {
        RectangleRenderer.getInstance().beginFrame(context);
        OutlineRenderer.getInstance().beginFrame(context);
        GlassOutlineRenderer.getInstance().beginFrame(context);
        HudSurfaceRenderer.getInstance().beginFrame(context);
        SmoothRoundedRenderer.getInstance().beginFrame(context);
    }
    static void flush() {
        SmoothRoundedRenderer.getInstance().flush();
        RectangleRenderer.getInstance().flush();
        OutlineRenderer.getInstance().flush();
        GlassOutlineRenderer.getInstance().flush();
        HudSurfaceRenderer.getInstance().flush();
    }
    static void closeAll() {
        SmoothRoundedRenderer.closeInstance();
        RectangleRenderer.closeInstance();
        OutlineRenderer.closeInstance();
        GlassOutlineRenderer.closeInstance();
        HudSurfaceRenderer.closeInstance();
    }
}
