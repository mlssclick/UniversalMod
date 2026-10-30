package universalmod.gui.clickgui;

import universalmod.render.Render2D;
import universalmod.render.shape.OutlineShape;

final class ClickGuiOutline {
    private static final float SMOOTHNESS = 1.55F;

    private ClickGuiOutline() {
    }

    static void draw(float x, float y, float width, float height, float radius, float thickness, int color) {
        Render2D.outline(new OutlineShape(
                x, y, width, height, radius, Math.max(0.72F, thickness), color
        ).withSmoothness(SMOOTHNESS));
    }
}
