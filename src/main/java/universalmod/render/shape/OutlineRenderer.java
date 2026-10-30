package universalmod.render.shape;

import net.minecraft.client.gui.DrawContext;

 






public final class OutlineRenderer implements AutoCloseable {
    private static final OutlineRenderer INSTANCE = new OutlineRenderer();
    private DrawContext activeGraphics;

    private OutlineRenderer() { }

    public static OutlineRenderer getInstance() { return INSTANCE; }

    public static void closeInstance() { INSTANCE.close(); }

    public void beginFrame(DrawContext graphics) { activeGraphics = graphics; }

    public void draw(DrawContext graphics, OutlineShape outline) {
        if (graphics != null) activeGraphics = graphics;
        enqueue(outline);
    }

    public void enqueue(OutlineShape outline) {
        if (outline == null || !outline.visible()) return;
        OutlineShape s = normalize(outline);
        ShapeRenderer.outline(s.x(), s.y(), s.width(), s.height(),
                s.radiusTopLeft(), s.radiusTopRight(), s.radiusBottomRight(), s.radiusBottomLeft(),
                s.thickness(), s.smoothness(),
                s.colorTopLeft(), s.colorTopRight(), s.colorBottomRight(), s.colorBottomLeft());
    }

     
    public void flush() { activeGraphics = null; }

    public static OutlineShape normalize(OutlineShape s) {
        if (s == null) return null;
        float maxRadius = Math.max(0.0f, Math.min(s.width(), s.height()) * 0.5f);
        float maxThickness = maxRadius;
        float tl = clamp(s.radiusTopLeft(), 0.0f, maxRadius);
        float tr = clamp(s.radiusTopRight(), 0.0f, maxRadius);
        float br = clamp(s.radiusBottomRight(), 0.0f, maxRadius);
        float bl = clamp(s.radiusBottomLeft(), 0.0f, maxRadius);
        float thickness = clamp(s.thickness(), 0.0f, maxThickness);
        float smoothness = Math.max(s.smoothness(), 0.5f);
        if (tl == s.radiusTopLeft() && tr == s.radiusTopRight()
                && br == s.radiusBottomRight() && bl == s.radiusBottomLeft()
                && thickness == s.thickness() && smoothness == s.smoothness()) return s;
        return new OutlineShape(s.x(), s.y(), s.width(), s.height(),
                tl, tr, br, bl, thickness,
                s.colorTopLeft(), s.colorTopRight(), s.colorBottomRight(), s.colorBottomLeft(), smoothness);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override public void close() { activeGraphics = null; }
}
