package universalmod.render.glass;

import universalmod.render.gpu.RenderPipeline;
import universalmod.render.gpu.GuiVertexConsumer;
import universalmod.render.blur.BlurFramebuffer;
import universalmod.render.gpu.ScreenRectangle;
import universalmod.render.gpu.TextureSetup;
import universalmod.render.gpu.GuiElementRenderState;
import org.joml.Matrix3x2f;

final class GlassState implements GuiElementRenderState {
    private final GlassQuad glass;
    private final Matrix3x2f pose;
    private final ScreenRectangle scissorArea;
    private final ScreenRectangle bounds;

    GlassState(Matrix3x2f pose, GlassQuad glass, ScreenRectangle scissorArea) {
        this.glass = glass;
        this.pose = pose;
        this.scissorArea = scissorArea;
        ScreenRectangle transformedBounds = new ScreenRectangle(
                Math.round(glass.x()),
                Math.round(glass.y()),
                Math.round(glass.width()),
                Math.round(glass.height())
        ).transformMaxBounds(this.pose);
        this.bounds = scissorArea == null ? transformedBounds : scissorArea.intersection(transformedBounds);
    }

    @Override
    public void buildVertices(GuiVertexConsumer consumer) {
        int batchIndex = GlassRenderer.getInstance().reserve(glass);
        if (batchIndex < 0) {
            return;
        }

        float x0 = glass.x();
        float y0 = glass.y();
        float x1 = glass.x() + glass.width();
        float y1 = glass.y() + glass.height();

        vertex(consumer, x0, y0, batchIndex);
        vertex(consumer, x0, y1, batchIndex);
        vertex(consumer, x1, y1, batchIndex);
        vertex(consumer, x1, y0, batchIndex);
    }

    private void vertex(GuiVertexConsumer consumer, float x, float y, int batchIndex) {
        consumer.addVertexWith2DPose(pose, x, y)
                .setColor(glass.color())
                .setLineWidth((float) (batchIndex + 1));
    }

    @Override
    public RenderPipeline pipeline() {
        return GlassRenderer.GLASS_PIPELINE;
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
