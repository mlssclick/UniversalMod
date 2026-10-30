package universalmod.render.image;

import universalmod.render.color.ColorUtil;

public record BuiltImage(String texture, float x, float y, float size, float explicitWidth, float explicitHeight,
                         float radius, float smoothness, boolean nearestFilter, float rotationDegrees,
                         float rotationOriginX, float rotationOriginY, float u0, float v0, float u1, float v1,
                         int colorTopLeft, int colorTopRight, int colorBottomRight, int colorBottomLeft) {
    public static final int DEFAULT_COLOR = ColorUtil.WHITE;
    public static final float DEFAULT_SMOOTHNESS = 1f;

    public BuiltImage(String texture, float x, float y, float size, float radius) {
        this(texture, x, y, size, 0, 0, radius, DEFAULT_SMOOTHNESS, false, 0, 0, 0, 0, 0, 1, 1, DEFAULT_COLOR, DEFAULT_COLOR, DEFAULT_COLOR, DEFAULT_COLOR);
    }

    public BuiltImage(String texture, float x, float y, float size, float radius, int color) {
        this(texture, x, y, size, 0, 0, radius, DEFAULT_SMOOTHNESS, false, 0, 0, 0, 0, 0, 1, 1, color, color, color, color);
    }

    public BuiltImage(String texture, float x, float y, float w, float h, float radius) {
        this(texture, x, y, 0, w, h, radius, DEFAULT_SMOOTHNESS, false, 0, 0, 0, 0, 0, 1, 1, DEFAULT_COLOR, DEFAULT_COLOR, DEFAULT_COLOR, DEFAULT_COLOR);
    }

    public BuiltImage(String texture, float x, float y, float w, float h, float radius, int color) {
        this(texture, x, y, 0, w, h, radius, DEFAULT_SMOOTHNESS, false, 0, 0, 0, 0, 0, 1, 1, color, color, color, color);
    }

    public boolean visible() {
        return texture != null && !texture.isBlank() && (size > 0 || (explicitWidth > 0 && explicitHeight > 0)) && (((colorTopLeft | colorTopRight | colorBottomRight | colorBottomLeft) >>> 24) != 0);
    }

    public boolean hasExplicitDimensions() {
        return explicitWidth > 0 && explicitHeight > 0;
    }

    public float width() {
        return hasExplicitDimensions() ? explicitWidth : size;
    }

    public float height() {
        return hasExplicitDimensions() ? explicitHeight : size;
    }

    public BuiltImage withColors(int... c) {
        if (c == null || c.length == 0) return this;
        int tl = c[0], tr = c[Math.min(1, c.length - 1)], br = c[Math.min(2, c.length - 1)], bl = c.length >= 4 ? c[3] : c.length == 2 ? c[0] : c[0];
        return new BuiltImage(texture, x, y, size, explicitWidth, explicitHeight, radius, smoothness, nearestFilter, rotationDegrees, rotationOriginX, rotationOriginY, u0, v0, u1, v1, tl, tr, br, bl);
    }

    public BuiltImage withColor(int c) {
        return new BuiltImage(texture, x, y, size, explicitWidth, explicitHeight, radius, smoothness, nearestFilter, rotationDegrees, rotationOriginX, rotationOriginY, u0, v0, u1, v1, c, c, c, c);
    }

    public BuiltImage withRotation(float d, float ox, float oy) {
        return new BuiltImage(texture, x, y, size, explicitWidth, explicitHeight, radius, smoothness, nearestFilter, d, ox, oy, u0, v0, u1, v1, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft);
    }

    public BuiltImage withUv(float a, float b, float c, float d) {
        return new BuiltImage(texture, x, y, size, explicitWidth, explicitHeight, radius, smoothness, nearestFilter, rotationDegrees, rotationOriginX, rotationOriginY, a, b, c, d, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft);
    }

    public BuiltImage withSmoothness(float s) {
        return new BuiltImage(texture, x, y, size, explicitWidth, explicitHeight, radius, s, nearestFilter, rotationDegrees, rotationOriginX, rotationOriginY, u0, v0, u1, v1, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft);
    }

    public BuiltImage withNearestFilter() {
        return new BuiltImage(texture, x, y, size, explicitWidth, explicitHeight, radius, smoothness, true, rotationDegrees, rotationOriginX, rotationOriginY, u0, v0, u1, v1, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft);
    }
}
