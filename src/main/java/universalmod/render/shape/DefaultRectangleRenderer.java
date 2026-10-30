package universalmod.render.shape;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import universalmod.render.ClientPipelines;
import universalmod.render.Render2DCoordinateSpace;




public final class DefaultRectangleRenderer implements AutoCloseable {
    private static final DefaultRectangleRenderer INSTANCE = new DefaultRectangleRenderer();
    private DrawContext activeGraphics;

    private DefaultRectangleRenderer() {
    }

    public static DefaultRectangleRenderer getInstance() {
        return INSTANCE;
    }

    public static void closeInstance() {
        INSTANCE.close();
    }

    public void beginFrame(DrawContext graphics) {
        activeGraphics = graphics;
    }

    public void enqueue(BuiltRectangle rectangle) {
        draw(activeGraphics, rectangle);
    }

    public void draw(DrawContext graphics, BuiltRectangle r) {
        if (r == null || !r.visible()) return;
        RectangleRenderer.getInstance().draw(graphics,new RectangleShape(r.x(),r.y(),r.width(),r.height(),
                r.radiusTopLeft(),r.radiusTopRight(),r.radiusBottomRight(),r.radiusBottomLeft(),
                r.colorTopLeft(),r.colorTopRight(),r.colorBottomRight(),r.colorBottomLeft(),
                r.smoothness(),r.verticalSplit(),r.splitColor()));
    }

    void drawLegacy(DrawContext graphics, BuiltRectangle r) {
        if (r == null || !r.visible()) return;
        beginState();
        try {
            ShaderProgram shader = ClientPipelines.bind(ClientPipelines.RECTANGLE, "ui_rect_default");
            if (shader == null) return;
            set2(shader, "Size", r.width(), r.height());
            set4(shader, "Radius", r.radiusTopLeft(), r.radiusTopRight(), r.radiusBottomRight(), r.radiusBottomLeft());
            set1(shader, "Smoothness", Math.max(0.001f, r.smoothness()));
            setColor(shader, "TopLeft", r.colorTopLeft());
            setColor(shader, "TopRight", r.colorTopRight());
            setColor(shader, "BottomRight", r.colorBottomRight());
            setColor(shader, "BottomLeft", r.colorBottomLeft());
            set1(shader, "VerticalSplit", r.verticalSplit());
            setColor(shader, "SplitColor", r.splitColor());
            BufferBuilder b = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            var m = Render2DCoordinateSpace.matrix();
            b.vertex(m, r.x(), r.y(), 0).color(0xFFFFFFFF);
            b.vertex(m, r.x(), r.y() + r.height(), 0).color(0xFFFFFFFF);
            b.vertex(m, r.x() + r.width(), r.y() + r.height(), 0).color(0xFFFFFFFF);
            b.vertex(m, r.x() + r.width(), r.y(), 0).color(0xFFFFFFFF);
            BufferRenderer.drawWithGlobalProgram(b.end());
        } finally {
            endState();
        }
    }

    public void flush() {
        activeGraphics = null;
    }

    @Override
    public void close() {
        activeGraphics = null;
    }

    private static void beginState() {
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    private static void endState() {
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
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
