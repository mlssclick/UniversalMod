package universalmod.hud.interaction;

import universalmod.hud.ElementBounds;
import universalmod.hud.ElementScreen;

public final class HudElementTransform {
    public static final float DEFAULT_MIN_WIDTH = 12.0F;
    public static final float DEFAULT_MIN_HEIGHT = 12.0F;
    public static final float DEFAULT_SCREEN_MARGIN = 4.0F;

    private final float defaultX;
    private final float defaultY;

    private float targetX;
    private float targetY;
    private float x;
    private float y;
    private float visualOffsetX;
    private float visualOffsetY;
    private float width = DEFAULT_MIN_WIDTH;
    private float height = DEFAULT_MIN_HEIGHT;
    private float minWidth = DEFAULT_MIN_WIDTH;
    private float minHeight = DEFAULT_MIN_HEIGHT;
    private float hitExpandLeft;
    private float hitExpandTop;
    private float hitExpandRight;
    private float hitExpandBottom;
    private float screenMarginLeft = DEFAULT_SCREEN_MARGIN;
    private float screenMarginTop = DEFAULT_SCREEN_MARGIN;
    private float screenMarginRight = DEFAULT_SCREEN_MARGIN;
    private float screenMarginBottom = DEFAULT_SCREEN_MARGIN;

    public HudElementTransform(float defaultX, float defaultY) {
        this.defaultX = finiteOrZero(defaultX);
        this.defaultY = finiteOrZero(defaultY);
        this.targetX = this.defaultX;
        this.targetY = this.defaultY;
        this.x = this.defaultX;
        this.y = this.defaultY;
    }

    public float x() { return x + visualOffsetX; }
    public float y() { return y + visualOffsetY; }
    public float baseX() { return x; }
    public float baseY() { return y; }
    public float targetX() { return targetX; }
    public float targetY() { return targetY; }
    public float width() { return width; }
    public float height() { return height; }
    public float visualOffsetX() { return visualOffsetX; }
    public float visualOffsetY() { return visualOffsetY; }

    public float centerX() { return x() + width * 0.5F; }
    public float centerY() { return y() + height * 0.5F; }

    public ElementBounds bounds() {
        return new ElementBounds(x(), y(), width, height);
    }

    public ElementBounds dragBounds() {
        return new ElementBounds(
                x() - hitExpandLeft,
                y() - hitExpandTop,
                width + hitExpandLeft + hitExpandRight,
                height + hitExpandTop + hitExpandBottom
        );
    }

    public boolean containsBounds(float mouseX, float mouseY, float padding) {
        float safePadding = safeExpansion(padding);
        return contains(mouseX, mouseY,
                x() - safePadding,
                y() - safePadding,
                x() + width + safePadding,
                y() + height + safePadding);
    }

    public boolean containsDragBounds(float mouseX, float mouseY, float padding) {
        float safePadding = safeExpansion(padding);
        return contains(mouseX, mouseY,
                x() - hitExpandLeft - safePadding,
                y() - hitExpandTop - safePadding,
                x() + width + hitExpandRight + safePadding,
                y() + height + hitExpandBottom + safePadding);
    }

    public HudElementTransform size(float width, float height) {
        if (Float.isFinite(width)) {
            this.width = Math.max(minWidth, width);
        }
        if (Float.isFinite(height)) {
            this.height = Math.max(minHeight, height);
        }
        return this;
    }

    public HudElementTransform minimumSize(float minWidth, float minHeight) {
        if (Float.isFinite(minWidth)) {
            this.minWidth = Math.max(1.0F, minWidth);
        }
        if (Float.isFinite(minHeight)) {
            this.minHeight = Math.max(1.0F, minHeight);
        }
        return size(width, height);
    }

    public HudElementTransform hitExpansion(float left, float top, float right, float bottom) {
        hitExpandLeft = safeExpansion(left);
        hitExpandTop = safeExpansion(top);
        hitExpandRight = safeExpansion(right);
        hitExpandBottom = safeExpansion(bottom);
        return this;
    }

    public HudElementTransform screenMargins(float left, float top, float right, float bottom) {
        screenMarginLeft = safeExpansion(left);
        screenMarginTop = safeExpansion(top);
        screenMarginRight = safeExpansion(right);
        screenMarginBottom = safeExpansion(bottom);
        return this;
    }

    public HudElementTransform position(float x, float y) {
        if (Float.isFinite(x)) {
            targetX = x;
            this.x = x;
        }
        if (Float.isFinite(y)) {
            targetY = y;
            this.y = y;
        }
        return this;
    }

    public HudElementTransform target(float x, float y) {
        if (Float.isFinite(x)) {
            targetX = x;
        }
        if (Float.isFinite(y)) {
            targetY = y;
        }
        return this;
    }

    public HudElementTransform visualOffset(float offsetX, float offsetY) {
        visualOffsetX = Float.isFinite(offsetX) ? offsetX : 0.0F;
        visualOffsetY = Float.isFinite(offsetY) ? offsetY : 0.0F;
        return this;
    }

    public HudElementTransform clearVisualOffset() {
        visualOffsetX = 0.0F;
        visualOffsetY = 0.0F;
        return this;
    }

    public HudElementTransform resetPosition() {
        return position(defaultX, defaultY);
    }

    public void approachTarget(float speed) {
        float safeSpeed = clamp(speed, 0.0F, 1.0F);
        x = lerp(x, targetX, safeSpeed);
        y = lerp(y, targetY, safeSpeed);
    }

    public void clamp(ElementScreen screen, boolean preserveAnimationTarget) {
        if (screen == null || !screen.valid()) {
            return;
        }
        targetX = clampedX(screen, targetX);
        targetY = clampedY(screen, targetY);
        if (preserveAnimationTarget) {
            x = clampedX(screen, x);
            y = clampedY(screen, y);
        } else {
            x = targetX;
            y = targetY;
        }
    }

    public void commitClamp(ElementScreen screen) {
        clamp(screen, false);
    }

    public float clampedX(ElementScreen screen, float value) {
        if (screen == null || !screen.valid()) {
            return value;
        }
        float minimum = screenMarginLeft + hitExpandLeft;
        float maximum = Math.max(minimum, screen.width() - width - hitExpandRight - screenMarginRight);
        return clamp(value, minimum, maximum);
    }

    public float clampedY(ElementScreen screen, float value) {
        if (screen == null || !screen.valid()) {
            return value;
        }
        float minimum = screenMarginTop + hitExpandTop;
        float maximum = Math.max(minimum, screen.height() - height - hitExpandBottom - screenMarginBottom);
        return clamp(value, minimum, maximum);
    }

    private static boolean contains(float x, float y, float left, float top, float right, float bottom) {
        return x >= left && x <= right && y >= top && y <= bottom;
    }

    private static float finiteOrZero(float value) {
        return Float.isFinite(value) ? value : 0.0F;
    }

    private static float safeExpansion(float value) {
        return Float.isFinite(value) ? Math.max(0.0F, value) : 0.0F;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float lerp(float from, float to, float speed) {
        return from + (to - from) * speed;
    }
}
