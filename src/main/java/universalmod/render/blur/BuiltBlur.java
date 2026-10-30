package universalmod.render.blur;

import net.minecraft.client.gui.DrawContext;
import universalmod.render.color.ColorUtil;

public record BuiltBlur(
        float x, float y, float width, float height,
        float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
        float smoothness, float blurRadius,
        int colorTopLeft, int colorTopRight, int colorBottomRight, int colorBottomLeft,
        BlurAlgorithm algorithm, float verticalSplit, int splitColor
) {
    private static final int DEFAULT_COLOR = ColorUtil.WHITE;
    public static final float NO_VERTICAL_SPLIT = -1f;

    public BuiltBlur {
        if (algorithm == null) algorithm = BlurAlgorithm.KAWASE;
    }

    public BuiltBlur(float x, float y, float w, float h, float radius, float smoothness, float blurRadius) {
        this(x, y, w, h, radius, radius, radius, radius, smoothness, blurRadius, DEFAULT_COLOR, DEFAULT_COLOR, DEFAULT_COLOR, DEFAULT_COLOR, BlurAlgorithm.KAWASE, NO_VERTICAL_SPLIT, DEFAULT_COLOR);
    }

    public BuiltBlur(float x, float y, float w, float h, float rtl, float rtr, float rbr, float rbl, float smoothness, float blurRadius, int color) {
        this(x, y, w, h, rtl, rtr, rbr, rbl, smoothness, blurRadius, color, color, color, color, BlurAlgorithm.KAWASE, NO_VERTICAL_SPLIT, DEFAULT_COLOR);
    }

    public BuiltBlur(float x, float y, float w, float h, float rtl, float rtr, float rbr, float rbl, float smoothness, float blurRadius, int tl, int tr, int br, int bl) {
        this(x, y, w, h, rtl, rtr, rbr, rbl, smoothness, blurRadius, tl, tr, br, bl, BlurAlgorithm.KAWASE, NO_VERTICAL_SPLIT, DEFAULT_COLOR);
    }

    public BuiltBlur withColor(int color) {
        return withColors(color, color, color, color);
    }

    public BuiltBlur withColors(int tl, int tr, int br, int bl) {
        return new BuiltBlur(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, smoothness, blurRadius, tl, tr, br, bl, algorithm, verticalSplit, splitColor);
    }

    public BuiltBlur withAlgorithm(BlurAlgorithm a) {
        return new BuiltBlur(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, smoothness, blurRadius, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft, a, verticalSplit, splitColor);
    }

    public BuiltBlur withVerticalColorSplit(float splitY, int topColor, int bodyColor) {
        return new BuiltBlur(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, smoothness, blurRadius, bodyColor, bodyColor, bodyColor, bodyColor, algorithm, splitY, topColor);
    }

    public BuiltBlur withoutVerticalColorSplit() {
        return new BuiltBlur(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, smoothness, blurRadius, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft, algorithm, NO_VERTICAL_SPLIT, splitColor);
    }

    public int color() {
        return colorTopLeft;
    }

    public void render(DrawContext graphics) {
        BlurFramebuffer.getInstance().draw(graphics, new BlurPass(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, smoothness, blurRadius, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft, algorithm, verticalSplit, splitColor));
    }

    public boolean visible() {
        int c = colorTopLeft | colorTopRight | colorBottomRight | colorBottomLeft;
        if (verticalSplit >= 0) c |= splitColor;
        return width > 0 && height > 0 && (c >>> 24) != 0 && blurRadius > 0;
    }
}
