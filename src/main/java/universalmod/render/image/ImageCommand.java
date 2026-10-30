package universalmod.render.image;

import universalmod.render.color.ColorUtil;

public record ImageCommand(
        String texture,
        float x,
        float y,
        float size,
        float explicitWidth,
        float explicitHeight,
        float radius,
        float smoothness,
        boolean nearestFilter,
        float rotationDegrees,
        float rotationOriginX,
        float rotationOriginY,
        float u0,
        float v0,
        float u1,
        float v1,
        int colorTopLeft,
        int colorTopRight,
        int colorBottomRight,
        int colorBottomLeft
) {
    public static final int DEFAULT_COLOR = ColorUtil.WHITE;
    public static final float DEFAULT_SMOOTHNESS = 1.0f;

    public ImageCommand(String texture, float x, float y, float size, float radius) {
        this(texture, x, y, size, 0.0f, 0.0f, radius, DEFAULT_SMOOTHNESS, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, DEFAULT_COLOR, DEFAULT_COLOR, DEFAULT_COLOR, DEFAULT_COLOR);
    }

    public ImageCommand(String texture, float x, float y, float size, float radius, int color) {
        this(texture, x, y, size, 0.0f, 0.0f, radius, DEFAULT_SMOOTHNESS, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, color, color, color, color);
    }

    public ImageCommand(String texture, float x, float y, float width, float height, float radius) {
        this(texture, x, y, 0.0f, width, height, radius, DEFAULT_SMOOTHNESS, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, DEFAULT_COLOR, DEFAULT_COLOR, DEFAULT_COLOR, DEFAULT_COLOR);
    }

    public ImageCommand(String texture, float x, float y, float width, float height, float radius, int color) {
        this(texture, x, y, 0.0f, width, height, radius, DEFAULT_SMOOTHNESS, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, color, color, color, color);
    }

    public boolean visible() {
        return texture != null
                && !texture.isBlank()
                && (size > 0.0f || (explicitWidth > 0.0f && explicitHeight > 0.0f))
                && (effectiveAlpha(colorTopLeft) > 0 || effectiveAlpha(colorTopRight) > 0 || effectiveAlpha(colorBottomRight) > 0 || effectiveAlpha(colorBottomLeft) > 0);
    }

    public boolean hasExplicitDimensions() {
        return explicitWidth > 0.0f && explicitHeight > 0.0f;
    }

    public ImageCommand withColors(int... colors) {
        if (colors == null || colors.length == 0) {
            return this;
        }
        if (colors.length == 1) {
            return new ImageCommand(texture, x, y, size, explicitWidth, explicitHeight, radius, smoothness, nearestFilter, rotationDegrees, rotationOriginX, rotationOriginY, u0, v0, u1, v1, colors[0], colors[0], colors[0], colors[0]);
        }
        if (colors.length == 2) {
            return new ImageCommand(texture, x, y, size, explicitWidth, explicitHeight, radius, smoothness, nearestFilter, rotationDegrees, rotationOriginX, rotationOriginY, u0, v0, u1, v1, colors[0], colors[1], colors[1], colors[0]);
        }
        if (colors.length == 3) {
            return new ImageCommand(texture, x, y, size, explicitWidth, explicitHeight, radius, smoothness, nearestFilter, rotationDegrees, rotationOriginX, rotationOriginY, u0, v0, u1, v1, colors[0], colors[1], colors[2], colors[0]);
        }
        return new ImageCommand(texture, x, y, size, explicitWidth, explicitHeight, radius, smoothness, nearestFilter, rotationDegrees, rotationOriginX, rotationOriginY, u0, v0, u1, v1, colors[0], colors[1], colors[2], colors[3]);
    }

    public ImageCommand withColor(int color) {
        return new ImageCommand(texture, x, y, size, explicitWidth, explicitHeight, radius, smoothness, nearestFilter, rotationDegrees, rotationOriginX, rotationOriginY, u0, v0, u1, v1, color, color, color, color);
    }

    public ImageCommand withRotation(float rotationDegrees, float originX, float originY) {
        return new ImageCommand(texture, x, y, size, explicitWidth, explicitHeight, radius, smoothness, nearestFilter, rotationDegrees, originX, originY, u0, v0, u1, v1, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft);
    }

    public ImageCommand withUv(float u0, float v0, float u1, float v1) {
        return new ImageCommand(texture, x, y, size, explicitWidth, explicitHeight, radius, smoothness, nearestFilter, rotationDegrees, rotationOriginX, rotationOriginY, u0, v0, u1, v1, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft);
    }

    public ImageCommand withSmoothness(float smoothness) {
        return new ImageCommand(texture, x, y, size, explicitWidth, explicitHeight, radius, smoothness, nearestFilter, rotationDegrees, rotationOriginX, rotationOriginY, u0, v0, u1, v1, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft);
    }

    public ImageCommand withNearestFilter() {
        return new ImageCommand(texture, x, y, size, explicitWidth, explicitHeight, radius, smoothness, true, rotationDegrees, rotationOriginX, rotationOriginY, u0, v0, u1, v1, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft);
    }

    private static int alpha(int color) {
        return (color >>> 24) & 0xFF;
    }

    private static int effectiveAlpha(int color) {
        return alpha(color);
    }
}
