package universalmod.render.surface;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import org.joml.Matrix3x2f;

final class GuiSurfaceState implements GuiElementRenderState {
    private final GuiSurface surface;
    private final Matrix3x2f pose;
    private final ScreenRectangle scissorArea;
    private final ScreenRectangle bounds;

    GuiSurfaceState(Matrix3x2f pose, GuiSurface surface, ScreenRectangle scissorArea) {
        this.surface = surface;
        this.pose = pose;
        this.scissorArea = scissorArea;
        ScreenRectangle transformed = new ScreenRectangle(
                Math.round(surface.x()), Math.round(surface.y()),
                Math.round(surface.width()), Math.round(surface.height())
        ).transformMaxBounds(pose);
        this.bounds = scissorArea == null ? transformed : scissorArea.intersection(transformed);
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        int batchIndex = GuiSurfaceRenderer.getInstance().reserve(surface);
        if (batchIndex < 0) return;
        vertex(consumer, surface.x(), surface.y(), batchIndex);
        vertex(consumer, surface.x(), surface.y() + surface.height(), batchIndex);
        vertex(consumer, surface.x() + surface.width(), surface.y() + surface.height(), batchIndex);
        vertex(consumer, surface.x() + surface.width(), surface.y(), batchIndex);
    }

    private void vertex(VertexConsumer consumer, float x, float y, int batchIndex) {
        consumer.addVertexWith2DPose(pose, x, y)
                .setColor(0xFFFFFFFF)
                .setLineWidth((float) (batchIndex + 1));
    }

    @Override public RenderPipeline pipeline() { return GuiSurfaceRenderer.PIPELINE; }
    @Override public TextureSetup textureSetup() { return TextureSetup.noTexture(); }
    @Override public ScreenRectangle scissorArea() { return scissorArea; }
    @Override public ScreenRectangle bounds() { return bounds; }
}
