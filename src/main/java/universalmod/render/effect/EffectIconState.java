package universalmod.render.effect;

import universalmod.render.gpu.RenderPipeline;
import universalmod.render.gpu.GuiVertexConsumer;
import universalmod.render.gpu.ScreenRectangle;
import universalmod.render.gpu.TextureSetup;
import universalmod.render.gpu.GuiElementRenderState;
import org.joml.Matrix3x2f;

import java.util.ArrayList;
import java.util.List;

final class EffectIconState implements GuiElementRenderState {
    private final Matrix3x2f pose;
    private final EffectIconTexture texture;
    private final List<EffectIconQuad> icons = new ArrayList<>(16);
    private float minX = Float.MAX_VALUE;
    private float minY = Float.MAX_VALUE;
    private float maxX = -Float.MAX_VALUE;
    private float maxY = -Float.MAX_VALUE;

    EffectIconState(Matrix3x2f pose, EffectIconTexture texture) {
        this.pose = pose;
        this.texture = texture;
    }

    void add(EffectIconQuad icon) {
        icons.add(icon);
        minX = Math.min(minX, icon.x());
        minY = Math.min(minY, icon.y());
        maxX = Math.max(maxX, icon.x() + icon.size());
        maxY = Math.max(maxY, icon.y() + icon.size());
    }

    @Override
    public void buildVertices(GuiVertexConsumer consumer) {
        for (EffectIconQuad icon : icons) {
            float x0 = icon.x();
            float y0 = icon.y();
            float x1 = icon.x() + icon.size();
            float y1 = icon.y() + icon.size();
            int color = icon.color();

            vertex(consumer, x0, y0, icon.u0(), icon.v0(), color);
            vertex(consumer, x0, y1, icon.u0(), icon.v1(), color);
            vertex(consumer, x1, y1, icon.u1(), icon.v1(), color);
            vertex(consumer, x1, y0, icon.u1(), icon.v0(), color);
        }
    }

    private void vertex(GuiVertexConsumer consumer, float x, float y, float u, float v, int color) {
        consumer.addVertexWith2DPose(pose, x, y)
                .setUv(u, v)
                .setColor(color);
    }

    @Override
    public RenderPipeline pipeline() {
        return EffectIconRenderer.EFFECT_ICON_PIPELINE;
    }

    @Override
    public TextureSetup textureSetup() {
        return texture.setup();
    }

    @Override
    public ScreenRectangle scissorArea() {
        return null;
    }

    @Override
    public ScreenRectangle bounds() {
        if (icons.isEmpty()) {
            return new ScreenRectangle(0, 0, 1, 1);
        }

        int x = (int) Math.floor(minX);
        int y = (int) Math.floor(minY);
        int width = Math.max(1, (int) Math.ceil(maxX - minX));
        int height = Math.max(1, (int) Math.ceil(maxY - minY));
        return new ScreenRectangle(x, y, width, height).transformMaxBounds(pose);
    }
}
