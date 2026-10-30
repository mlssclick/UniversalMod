package universalmod.render.shape;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

public final class ShapeUniformEncoder {
    public static final int MAX_SHAPES = 512;
    public static final int RECTANGLE_BYTES = 7 * 16;
    public static final int OUTLINE_BYTES = 6 * 16;

    private ShapeUniformEncoder() { }

    public static RectangleShape normalized(RectangleShape r) {
        float maxRadius = Math.max(0.0f, Math.min(r.width(), r.height()) * .5f);
        float tl = clamp(r.radiusTopLeft(), 0, maxRadius), tr = clamp(r.radiusTopRight(), 0, maxRadius);
        float br = clamp(r.radiusBottomRight(), 0, maxRadius), bl = clamp(r.radiusBottomLeft(), 0, maxRadius);
        float smoothness = Math.max(r.smoothness(), .5f);
        float split = r.verticalSplit();
        split = !Float.isFinite(split) || split < 0 ? RectangleShape.NO_VERTICAL_SPLIT
                : clamp(split, 0.0f, Math.max(0.0f, r.height()));
        return new RectangleShape(r.x(), r.y(), r.width(), r.height(), tl, tr, br, bl,
                r.colorTopLeft(), r.colorTopRight(), r.colorBottomRight(), r.colorBottomLeft(),
                smoothness, split, r.splitColor());
    }

    public static OutlineShape normalized(OutlineShape o) {
        float radius = Math.max(0.0f, Math.min(o.width(), o.height()) * .5f);
        float tl = clamp(o.radiusTopLeft(), 0, radius), tr = clamp(o.radiusTopRight(), 0, radius);
        float br = clamp(o.radiusBottomRight(), 0, radius), bl = clamp(o.radiusBottomLeft(), 0, radius);
        float thickness = clamp(o.thickness(), 0.0f, radius);
        return new OutlineShape(o.x(), o.y(), o.width(), o.height(), tl, tr, br, bl, thickness,
                o.colorTopLeft(), o.colorTopRight(), o.colorBottomRight(), o.colorBottomLeft(),
                Math.max(o.smoothness(), .5f));
    }

    public static ByteBuffer rectangles(List<RectangleShape> shapes) {
        validate(shapes);
        ByteBuffer data = buffer(shapes.size(), RECTANGLE_BYTES);
        for (int i = 0; i < shapes.size(); i++) {
            RectangleShape r = normalized(shapes.get(i));
            int p = i * RECTANGLE_BYTES;
            put4(data, p, r.radiusTopLeft(), r.radiusTopRight(), r.radiusBottomRight(), r.radiusBottomLeft());
            put4(data, p+16, r.width(), r.height(), r.smoothness(), r.verticalSplit());
            color(data,p+32,r.colorTopLeft()); color(data,p+48,r.colorTopRight());
            color(data,p+64,r.colorBottomRight()); color(data,p+80,r.colorBottomLeft());
            color(data,p+96,r.splitColor());
        }
        return data;
    }

    public static ByteBuffer outlines(List<OutlineShape> shapes) {
        validate(shapes);
        ByteBuffer data = buffer(shapes.size(), OUTLINE_BYTES);
        for (int i = 0; i < shapes.size(); i++) {
            OutlineShape o = normalized(shapes.get(i));
            int p = i * OUTLINE_BYTES;
            put4(data,p,o.radiusTopLeft(),o.radiusTopRight(),o.radiusBottomRight(),o.radiusBottomLeft());
            put4(data,p+16,o.width(),o.height(),o.thickness(),o.smoothness());
            color(data,p+32,o.colorTopLeft()); color(data,p+48,o.colorTopRight());
            color(data,p+64,o.colorBottomRight()); color(data,p+80,o.colorBottomLeft());
        }
        return data;
    }

    private static void validate(List<?> shapes) {
        if (shapes == null || shapes.size() > MAX_SHAPES)
            throw new IllegalArgumentException("Null batch or more than 512 shapes");
        for (Object shape : shapes) if (shape == null)
            throw new IllegalArgumentException("Null shape in batch");
    }
    private static ByteBuffer buffer(int count, int stride) {
        return ByteBuffer.allocateDirect(Math.max(1, count) * stride).order(ByteOrder.nativeOrder());
    }
    private static float clamp(float v, float a, float b) { return Math.max(a,Math.min(b,v)); }
    private static void put4(ByteBuffer b,int p,float a,float c,float d,float e) {
        b.putFloat(p,a); b.putFloat(p+4,c); b.putFloat(p+8,d); b.putFloat(p+12,e);
    }
    private static void color(ByteBuffer b,int p,int argb) {
        put4(b,p,((argb>>>16)&255)/255f,((argb>>>8)&255)/255f,(argb&255)/255f,((argb>>>24)&255)/255f);
    }
}
