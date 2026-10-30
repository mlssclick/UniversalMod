package universalmod.render.blur;

import net.minecraft.client.gui.DrawContext;
import universalmod.render.color.ColorUtil;

public record BlurPass(
        float x,
        float y,
        float width,
        float height,
        float radiusTopLeft,
        float radiusTopRight,
        float radiusBottomRight,
        float radiusBottomLeft,
        float smoothness,
        float blurRadius,
        int colorTopLeft,
        int colorTopRight,
        int colorBottomRight,
        int colorBottomLeft,
        BlurAlgorithm algorithm,
        float verticalSplit,
        int splitColor
) {
    private static final int DEFAULT_COLOR = ColorUtil.WHITE;
    public static final float NO_VERTICAL_SPLIT = -1.0f;

    public BlurPass {
        if (algorithm == null) {
            algorithm = BlurAlgorithm.KAWASE;
        }
    }

    public BlurPass(
            float x,
            float y,
            float width,
            float height,
            float radiusTopLeft,
            float radiusTopRight,
            float radiusBottomRight,
            float radiusBottomLeft,
            float smoothness,
            float blurRadius,
            int colorTopLeft,
            int colorTopRight,
            int colorBottomRight,
            int colorBottomLeft
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
                smoothness,
                blurRadius,
                colorTopLeft,
                colorTopRight,
                colorBottomRight,
                colorBottomLeft,
                BlurAlgorithm.KAWASE,
                NO_VERTICAL_SPLIT,
                DEFAULT_COLOR
        );
    }

    public BlurPass(float x, float y, float width, float height, float radius, float smoothness, float blurRadius) {
        this(x, y, width, height, radius, radius, radius, radius, smoothness, blurRadius, DEFAULT_COLOR);
    }

    public BlurPass(
            float x,
            float y,
            float width,
            float height,
            float radiusTopLeft,
            float radiusTopRight,
            float radiusBottomRight,
            float radiusBottomLeft,
            float smoothness,
            float blurRadius,
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
                smoothness,
                blurRadius,
                color,
                color,
                color,
                color
        );
    }

    public void render(DrawContext graphics) {
        BlurPass normalized = BlurGeometry.normalize(this);
        if (!normalized.visible()) return;
        universalmod.render.blur.BlurRenderer.blur(
                normalized.x(), normalized.y(), normalized.width(), normalized.height(),
                normalized.radiusTopLeft(), normalized.radiusTopRight(),
                normalized.radiusBottomRight(), normalized.radiusBottomLeft(),
                normalized.blurRadius(), normalized.smoothness(),
                normalized.colorTopLeft(), normalized.colorTopRight(),
                normalized.colorBottomRight(), normalized.colorBottomLeft(),
                normalized.verticalSplit(), normalized.splitColor(), normalized.algorithm());
    }

    public BlurPass withColor(int color) {
        return withColors(color, color, color, color);
    }

    public BlurPass withColors(int colorTopLeft, int colorTopRight, int colorBottomRight, int colorBottomLeft) {
        return new BlurPass(
                x,
                y,
                width,
                height,
                radiusTopLeft,
                radiusTopRight,
                radiusBottomRight,
                radiusBottomLeft,
                smoothness,
                blurRadius,
                colorTopLeft,
                colorTopRight,
                colorBottomRight,
                colorBottomLeft,
                algorithm,
                verticalSplit,
                splitColor
        );
    }

    public BlurPass withAlgorithm(BlurAlgorithm algorithm) {
        return new BlurPass(
                x,
                y,
                width,
                height,
                radiusTopLeft,
                radiusTopRight,
                radiusBottomRight,
                radiusBottomLeft,
                smoothness,
                blurRadius,
                colorTopLeft,
                colorTopRight,
                colorBottomRight,
                colorBottomLeft,
                algorithm,
                verticalSplit,
                splitColor
        );
    }

    public BlurPass withVerticalColorSplit(float splitY, int topColor, int bodyColor) {
        return new BlurPass(
                x,
                y,
                width,
                height,
                radiusTopLeft,
                radiusTopRight,
                radiusBottomRight,
                radiusBottomLeft,
                smoothness,
                blurRadius,
                bodyColor,
                bodyColor,
                bodyColor,
                bodyColor,
                algorithm,
                splitY,
                topColor
        );
    }

    public BlurPass withoutVerticalColorSplit() {
        return new BlurPass(
                x,
                y,
                width,
                height,
                radiusTopLeft,
                radiusTopRight,
                radiusBottomRight,
                radiusBottomLeft,
                smoothness,
                blurRadius,
                colorTopLeft,
                colorTopRight,
                colorBottomRight,
                colorBottomLeft,
                algorithm,
                NO_VERTICAL_SPLIT,
                splitColor
        );
    }

    public int color() {
        return colorTopLeft;
    }

    public boolean visible() {
        int colors = colorTopLeft | colorTopRight | colorBottomRight | colorBottomLeft;
        if (verticalSplit >= 0.0f) {
            colors |= splitColor;
        }
        return width > 0.0f && height > 0.0f && (colors >>> 24) != 0 && blurRadius > 0.0f;
    }
}
