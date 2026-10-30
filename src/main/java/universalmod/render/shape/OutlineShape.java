package universalmod.render.shape;

import net.minecraft.client.gui.GuiGraphics;
import universalmod.render.color.ColorUtil;

public record OutlineShape(
        float x,
        float y,
        float width,
        float height,
        float radiusTopLeft,
        float radiusTopRight,
        float radiusBottomRight,
        float radiusBottomLeft,
        float thickness,
        int colorTopLeft,
        int colorTopRight,
        int colorBottomRight,
        int colorBottomLeft,
        float smoothness
) {
    public static final float DEFAULT_SMOOTHNESS = 1.0f;
    public static final int DEFAULT_COLOR = ColorUtil.WHITE;

    public OutlineShape(float x, float y, float width, float height, float radius, float thickness, int color) {
        this(x, y, width, height, radius, radius, radius, radius, thickness, color, color, color, color, DEFAULT_SMOOTHNESS);
    }

    public OutlineShape(
            float x,
            float y,
            float width,
            float height,
            float radiusTopLeft,
            float radiusTopRight,
            float radiusBottomRight,
            float radiusBottomLeft,
            float thickness,
            int color
    ) {
        this(
                x,
                y,
                width,
                height,
                radiusTopLeft,
                radiusTopRight,
                radiusBottomRight,
                radiusBottomLeft,
                thickness,
                color,
                color,
                color,
                color,
                DEFAULT_SMOOTHNESS
        );
    }

    public OutlineShape withSmoothness(float smoothness) {
        return new OutlineShape(
                x,
                y,
                width,
                height,
                radiusTopLeft,
                radiusTopRight,
                radiusBottomRight,
                radiusBottomLeft,
                thickness,
                colorTopLeft,
                colorTopRight,
                colorBottomRight,
                colorBottomLeft,
                smoothness
        );
    }

    public OutlineShape withColor(int color) {
        return new OutlineShape(
                x,
                y,
                width,
                height,
                radiusTopLeft,
                radiusTopRight,
                radiusBottomRight,
                radiusBottomLeft,
                thickness,
                color,
                color,
                color,
                color,
                smoothness
        );
    }

    public void render(GuiGraphics graphics) {
        OutlineRenderer.getInstance().draw(graphics, this);
    }

    public boolean visible() {
        int colors = colorTopLeft | colorTopRight | colorBottomRight | colorBottomLeft;
        return width > 0.0f && height > 0.0f && thickness > 0.0f && (colors >>> 24) != 0;
    }
}
