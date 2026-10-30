package universalmod.gui.clickgui.component.option;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import universalmod.render.Render2D;
import universalmod.gui.ui.control.Bounds;
import universalmod.gui.ui.control.SegmentGridLayout;
import universalmod.render.animation.Easings;
import universalmod.render.animation.ReversibleAnimationTimeline;
import universalmod.render.color.ColorUtil;
import universalmod.render.font.FontType;
import universalmod.render.surface.GuiSurface;
import universalmod.setting.MultiModeSetting;
import universalmod.theme.ThemeColors;


public class MultiOption extends ClickGuiOption {
    private static final int MAX_COLUMNS = 4;

    private final MultiModeSetting setting;
    private final String[] values;
    private final ReversibleAnimationTimeline[] animations;
    private java.util.List<Bounds> hitCells = java.util.List.of();

    public MultiOption(MultiModeSetting setting) {
        super(setting.getName());
        this.setting = setting;
        this.values = setting.getModes().toArray(String[]::new);
        this.animations = new ReversibleAnimationTimeline[values.length];
        for (int i = 0; i < values.length; i++) {
            animations[i] = new ReversibleAnimationTimeline();
            animations[i].set(setting.isSelected(values[i]) ? 1.0 : 0.0);
        }
    }

    @Override
    public float getHeight() {
        return SegmentGridLayout.controlHeight(values.length, MAX_COLUMNS,
                OptionStyle.SEGMENT_TOP, OptionStyle.SEGMENT_HEIGHT, OptionStyle.SEGMENT_GAP);
    }

    @Override
    protected void renderControl(GuiGraphics graphics, float x, float y, float width, float height, float scale, float alpha) {
        float titleSize = OptionStyle.SEGMENT_TITLE_SIZE * scale;
        float titleY = OptionStyle.centeredTextY(FontType.BOLD, displayName(), titleSize, y, 13.0f * scale);
        Render2D.text(FontType.BOLD, displayName(), x, titleY, titleSize,
                color(246, 244, 249, 255, alpha));
        if (values.length == 0) return;

        float gap = OptionStyle.SEGMENT_GAP * scale;
        float segmentH = OptionStyle.SEGMENT_HEIGHT * scale;
        float startY = y + OptionStyle.SEGMENT_TOP * scale;
        hitCells = SegmentGridLayout.cells(x, startY, width, segmentH, gap, values.length, MAX_COLUMNS);

        for (int i = 0; i < values.length; i++) {
            Bounds cell = hitCells.get(i);
            float sx = cell.x();
            float sy = cell.y();
            float segmentW = cell.width();
            boolean selected = setting.isSelected(values[i]);
            animations[i].run(selected ? 1.0 : 0.0, 0.15, Easings.CUBIC_OUT, false);
            animations[i].update();
            float p = clamp(animations[i].get(), 0.0f, 1.0f);

            int base = ColorUtil.lerpColor(
                    ColorUtil.rgba(39, 36, 43, Math.round(240.0f * alpha)),
                    ThemeColors.clickGuiAccentColor(Math.round(252.0f * alpha), 1.0f), p);
            float radius = 3.2f * scale;
            new GuiSurface(sx, sy, segmentW, segmentH, radius,
                    alpha, 0.07f + 0.44f * p, base, ThemeColors.clickGuiAccentColor(255, 1.0f)).render(graphics);
            Render2D.outline(sx, sy, segmentW, segmentH, radius, 0.33f * scale,
                    color(118, 110, 126, Math.round(42.0f + 44.0f * p), alpha));

            String display = setting.getDisplayMode(values[i]);
            float textSize = 6.55f * scale;
            renderBouncingCenteredText(graphics, display, sx, sy, segmentW, segmentH, textSize,
                    p > 0.5f ? color(255, 255, 255, 255, alpha) : color(174, 170, 181, 244, alpha),
                    4.0f * scale);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() == 2 && hovered(event.x(), event.y(), x, y, width, height)) {
            setting.reset();
            return true;
        }
        if (event.button() != 0) return false;
        for (int i = 0; i < values.length && i < hitCells.size(); i++) {
            if (hitCells.get(i).contains(event.x(), event.y())) {
                String mode = values[i];
                setting.setSelected(mode, !setting.isSelected(mode));
                return true;
            }
        }
        return false;
    }

}
