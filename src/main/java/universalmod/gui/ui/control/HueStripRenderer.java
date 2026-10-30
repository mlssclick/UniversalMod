package universalmod.gui.ui.control;

import universalmod.render.Render2D;
import universalmod.render.color.ColorUtil;

public final class HueStripRenderer {
    private static final String HORIZONTAL = "universalmod:textures/ui/color_picker/hue_horizontal.png";
    private static final String VERTICAL = "universalmod:textures/ui/color_picker/hue_vertical.png";

    private HueStripRenderer() {}

    public static void render(float x, float y, float width, float height, float radius,
                              float opacity, boolean vertical) {
        if (!Float.isFinite(opacity) || width <= 0f || height <= 0f || opacity <= 0f) return;
        int alpha = Math.round(255f * Math.min(1f, opacity));
        Render2D.image(vertical ? VERTICAL : HORIZONTAL, x, y, width, height, radius,
                ColorUtil.rgba(255, 255, 255, alpha));
    }
}
