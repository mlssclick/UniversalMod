package universalmod.render.hud;

import net.minecraft.client.gui.DrawContext;

 



public final class HudSurfaceRenderer implements AutoCloseable {
    private static final HudSurfaceRenderer INSTANCE = new HudSurfaceRenderer();
    private DrawContext graphics;
    private HudSurfaceRenderer() { }
    public static HudSurfaceRenderer getInstance() { return INSTANCE; }
    public static void closeInstance() { INSTANCE.close(); }
    public void beginFrame(DrawContext context) { graphics = context; }
    public void draw(DrawContext context, HudSurface panel) { submit(panel); }
    public void enqueue(HudSurface panel) { submit(panel); }
    public void flush() { graphics = null; }
    @Override public void close() { graphics = null; }

    public static HudSurface normalize(HudSurface p) {
        if (p == null) return null;
        float maxRadius = Math.max(0f, Math.min(p.width(), p.height()) * 0.5f);
        return new HudSurface(p.x(), p.y(), p.width(), p.height(),
                clamp(p.radiusTopLeft(), 0, maxRadius),
                clamp(p.radiusTopRight(), 0, maxRadius),
                clamp(p.radiusBottomRight(), 0, maxRadius),
                clamp(p.radiusBottomLeft(), 0, maxRadius),
                clamp(p.alpha(), 0, 1), Math.max(.45f, p.smoothness()),
                clamp(p.darkness(), 0, 1));
    }

    private static void submit(HudSurface panel) {
        if (panel == null || !panel.visible()) return;
        HudSurface p = normalize(panel);
        HudChromeRenderer.draw(p.x(), p.y(), p.width(), p.height(),
                p.radiusTopLeft(), p.radiusTopRight(), p.radiusBottomRight(),
                p.radiusBottomLeft(), p.alpha(), p.smoothness(), p.darkness());
    }
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
