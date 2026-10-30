package universalmod.gui.clickgui.component.option;

import universalmod.render.Render2D;
import universalmod.render.color.ColorUtil;
import universalmod.render.font.FontType;


public final class OptionStyle {
    public static final float TITLE_SIZE = 9.15f;
    public static final float DESCRIPTION_SIZE = 5.50f;
    public static final float INLINE_OPTION_HEIGHT = 22.0f;
    public static final float INLINE_CONTROL_HEIGHT = 13.0f;
    public static final float INLINE_RIGHT_GAP = 0.0f;

    public static final float SEGMENT_TITLE_SIZE = 9.15f;
    public static final float SEGMENT_TOP = 16.5f;
    public static final float SEGMENT_HEIGHT = 13.0f;
    public static final float SEGMENT_GAP = 2.0f;

    public static final float COLOR_KNOB = 12.0f;

    private OptionStyle() {
    }


    public static float circleRadius(float diameter, float scale) {
        return Math.max(0.0f, diameter * 0.5f - Math.max(0.0f, scale));
    }

    public static float centeredTextY(FontType font, String text, float size, float boxY, float boxHeight) {
        if (text == null) text = "";
        Render2D.TextVisualBounds bounds = Render2D.textVisualBounds(font, text, size);
        if (bounds == null || bounds.empty()) {
            return boxY + (boxHeight - size) * 0.5f;
        }
        return boxY + boxHeight * 0.5f - bounds.centerY();
    }


    public static int white(float alpha) {
        return ColorUtil.rgba(247, 245, 250, Math.round(255.0f * alpha));
    }

    public static int muted(float alpha) {
        return ColorUtil.rgba(157, 153, 165, Math.round(242.0f * alpha));
    }
}
