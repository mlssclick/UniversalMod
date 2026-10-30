package universalmod.render.shape;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import universalmod.render.ClientPipelines;
import universalmod.render.Render2DCoordinateSpace;

public final class DefaultOutlineRenderer implements AutoCloseable {
    private static final DefaultOutlineRenderer INSTANCE = new DefaultOutlineRenderer();
    private DrawContext activeGraphics;

    private DefaultOutlineRenderer() {
    }

    public static DefaultOutlineRenderer getInstance() {
        return INSTANCE;
    }

    public static void closeInstance() {
        INSTANCE.close();
    }

    public void beginFrame(DrawContext g) {
        activeGraphics = g;
    }

    public void enqueue(BuiltOutline o) {
        draw(activeGraphics, o);
    }

    public void draw(DrawContext g, BuiltOutline o) {
        if (o == null || !o.visible()) return;
        OutlineRenderer.getInstance().draw(g,new OutlineShape(o.x(),o.y(),o.width(),o.height(),
                o.radiusTopLeft(),o.radiusTopRight(),o.radiusBottomRight(),o.radiusBottomLeft(),
                o.thickness(),o.colorTopLeft(),o.colorTopRight(),o.colorBottomRight(),o.colorBottomLeft(),o.smoothness()));
    }

    void drawLegacy(DrawContext g, BuiltOutline o) {
        if (o == null || !o.visible()) return;
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        try {
            ShaderProgram s = ClientPipelines.bind(ClientPipelines.OUTLINE, "ui_outline_default");
            if (s == null) return;
            set2(s, "Size", o.width(), o.height());
            set4(s, "Radius", o.radiusTopLeft(), o.radiusTopRight(), o.radiusBottomRight(), o.radiusBottomLeft());
            set1(s, "Thickness", o.thickness());
            set1(s, "Smoothness", Math.max(.001f, o.smoothness()));
            setColor(s, "TopLeft", o.colorTopLeft());
            setColor(s, "TopRight", o.colorTopRight());
            setColor(s, "BottomRight", o.colorBottomRight());
            setColor(s, "BottomLeft", o.colorBottomLeft());
            BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            var m = Render2DCoordinateSpace.matrix();
            b.vertex(m, o.x(), o.y(), 0).color(0xFFFFFFFF);
            b.vertex(m, o.x(), o.y() + o.height(), 0).color(0xFFFFFFFF);
            b.vertex(m, o.x() + o.width(), o.y() + o.height(), 0).color(0xFFFFFFFF);
            b.vertex(m, o.x() + o.width(), o.y(), 0).color(0xFFFFFFFF);
            BufferRenderer.drawWithGlobalProgram(b.end());
        } finally {
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
        }
    }

    public void flush() {
        activeGraphics = null;
    }

    @Override
    public void close() {
        activeGraphics = null;
    }

    private static void set1(ShaderProgram s, String n, float a) {
        var u = s.getUniform(n);
        if (u != null) u.set(a);
    }

    private static void set2(ShaderProgram s, String n, float a, float b) {
        var u = s.getUniform(n);
        if (u != null) u.set(a, b);
    }

    private static void set4(ShaderProgram s, String n, float a, float b, float c, float d) {
        var u = s.getUniform(n);
        if (u != null) u.set(a, b, c, d);
    }

    private static void setColor(ShaderProgram s, String n, int c) {
        set4(s, n, ((c >>> 16) & 255) / 255f, ((c >>> 8) & 255) / 255f, (c & 255) / 255f, ((c >>> 24) & 255) / 255f);
    }
}
