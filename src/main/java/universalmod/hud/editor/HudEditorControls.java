package universalmod.hud.editor;

import universalmod.gui.ui.control.SmoothSwitchRenderer;
import universalmod.gui.ui.control.SmoothFloatSliderRenderer;
import universalmod.module.render.Hud;
import universalmod.render.Render2D;
import universalmod.render.color.ColorUtil;
import universalmod.render.font.FontType;
import universalmod.theme.ThemeColors;

public final class HudEditorControls {
    public static final float PANEL_WIDTH = 301.0F;
    public static final float HEADER_HEIGHT = 73.0F;
    public static final float PADDING = 16.0F;
    public static final float ROW_GAP = 16.0F;
    public static final float CHECKBOX_SIZE = 16.0F;
    public static final float SEGMENT_HEIGHT = 32.0F;
    private static final float NATIVE_SCALE = 0.5F;

    private HudEditorControls() {
    }

    public static float scale() {
        return NATIVE_SCALE;
    }

    public static float u(float value) {
        return value * NATIVE_SCALE;
    }

    public static float panelWidth() {
        return u(PANEL_WIDTH);
    }

    public static void panel(float x, float y, float height, float alpha) {
        Hud.renderHudBackground(x, y, panelWidth(), height, u(14.0F), u(14.0F), u(1.0F),
                ColorUtil.rgba(12, 14, 18, Math.round(238.0F * clamp01(alpha))));
    }

    public static void divider(float x, float y, float alpha) {
        Render2D.rect(x + u(PADDING), y, panelWidth() - u(PADDING * 2.0F), u(1.0F), u(0.5F),
                ColorUtil.rgba(255, 255, 255, Math.round(18.0F * clamp01(alpha))));
    }

    public static void checkbox(float x, float y, float alpha, float progress) {
        
        
        float width = SmoothSwitchRenderer.WIDTH;
        float height = SmoothSwitchRenderer.HEIGHT;
        float centeredY = y + (u(CHECKBOX_SIZE) - height) * 0.5F;
        SmoothSwitchRenderer.renderSized(x, centeredY, width, height, alpha, progress);
    }

    public static void contextMenuSuccess(float x, float y, float width, float height, float alpha) {
        int a = Math.round(255.0F * clamp01(alpha));
        if (a <= 0) {
            return;
        }
        Render2D.image("hud/dropdown_success", x, y, width, height, 0.0F,
                ThemeColors.hudAccentColor(a));
    }

    public static SliderBounds slider(String label, float x, float y, float width, float alpha, float progress) {
        return slider(label, x, y, width, alpha, progress, 0.0F);
    }

    public static SliderBounds slider(String label, float x, float y, float width, float alpha, float progress, float press) {
        String safe = label == null ? "" : label;
        float labelSize = u(14.0F);
        Render2D.text(FontType.BOLD, safe, x, visualCenteredTextY(safe, labelSize, y, u(14.0F)), labelSize,
                ColorUtil.rgba(230, 234, 240, Math.round(255.0F * clamp01(alpha))));
        float trackY = y + u(14.0F + 10.0F);
        SmoothFloatSliderRenderer.Layout bounds = SmoothFloatSliderRenderer.render(x, trackY, width, 1.0F, alpha, progress, press);
        return new SliderBounds(bounds.track().x(), bounds.track().y(), bounds.track().width(),
                bounds.hit().x(), bounds.hit().y(), bounds.hit().width(), bounds.hit().height(), bounds.knobSize());
    }

    public static SliderBounds sliderBounds(String label, float x, float y, float width) {
        float trackY = y + u(14.0F + 10.0F);
        SmoothFloatSliderRenderer.Layout bounds = SmoothFloatSliderRenderer.layout(x, trackY, width, 1.0F);
        return new SliderBounds(bounds.track().x(), bounds.track().y(), bounds.track().width(),
                bounds.hit().x(), bounds.hit().y(), bounds.hit().width(), bounds.hit().height(), bounds.knobSize());
    }

    public static float sliderHeight(String label) {
        return u(14.0F + 10.0F) + 7.0F;
    }

