package universalmod.render.blur;

import universalmod.render.color.ColorUtil;

public record BuiltBlur(float x, float y, float width, float height, float radiusTopLeft, float radiusTopRight,
                        float radiusBottomRight, float radiusBottomLeft, float smoothness, float blurRadius,
                        int colorTopLeft, int colorTopRight, int colorBottomRight, int colorBottomLeft) {
    public BuiltBlur(float x, float y, float width, float height, float radius, float smoothness, float blurRadius) {
        this(x, y, width, height, radius, radius, radius, radius, smoothness, blurRadius, ColorUtil.WHITE, ColorUtil.WHITE, ColorUtil.WHITE, ColorUtil.WHITE);
    }

    public BuiltBlur(float x, float y, float width, float height, float rtl, float rtr, float rbr, float rbl, float smoothness, float blurRadius, int color) {
        this(x, y, width, height, rtl, rtr, rbr, rbl, smoothness, blurRadius, color, color, color, color);
    }

    public BuiltBlur withColor(int color) {
        return new BuiltBlur(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, smoothness, blurRadius, color, color, color, color);
    }

    public BuiltBlur withColors(int tl, int tr, int br, int bl) {
        return new BuiltBlur(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, smoothness, blurRadius, tl, tr, br, bl);
    }

    public int color() {
        return colorTopLeft;
    }
}
