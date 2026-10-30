package universalmod.render.shape;

import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix4f;
import universalmod.render.Render2DCoordinateSpace;
import universalmod.render.Render2D;
import java.util.ArrayList;
import java.util.List;

public final class RectangleRenderer implements AutoCloseable {
    private static final RectangleRenderer INSTANCE = new RectangleRenderer();
    private DrawContext graphics;
    private RectangleRenderer() { }
    public static RectangleRenderer getInstance() { return INSTANCE; }
    public static void closeInstance() { INSTANCE.close(); }
    public void beginFrame(DrawContext g) { graphics = g; }
    public void beginGuiFrame() { }
    public void enqueue(RectangleShape shape) { draw(graphics, shape); }
    public void draw(DrawContext context, RectangleShape shape) {
        if (shape == null || !shape.visible()) return;
        Render2D.renderBarrier();
        if (!ShapeBatchRenderer.getInstance().drawRectangles(List.of(new RectangleState(shape, Render2DCoordinateSpace.matrix())))) {
            DefaultRectangleRenderer.getInstance().drawLegacy(context, toBuilt(shape));
        }
    }
    public void drawBatch(List<RectangleShape> shapes) {
        if (shapes == null || shapes.isEmpty()) return;
        Render2D.renderBarrier();

        for (int start=0;start<shapes.size();start+=ShapeUniformEncoder.MAX_SHAPES) {
            int end=Math.min(shapes.size(),start+ShapeUniformEncoder.MAX_SHAPES);
            List<RectangleState> part=new ArrayList<>();
            Matrix4f pose=new Matrix4f(Render2DCoordinateSpace.matrix());
            for (int i=start;i<end;i++) if (shapes.get(i)!=null && shapes.get(i).visible())
                part.add(new RectangleState(shapes.get(i),pose));
            if (!part.isEmpty() && !ShapeBatchRenderer.getInstance().drawRectangles(part))
                for (RectangleState state:part) DefaultRectangleRenderer.getInstance().drawLegacy(graphics,toBuilt(state.rectangle()));
        }
    }
    private static BuiltRectangle toBuilt(RectangleShape raw) {
        RectangleShape r = ShapeUniformEncoder.normalized(raw);
        return new BuiltRectangle(r.x(),r.y(),r.width(),r.height(),r.radiusTopLeft(),r.radiusTopRight(),r.radiusBottomRight(),r.radiusBottomLeft(),
                r.colorTopLeft(),r.colorTopRight(),r.colorBottomRight(),r.colorBottomLeft(),r.smoothness(),r.verticalSplit(),r.splitColor());
    }
    public void flush() { graphics=null; }
    @Override public void close() { graphics=null; ShapeBatchRenderer.getInstance().close(); }
}
