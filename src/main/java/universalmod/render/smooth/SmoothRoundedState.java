package universalmod.render.smooth;

import universalmod.render.gpu.RenderPipeline;
import universalmod.render.gpu.GuiVertexConsumer;
import net.minecraft.client.MinecraftClient;
import universalmod.render.gpu.ScreenRectangle;
import universalmod.render.gpu.TextureSetup;
import universalmod.render.gpu.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;


final class SmoothRoundedState implements GuiElementRenderState {
    private static final float RADIUS_SCALE = 16.0F;
    private static final float FILL_TYPE_SIGNAL = -1.0F;

    private final SmoothRoundedShape shape;
    private final Matrix3x2f pose;
    private final ScreenRectangle scissorArea;
    private final ScreenRectangle bounds;
    private final int encodedRadius;

    SmoothRoundedState(Matrix3x2f pose, SmoothRoundedShape shape, ScreenRectangle scissorArea) {
        this.shape = shape;
        this.pose = pose;
        this.scissorArea = scissorArea;
        this.bounds = transformedBounds(shape, pose, scissorArea);

        MinecraftClient minecraft = MinecraftClient.getInstance();
        float guiScale = minecraft == null || minecraft.getWindow() == null
                ? 1.0F
                : (float) minecraft.getWindow().getScaleFactor();
        
        float pixelScale = guiScale * pose.m00();
        this.encodedRadius = clampShort(Math.round(Math.max(0.0F, shape.radius()) * pixelScale * RADIUS_SCALE));
    }

    @Override
    public void buildVertices(GuiVertexConsumer consumer) {
        float x0 = shape.x();
        float y0 = shape.y();
        float x1 = x0 + shape.width();
        float y1 = y0 + shape.height();

        vertex(consumer, x0, y0, 0.0F, 0.0F);
        vertex(consumer, x0, y1, 0.0F, 1.0F);
        vertex(consumer, x1, y1, 1.0F, 1.0F);
        vertex(consumer, x1, y0, 1.0F, 0.0F);
    }

    private void vertex(GuiVertexConsumer consumer, float x, float y, float u, float v) {
        consumer.addVertexWith2DPose(pose, x, y)
                .setColor(shape.color())
                .setUv(u, v)
                .setUv1(encodedRadius, encodedRadius)
                .setUv2(encodedRadius, encodedRadius)
                .setNormal(FILL_TYPE_SIGNAL, 0.0F, 0.0F)
                .setLineWidth(0.0F);
    }

    @Override
    public RenderPipeline pipeline() {
        return SmoothRoundedRenderer.PIPELINE;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    @Override
    public ScreenRectangle scissorArea() {
        return scissorArea;
    }

    @Override
    public ScreenRectangle bounds() {
        return bounds;
    }

    private static ScreenRectangle transformedBounds(SmoothRoundedShape shape, Matrix3x2f pose, ScreenRectangle scissor) {
        int left = (int) Math.floor(shape.x());
        int top = (int) Math.floor(shape.y());
        int right = left + Math.max(1, (int) Math.ceil(shape.x() + shape.width()) - left);
        int bottom = top + Math.max(1, (int) Math.ceil(shape.y() + shape.height()) - top);

        Vector2f point = new Vector2f();
        pose.transformPosition(left, top, point);
        float minX = point.x;
        float maxX = point.x;
        float minY = point.y;
        float maxY = point.y;

        pose.transformPosition(right, top, point);
        minX = Math.min(minX, point.x);
        maxX = Math.max(maxX, point.x);
        minY = Math.min(minY, point.y);
        maxY = Math.max(maxY, point.y);

        pose.transformPosition(left, bottom, point);
        minX = Math.min(minX, point.x);
        maxX = Math.max(maxX, point.x);
        minY = Math.min(minY, point.y);
        maxY = Math.max(maxY, point.y);

        pose.transformPosition(right, bottom, point);
        minX = Math.min(minX, point.x);
        maxX = Math.max(maxX, point.x);
        minY = Math.min(minY, point.y);
        maxY = Math.max(maxY, point.y);

        ScreenRectangle transformed = new ScreenRectangle(
                (int) Math.floor(minX),
                (int) Math.floor(minY),
                (int) Math.ceil(maxX - minX),
                (int) Math.ceil(maxY - minY)
        );
        return scissor == null ? transformed : scissor.intersection(transformed);
    }

    private static int clampShort(int value) {
        return Math.max(0, Math.min(32767, value));
    }
}
