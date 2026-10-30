package universalmod.render.shape;

import net.minecraft.client.gui.DrawContext;

 


public final class RectangleRenderer implements AutoCloseable {
    private static final RectangleRenderer INSTANCE = new RectangleRenderer();
    private DrawContext graphics;

    private RectangleRenderer() { }
    public static RectangleRenderer getInstance() { return INSTANCE; }
    public static void closeInstance() { INSTANCE.close(); }

    public void beginFrame(DrawContext context) { graphics = context; }
    public void draw(DrawContext context, RectangleShape shape) {
        if (shape == null || !shape.visible()) return;
        ShapeRenderer.rect(normalize(shape));
    }
    public void enqueue(RectangleShape shape) { draw(graphics, shape); }
    public void flush() { graphics = null; }
    @Override public void close() { graphics = null; }

    public static RectangleShape normalize(RectangleShape r) {
        if (r == null) return null;
        float maxRadius = Math.max(0.0f, Math.min(r.width(), r.height()) * 0.5f);
        float tl = clamp(r.radiusTopLeft(), 0, maxRadius);
        float tr = clamp(r.radiusTopRight(), 0, maxRadius);
        float br = clamp(r.radiusBottomRight(), 0, maxRadius);
        float bl = clamp(r.radiusBottomLeft(), 0, maxRadius);
        float smoothness = Math.max(r.smoothness(), 0.5f);
        float split = r.verticalSplit();
        if (!Float.isFinite(split) || split < 0) split = RectangleShape.NO_VERTICAL_SPLIT;
        else split = clamp(split, 0, Math.max(r.height(), 0));
        return new RectangleShape(r.x(), r.y(), r.width(), r.height(), tl, tr, br, bl,
                r.colorTopLeft(), r.colorTopRight(), r.colorBottomRight(), r.colorBottomLeft(),
                smoothness, split, r.splitColor());
    }
    private static float clamp(float x, float min, float max) { return Math.max(min, Math.min(max, x)); }
}