    public static SegmentBounds segmented(String label, String left, String right, float x, float y, float width,
                                          float alpha, float progress, boolean rightSelected) {
        String safeLabel = label == null ? "" : label;
        float labelSize = u(14.0F);
        Render2D.text(FontType.BOLD, safeLabel, x, visualCenteredTextY(safeLabel, labelSize, y, u(14.0F)), labelSize,
                ColorUtil.rgba(230, 234, 240, Math.round(255.0F * clamp01(alpha))));
        float top = y + u(14.0F + 10.0F);
        float height = u(32.0F);
        float a = clamp01(alpha);
        float radius = u(8.0F);
        if (Hud.isBlurEnabled()) {
            Render2D.blur(x, top, width, height, radius, 4.5F, 0.72F,
                    ColorUtil.rgba(255, 255, 255, Math.round(210.0F * a)));
            Render2D.rect(x, top, width, height, radius,
                    ColorUtil.rgba(18, 20, 25, Math.round(132.0F * a)));
        } else {
            Render2D.rect(x, top, width, height, radius,
                    ColorUtil.rgba(29, 31, 36, Math.round(245.0F * a)));
        }
        float segment = width * 0.5F;
        float pillWidth = segment - u(5.66F);
        float centerLeft = x + segment * 0.5F;
        float centerRight = x + segment * 1.5F;
        float center = centerLeft + (centerRight - centerLeft) * clamp01(progress);
        float pillX = center - pillWidth * 0.5F;
        float pillY = top + u(3.0F);
        float pillH = u(26.0F);
        if (Hud.isBlurEnabled()) {
            Render2D.blur(pillX, pillY, pillWidth, pillH, radius, 4.5F, 0.70F,
                    ColorUtil.rgba(255, 255, 255, Math.round(185.0F * a)));
            Render2D.rect(pillX, pillY, pillWidth, pillH, radius,
                    ColorUtil.rgba(8, 10, 14, Math.round(174.0F * a)));
        } else {
            Render2D.rect(pillX, pillY, pillWidth, pillH, radius,
                    ColorUtil.rgba(10, 12, 16, Math.round(250.0F * a)));
        }
        float textSize = u(12.0F);
        float leftY = visualCenteredTextY(left, textSize, top, height);
        float rightY = visualCenteredTextY(right, textSize, top, height);
        float leftWidth = Render2D.textWidth(FontType.BOLD, left, textSize);
        float rightWidth = Render2D.textWidth(FontType.BOLD, right, textSize);
        Render2D.text(FontType.BOLD, left, centerLeft - leftWidth * 0.5F, leftY, textSize,
                ColorUtil.rgba(rightSelected ? 136 : 244, rightSelected ? 142 : 246, rightSelected ? 152 : 248,
                        Math.round(255.0F * clamp01(alpha))));
        Render2D.text(FontType.BOLD, right, centerRight - rightWidth * 0.5F, rightY, textSize,
                ColorUtil.rgba(rightSelected ? 244 : 136, rightSelected ? 246 : 142, rightSelected ? 248 : 152,
                        Math.round(255.0F * clamp01(alpha))));
        return new SegmentBounds(x, top, width, height);
    }

    public static float segmentedHeight(String label) {
        return u(14.0F + 10.0F + 32.0F);
    }

    public static float visualCenteredTextY(String text, float size, float boxY, float boxHeight) {
        String safe = text == null ? "" : text;
        Render2D.TextVisualBounds bounds = Render2D.textVisualBounds(FontType.BOLD, safe, size);
        if (bounds == null || bounds.empty()) {
            return boxY + (boxHeight - size) * 0.5F;
        }
        return boxY + boxHeight * 0.5F - bounds.centerY();
    }

    public static int lerpColor(int a, int b, float t) {
        float p = clamp01(t);
        int aa = (a >>> 24) & 255;
        int ar = (a >>> 16) & 255;
        int ag = (a >>> 8) & 255;
        int ab = a & 255;
        int ba = (b >>> 24) & 255;
        int br = (b >>> 16) & 255;
        int bg = (b >>> 8) & 255;
        int bb = b & 255;
        return (Math.round(aa + (ba - aa) * p) << 24)
                | (Math.round(ar + (br - ar) * p) << 16)
                | (Math.round(ag + (bg - ag) * p) << 8)
                | Math.round(ab + (bb - ab) * p);
    }

    public static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    public static float sliderProgressFromMouse(double mouseX, SliderBounds bounds) {
        if (bounds == null) {
            return 0.0F;
        }
        return SmoothFloatSliderRenderer.progressFromMouse(mouseX, bounds.x(), bounds.width(), 1.0F);
    }

    public record SliderBounds(float x, float trackY, float width, float hitX, float hitY,
                               float hitWidth, float hitHeight, float knobSize) {
    }

    public record SegmentBounds(float x, float y, float width, float height) {
    }
}
