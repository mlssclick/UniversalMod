package universalmod.render.hud;

import net.minecraft.client.gui.DrawContext;

public final class HudChromeRenderer implements AutoCloseable {
    private static final HudChromeRenderer INSTANCE = new HudChromeRenderer();
    private HudChromeRenderer() {}
    public static HudChromeRenderer getInstance() { return INSTANCE; }
    public static void closeInstance() { INSTANCE.close(); }
    public void beginFrame(DrawContext graphics) { HudSurfaceRenderer.getInstance().beginFrame(graphics); }
    public void flush() { HudSurfaceRenderer.getInstance().flush(); }
    public void enqueue(BuiltHudChrome p) { HudSurfaceRenderer.getInstance().enqueue(toSurface(p)); }
    public void draw(DrawContext graphics, BuiltHudChrome p) { HudSurfaceRenderer.getInstance().draw(graphics, toSurface(p)); }
    static HudSurface toSurface(BuiltHudChrome p) {
        if (p == null) return null;
        return new HudSurface(p.x(), p.y(), p.width(), p.height(),
                p.radiusTopLeft(), p.radiusTopRight(), p.radiusBottomRight(), p.radiusBottomLeft(),
                p.alpha(), p.smoothness(), p.darkness());
    }
    @Override public void close() { HudSurfaceRenderer.closeInstance(); }
}
