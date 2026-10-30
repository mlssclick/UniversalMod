package universalmod.render.smooth;

import net.minecraft.client.gui.DrawContext;
import universalmod.render.Render2DCoordinateSpace;
import universalmod.render.ScissorUtil;
import universalmod.render.gpu.GuiStateRenderer;
import universalmod.render.gpu.RenderPipeline;

public final class SmoothRoundedRenderer implements AutoCloseable {
    public static final RenderPipeline PIPELINE = new RenderPipeline(
            "ui/smooth_rounded/rounded", "ui/smooth_rounded/rounded");
    private static final SmoothRoundedRenderer INSTANCE = new SmoothRoundedRenderer();
    private DrawContext graphics;

    private SmoothRoundedRenderer() { }
    public static SmoothRoundedRenderer getInstance() { return INSTANCE; }
    public static void closeInstance() { INSTANCE.close(); }
    public void beginFrame(DrawContext graphics) { this.graphics = graphics; }
    public void flush() { graphics = null; }

    public void enqueue(SmoothRoundedShape shape) {
        if (shape == null || !shape.visible()) return;
        GuiStateRenderer.getInstance().submitGuiElement(new SmoothRoundedState(
                Render2DCoordinateSpace.pose(graphics), shape, ScissorUtil.current()));
    }
    @Override public void close() { graphics = null; PIPELINE.close(); }
}
