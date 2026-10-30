package universalmod.hud.interaction;

import universalmod.hud.ElementBounds;
import universalmod.hud.ElementScreen;
import universalmod.hud.HudStyleOverrides;

public final class HudResizeSession {
    private static final float HIT_SIZE = 7.0F;
    private static final float SCREEN_PADDING = 4.0F;
    private static final Handle[] HANDLES = {
            Handle.TOP_LEFT, Handle.TOP_RIGHT, Handle.BOTTOM_LEFT, Handle.BOTTOM_RIGHT,
            Handle.LEFT, Handle.RIGHT, Handle.TOP, Handle.BOTTOM
    };

    public enum Handle {
        NONE(0, 0),
        TOP_LEFT(-1, -1), TOP_RIGHT(1, -1),
        BOTTOM_LEFT(-1, 1), BOTTOM_RIGHT(1, 1),
        LEFT(-1, 0), RIGHT(1, 0), TOP(0, -1), BOTTOM(0, 1);

        private final int horizontal;
        private final int vertical;

        Handle(int horizontal, int vertical) {
            this.horizontal = horizontal;
            this.vertical = vertical;
        }

        public boolean active() { return this != NONE; }
        public boolean northwestSoutheast() { return this == TOP_LEFT || this == BOTTOM_RIGHT; }
        public boolean horizontalOnly() { return vertical == 0 && horizontal != 0; }
        public boolean verticalOnly() { return horizontal == 0 && vertical != 0; }
        public int horizontal() { return horizontal; }
        public int vertical() { return vertical; }
    }

    public record Result(float x, float y, float width, float height, float sizePercent) { }

    private Handle handle = Handle.NONE;
    private ElementBounds initial;
    private float startMouseX;
    private float startMouseY;
    private float startPercent;

    public static Handle hit(ElementBounds bounds, float mouseX, float mouseY) {
        if (bounds == null || !Float.isFinite(mouseX) || !Float.isFinite(mouseY)
                || bounds.width() <= 0.0F || bounds.height() <= 0.0F) return Handle.NONE;
        Handle selected = Handle.NONE;
        float nearest = Float.POSITIVE_INFINITY;
        for (Handle candidate : HANDLES) {
            float x = bounds.x() + bounds.width() * (candidate.horizontal + 1) * 0.5F;
            float y = bounds.y() + bounds.height() * (candidate.vertical + 1) * 0.5F;
            float dx = mouseX - x;
            float dy = mouseY - y;
            if (Math.abs(dx) > HIT_SIZE || Math.abs(dy) > HIT_SIZE) continue;
            float distance = dx * dx + dy * dy;
            if (distance < nearest) {
                nearest = distance;
                selected = candidate;
            }
        }
        return selected;
    }

    public boolean begin(ElementBounds bounds, float mouseX, float mouseY, float sizePercent) {
        Handle selected = hit(bounds, mouseX, mouseY);
        if (!selected.active() || !Float.isFinite(sizePercent) || sizePercent <= 0.0F) return false;
        handle = selected;
        initial = bounds;
        startMouseX = mouseX;
        startMouseY = mouseY;
        startPercent = sizePercent;
        return true;
    }

    public Result update(float mouseX, float mouseY, ElementScreen screen) {
        if (!active() || !Float.isFinite(mouseX) || !Float.isFinite(mouseY)) return null;
        float width0 = initial.width();
        float height0 = initial.height();
        float dx = mouseX - startMouseX;
        float dy = mouseY - startMouseY;
        float ratio;
        if (handle.horizontalOnly()) {
            ratio = 1.0F + handle.horizontal * dx / width0;
        } else if (handle.verticalOnly()) {
            ratio = 1.0F + handle.vertical * dy / height0;
        } else {
            ratio = 1.0F + (handle.horizontal * dx * width0 + handle.vertical * dy * height0)
                    / (width0 * width0 + height0 * height0);
        }
        float minRatio = HudStyleOverrides.SIZE_MIN_PERCENT / startPercent;
        float maxRatio = HudStyleOverrides.SIZE_MAX_PERCENT / startPercent;
        if (screen != null && screen.valid()) {
            float left = initial.x() - SCREEN_PADDING;
            float top = initial.y() - SCREEN_PADDING;
            float right = screen.width() - SCREEN_PADDING - initial.x();
            float bottom = screen.height() - SCREEN_PADDING - initial.y();
            float horizontalRoom = handle.horizontal < 0 ? left + width0
                    : handle.horizontal > 0 ? right : 2.0F * Math.min(left + width0 * 0.5F, right - width0 * 0.5F);
            float verticalRoom = handle.vertical < 0 ? top + height0
                    : handle.vertical > 0 ? bottom : 2.0F * Math.min(top + height0 * 0.5F, bottom - height0 * 0.5F);
            if (horizontalRoom > 0.0F) maxRatio = Math.min(maxRatio, Math.max(minRatio, horizontalRoom / width0));
            if (verticalRoom > 0.0F) maxRatio = Math.min(maxRatio, Math.max(minRatio, verticalRoom / height0));
        }
        ratio = clamp(ratio, minRatio, maxRatio);
        float percent = clamp(Math.round(startPercent * ratio),
                HudStyleOverrides.SIZE_MIN_PERCENT, HudStyleOverrides.SIZE_MAX_PERCENT);
        ratio = percent / startPercent;
        float width = width0 * ratio;
        float height = height0 * ratio;
        float x = initial.x() + (width0 - width) * (handle.horizontal < 0 ? 1.0F : handle.horizontal > 0 ? 0.0F : 0.5F);
        float y = initial.y() + (height0 - height) * (handle.vertical < 0 ? 1.0F : handle.vertical > 0 ? 0.0F : 0.5F);
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
