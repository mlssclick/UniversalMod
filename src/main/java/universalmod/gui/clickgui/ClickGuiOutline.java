package universalmod.gui.clickgui;

import universalmod.render.Render2D;


final class ClickGuiOutline {
    private static final float SMOOTHNESS = 1.55F;

    private ClickGuiOutline() {
    }

    static void draw(float x, float y, float width, float height, float radius, float thickness, int color) {
        Render2D.outline(x, y, width, height, radius, Math.max(0.72F, thickness), color);
    }
}
