package universalmod.render.glass;

import net.minecraft.client.gui.DrawContext;

 




public final class GlassOutlineRenderer implements AutoCloseable {
    private static final GlassOutlineRenderer INSTANCE = new GlassOutlineRenderer();
    private DrawContext activeGraphics;

    private GlassOutlineRenderer() { }
    public static GlassOutlineRenderer getInstance() { return INSTANCE; }
    public static void closeInstance() { INSTANCE.close(); }

    public void beginFrame(DrawContext context) { activeGraphics = context; }
    public void enqueue(GlassOutline outline) { draw(activeGraphics, outline); }

    public void draw(DrawContext context, GlassOutline outline) {
        if (context != null) activeGraphics = context;
        if (outline == null || !outline.visible()) return;
        GlassRenderer.draw(context, outline);
    }

    public void flush() { activeGraphics = null; }
    @Override public void close() { activeGraphics = null; }
}
