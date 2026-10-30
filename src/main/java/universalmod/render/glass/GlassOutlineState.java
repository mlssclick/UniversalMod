package universalmod.render.glass;

import universalmod.render.gpu.RenderPipeline;
import universalmod.render.gpu.GuiVertexConsumer;
import universalmod.render.blur.BlurFramebuffer;
import universalmod.render.gpu.ScreenRectangle;
import universalmod.render.gpu.TextureSetup;
import universalmod.render.gpu.GuiElementRenderState;
import org.joml.Matrix3x2f;

final class GlassOutlineState implements GuiElementRenderState {
    private final GlassOutline outline;
    private final Matrix3x2f pose;
    private final ScreenRectangle scissorArea;
    private final ScreenRectangle bounds;

    GlassOutlineState(Matrix3x2f pose, GlassOutline outline, ScreenRectangle scissorArea) {
        this.outline = outline;
        this.pose = pose;
        this.scissorArea = scissorArea;
        ScreenRectangle transformedBounds = new ScreenRectangle(
                Math.round(outline.x()),
                Math.round(outline.y()),
                Math.round(outline.width()),
                Math.round(outline.height())
        ).transformMaxBounds(this.pose);
        this.bounds = scissorArea == null ? transformedBounds : scissorArea.intersection(transformedBounds);
    }

    @Override
    public void buildVertices(GuiVertexConsumer consumer) {
        int batchIndex = GlassOutlineRenderer.getInstance().reserve(outline);
        if (batchIndex < 0) {
            return;
        }

        float x0 = outline.x();
        float y0 = outline.y();
        float x1 = outline.x() + outline.width();
        float y1 = outline.y() + outline.height();

        vertex(consumer, x0, y0, batchIndex);
        vertex(consumer, x0, y1, batchIndex);
        vertex(consumer, x1, y1, batchIndex);
        vertex(consumer, x1, y0, batchIndex);
    }

    private void vertex(GuiVertexConsumer consumer, float x, float y, int batchIndex) {
        consumer.addVertexWith2DPose(pose, x, y)
                .setColor(outline.color())
                .setLineWidth((float) (batchIndex + 1));
    }

    @Override
    public RenderPipeline pipeline() {
        return GlassOutlineRenderer.GLASS_OUTLINE_PIPELINE;
    }

    @Override
    public TextureSetup textureSetup() {
        return BlurFramebuffer.getInstance().textureSetup();
    }

    @Override
    public ScreenRectangle scissorArea() {
        return scissorArea;
    }

    @Override
    public ScreenRectangle bounds() {
        return bounds;
    }
}
