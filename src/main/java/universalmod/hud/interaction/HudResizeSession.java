package universalmod.hud.interaction;

import universalmod.hud.ElementBounds;
import universalmod.hud.ElementScreen;
import universalmod.hud.HudStyleOverrides;

public final class HudResizeSession {
    private static final float CORNER_HIT = 7.0F;
    private static final float SCREEN_PADDING = 4.0F;
    private static final Handle[] CORNERS = {Handle.TOP_LEFT, Handle.TOP_RIGHT,
            Handle.BOTTOM_LEFT, Handle.BOTTOM_RIGHT};

    public enum Handle {
        NONE(0, 0),
        TOP_LEFT(-1, -1), TOP_RIGHT(1, -1),
        BOTTOM_LEFT(-1, 1), BOTTOM_RIGHT(1, 1);

        private final int horizontal;
        private final int vertical;

        Handle(int horizontal, int vertical) {
            this.horizontal = horizontal;
            this.vertical = vertical;
        }

        public boolean active() { return this != NONE; }
        public boolean northwestSoutheast() {
            return this == TOP_LEFT || this == BOTTOM_RIGHT;
        }
    }

    public record Result(float x, float y, float width, float height, float sizePercent) { }

    private Handle handle = Handle.NONE;
    private ElementBounds initial;
    private float startMouseX;
    private float startMouseY;
    private float startPercent;

    public static Handle hit(ElementBounds bounds, float mouseX, float mouseY) {
        if (bounds == null || bounds.width() <= 0.0F || bounds.height() <= 0.0F) {
            return Handle.NONE;
        }
        float x = bounds.x();
        float y = bounds.y();
        float right = x + bounds.width();
        float bottom = y + bounds.height();
        Handle nearest = Handle.NONE;
        float bestDistanceSquared = Float.POSITIVE_INFINITY;
        for (Handle corner : CORNERS) {
            float cornerX = corner.horizontal < 0 ? x : right;
            float cornerY = corner.vertical < 0 ? y : bottom;
            float dx = mouseX - cornerX;
            float dy = mouseY - cornerY;
            if (Math.abs(dx) > CORNER_HIT || Math.abs(dy) > CORNER_HIT) {
                continue;
            }
            float squared = dx * dx + dy * dy;
            if (squared < bestDistanceSquared) {
                bestDistanceSquared = squared;
                nearest = corner;
            }
        }
        return nearest;
    }

    public boolean begin(ElementBounds bounds, float mouseX, float mouseY, float sizePercent) {
        Handle selected = hit(bounds, mouseX, mouseY);
        if (!selected.active()) return false;
        handle = selected;
        initial = bounds;
        startMouseX = mouseX;
        startMouseY = mouseY;
        startPercent = Math.max(0.001F, sizePercent);
        return true;
    }

    public Result update(float mouseX, float mouseY, ElementScreen screen) {
        if (!active()) return null;
        float width0 = initial.width();
        float height0 = initial.height();
        float dx = mouseX - startMouseX;
        float dy = mouseY - startMouseY;
        float diagonalSquared = width0 * width0 + height0 * height0;
        float relative = (handle.horizontal * dx * width0 + handle.vertical * dy * height0)
                / diagonalSquared;
        float ratio = 1.0F + relative;
        float minRatio = HudStyleOverrides.SIZE_MIN_PERCENT / startPercent;
        float maxRatio = HudStyleOverrides.SIZE_MAX_PERCENT / startPercent;
        if (screen != null && screen.valid()) {
            float horizontalRoom = handle.horizontal < 0
                    ? initial.x() + width0 - SCREEN_PADDING
                    : screen.width() - SCREEN_PADDING - initial.x();
            float verticalRoom = handle.vertical < 0
                    ? initial.y() + height0 - SCREEN_PADDING
                    : screen.height() - SCREEN_PADDING - initial.y();
            maxRatio = Math.min(maxRatio, Math.max(minRatio, horizontalRoom / width0));
            maxRatio = Math.min(maxRatio, Math.max(minRatio, verticalRoom / height0));
        }
        ratio = clamp(ratio, minRatio, maxRatio);
        float percent = clamp(Math.round(startPercent * ratio),
                HudStyleOverrides.SIZE_MIN_PERCENT, HudStyleOverrides.SIZE_MAX_PERCENT);
        ratio = percent / startPercent;
        float width = width0 * ratio;
        float height = height0 * ratio;
        float x = handle.horizontal < 0 ? initial.x() + width0 - width : initial.x();
        float y = handle.vertical < 0 ? initial.y() + height0 - height : initial.y();
        return new Result(x, y, width, height, percent);
    }

    public boolean active() { return handle.active(); }
    public Handle handle() { return handle; }

    public void end() {
        handle = Handle.NONE;
        initial = null;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
