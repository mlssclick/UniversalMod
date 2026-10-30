package universalmod.render.shape;

import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix4f;
import universalmod.render.Render2DCoordinateSpace;
import universalmod.render.Render2D;
import java.util.ArrayList;
import java.util.List;

public final class OutlineRenderer implements AutoCloseable {
    private static final OutlineRenderer INSTANCE = new OutlineRenderer();
    private DrawContext graphics;
    private OutlineRenderer() { }
    public static OutlineRenderer getInstance() { return INSTANCE; }
    public static void closeInstance() { INSTANCE.close(); }
    public void beginFrame(DrawContext g) { graphics=g; }
    public void beginGuiFrame() { }
    public void enqueue(OutlineShape shape) { draw(graphics,shape); }
    public void draw(DrawContext context, OutlineShape shape) {
        if (shape==null || !shape.visible()) return;
        Render2D.renderBarrier();
        if (!ShapeBatchRenderer.getInstance().drawOutlines(List.of(new OutlineState(shape,Render2DCoordinateSpace.matrix()))))
            DefaultOutlineRenderer.getInstance().drawLegacy(context,toBuilt(shape));
    }
    public void drawBatch(List<OutlineShape> shapes) {
        if (shapes==null || shapes.isEmpty()) return;
        Render2D.renderBarrier();
        for (int start=0;start<shapes.size();start+=ShapeUniformEncoder.MAX_SHAPES) {
            List<OutlineState> part=new ArrayList<>();
            Matrix4f pose=new Matrix4f(Render2DCoordinateSpace.matrix());
            for (int i=start;i<Math.min(shapes.size(),start+ShapeUniformEncoder.MAX_SHAPES);i++)
                if (shapes.get(i)!=null && shapes.get(i).visible()) part.add(new OutlineState(shapes.get(i),pose));
            if (!part.isEmpty() && !ShapeBatchRenderer.getInstance().drawOutlines(part))
                for (OutlineState state:part) DefaultOutlineRenderer.getInstance().drawLegacy(graphics,toBuilt(state.outline()));
        }
    }
    private static BuiltOutline toBuilt(OutlineShape raw) {
        OutlineShape o = ShapeUniformEncoder.normalized(raw);
        return new BuiltOutline(o.x(),o.y(),o.width(),o.height(),o.radiusTopLeft(),o.radiusTopRight(),o.radiusBottomRight(),o.radiusBottomLeft(),
                o.thickness(),o.colorTopLeft(),o.colorTopRight(),o.colorBottomRight(),o.colorBottomLeft(),o.smoothness());
    }
    public void flush(){graphics=null;}
    @Override public void close(){graphics=null;ShapeBatchRenderer.getInstance().close();}
}
