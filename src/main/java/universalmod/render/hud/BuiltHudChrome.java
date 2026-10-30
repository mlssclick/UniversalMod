package universalmod.render.hud;

import net.minecraft.client.gui.DrawContext;

public record BuiltHudChrome(float x, float y, float width, float height, float radiusTopLeft, float radiusTopRight,
                             float radiusBottomRight, float radiusBottomLeft, float alpha, float smoothness,
                             float darkness) {
    public BuiltHudChrome(float x, float y, float w, float h, float radius, float alpha) {
        this(x, y, w, h, radius, radius, radius, radius, alpha, .85f, .92f);
    }

    public BuiltHudChrome(float x, float y, float w, float h, float radius, float alpha, float smoothness, float darkness) {
        this(x, y, w, h, radius, radius, radius, radius, alpha, smoothness, darkness);
    }

    public boolean visible() {
        return width > 0 && height > 0 && alpha > .001f;
    }

    public void render(DrawContext g) {
        HudChromeRenderer.getInstance().draw(g, this);
    }
}
