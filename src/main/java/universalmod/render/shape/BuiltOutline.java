package universalmod.render.shape;

import net.minecraft.client.gui.DrawContext;
import universalmod.render.color.ColorUtil;

public record BuiltOutline(
        float x, float y, float width, float height,
        float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
        float thickness, int colorTopLeft, int colorTopRight, int colorBottomRight, int colorBottomLeft,
        float smoothness
) {
    public static final float DEFAULT_SMOOTHNESS = 1.0f;
    public static final int DEFAULT_COLOR = ColorUtil.WHITE;

    public BuiltOutline(float x, float y, float w, float h, float radius, float thickness, int color) {
        this(x, y, w, h, radius, radius, radius, radius, thickness, color, color, color, color, DEFAULT_SMOOTHNESS);
    }

    public BuiltOutline(float x, float y, float w, float h, float rtl, float rtr, float rbr, float rbl, float thickness, int color) {
        this(x, y, w, h, rtl, rtr, rbr, rbl, thickness, color, color, color, color, DEFAULT_SMOOTHNESS);
    }

    public BuiltOutline withSmoothness(float v) {
        return new BuiltOutline(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, thickness, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft, v);
    }

    public BuiltOutline withColors(int tl, int tr, int br, int bl) {
        return new BuiltOutline(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, thickness, tl, tr, br, bl, smoothness);
    }

    public void render(DrawContext graphics) {
        DefaultOutlineRenderer.getInstance().draw(graphics, this);
    }

    public boolean visible() {
        return width > 0 && height > 0 && thickness > 0 && (((colorTopLeft | colorTopRight | colorBottomRight | colorBottomLeft) >>> 24) != 0);
    }
}
