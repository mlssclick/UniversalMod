package universalmod.render.surface;

import net.minecraft.client.gui.GuiGraphics;

public record GuiSurface(
        float x, float y, float width, float height,
        float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
        float alpha, float smoothness, float accentStrength,
        int baseColor, int accentColor
) {
    public GuiSurface(float x, float y, float width, float height,
                                 float radius, float alpha, float accentStrength,
                                 int baseColor, int accentColor) {
        this(x, y, width, height, radius, radius, radius, radius,
                alpha, 1.0f, accentStrength, baseColor, accentColor);
    }

    public void render(GuiGraphics graphics) {
        GuiSurfaceRenderer.getInstance().draw(graphics, this);
    }

    public boolean visible() {
        return width > 0.0f && height > 0.0f && alpha > 0.001f;
    }
}
