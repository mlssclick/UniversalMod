package universalmod.render.shape;

import net.minecraft.client.gui.DrawContext;
import universalmod.render.color.ColorUtil;

public record RectangleShape(
        float x,
        float y,
        float width,
        float height,
        float radiusTopLeft,
        float radiusTopRight,
        float radiusBottomRight,
        float radiusBottomLeft,
        int colorTopLeft,
        int colorTopRight,
        int colorBottomRight,
        int colorBottomLeft,
        float smoothness,
        float verticalSplit,
        int splitColor
) {
    public static final float DEFAULT_SMOOTHNESS = 1.0f;
    public static final int DEFAULT_COLOR = ColorUtil.WHITE;
    public static final float NO_VERTICAL_SPLIT = -1.0f;

    public RectangleShape(
            float x,
            float y,
            float width,
            float height,
            float radiusTopLeft,
            float radiusTopRight,
            float radiusBottomRight,
            float radiusBottomLeft,
            int colorTopLeft,
            int colorTopRight,
            int colorBottomRight,
            int colorBottomLeft,
            float smoothness
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
                colorTopLeft,
                colorTopRight,
                colorBottomRight,
                colorBottomLeft,
                smoothness,
                NO_VERTICAL_SPLIT,
                DEFAULT_COLOR
        );
    }

    public RectangleShape(float x, float y, float width, float height, float radius, int color) {
        this(x, y, width, height, radius, radius, radius, radius, color, color, color, color, DEFAULT_SMOOTHNESS);
    }

    public RectangleShape(
            float x,
            float y,
            float width,
            float height,
            float radiusTopLeft,
            float radiusTopRight,
            float radiusBottomRight,
            float radiusBottomLeft,
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
                color,
                color,
                color,
                color,
                DEFAULT_SMOOTHNESS
        );
    }

    public RectangleShape withSmoothness(float smoothness) {
        return new RectangleShape(
                x,
                y,
                width,
                height,
                radiusTopLeft,
                radiusTopRight,
                radiusBottomRight,
                radiusBottomLeft,
                colorTopLeft,
                colorTopRight,
                colorBottomRight,
                colorBottomLeft,
                smoothness,
                verticalSplit,
                splitColor
        );
    }

    public RectangleShape withColor(int color) {
        return new RectangleShape(
                x,
                y,
                width,
                height,
                radiusTopLeft,
                radiusTopRight,
                radiusBottomRight,
                radiusBottomLeft,
                color,
                color,
                color,
                color,
                smoothness,
                verticalSplit,
                splitColor
        );
    }

    public RectangleShape withVerticalColorSplit(float splitY, int topColor, int bodyColor) {
        return new RectangleShape(
                x,
                y,
                width,
                height,
                radiusTopLeft,
                radiusTopRight,
                radiusBottomRight,
                radiusBottomLeft,
                bodyColor,
                bodyColor,
                bodyColor,
                bodyColor,
                smoothness,
                splitY,
                topColor
        );
    }

    public RectangleShape withoutVerticalColorSplit() {
        return new RectangleShape(
                x,
                y,
                width,
                height,
                radiusTopLeft,
                radiusTopRight,
                radiusBottomRight,
                radiusBottomLeft,
                colorTopLeft,
                colorTopRight,
                colorBottomRight,
                colorBottomLeft,
                smoothness,
                NO_VERTICAL_SPLIT,
                splitColor
        );
    }

    public void render(DrawContext graphics) {
        RectangleRenderer.getInstance().draw(graphics, this);
    }

    public boolean visible() {
        int colors = colorTopLeft | colorTopRight | colorBottomRight | colorBottomLeft;
        if (verticalSplit >= 0.0f) {
            colors |= splitColor;
        }
        return width > 0.0f && height > 0.0f && ((colors >>> 24) != 0);
    }
}
