package universalmod.gui.clickgui.component.option;

import net.minecraft.client.gui.DrawContext;
import universalmod.gui.clickgui.MouseButtonEvent;
import universalmod.gui.ui.control.ColorPickerGeometry;
import universalmod.gui.ui.control.ColorPickerInteraction;
import universalmod.gui.ui.control.ColorPickerRenderer;
import universalmod.gui.ui.control.ColorSwatchRenderer;
import universalmod.render.Render2D;
import universalmod.render.animation.Easings;
import universalmod.render.animation.ReversibleAnimationTimeline;
import universalmod.render.color.ColorUtil;
import universalmod.render.font.FontType;
import universalmod.render.surface.GuiSurface;
import universalmod.setting.ColorSetting;
import universalmod.setting.SliderSetting;
import universalmod.theme.ThemeColors;

import java.awt.Color;


public class ColorOption extends ClickGuiOption {
    private static final float POPUP_WIDTH = 105.0F;
    private static final float POPUP_HEIGHT = 98.0F;

    private final ReversibleAnimationTimeline openAnimation = new ReversibleAnimationTimeline();
    private final ColorSetting setting;
    private final SliderSetting linkedAlphaSetting;
    private final ColorPickerInteraction picker = new ColorPickerInteraction();

    private boolean open;
    private float swatchX;
    private float swatchY;
    private float swatchSize;
    private float popupX;
    private float popupY;
    private float popupWidth;
    private float popupHeight;

    public ColorOption(ColorSetting setting) {
        this(setting, null);
    }

    public ColorOption(ColorSetting setting, SliderSetting linkedAlphaSetting) {
        super(setting.getName());
        this.setting = setting;
        this.linkedAlphaSetting = linkedAlphaSetting;
        syncPickerFromSetting();
        openAnimation.set(0.0D);
    }

    @Override
    public float getHeight() {
        return OptionStyle.INLINE_OPTION_HEIGHT;
    }

    @Override
    protected void renderControl(DrawContext graphics, float x, float y, float width, float height, float scale, float alpha) {
        if (!picker.dragging()) {
            syncPickerFromSetting();
        }

        swatchSize = OptionStyle.COLOR_KNOB * scale;
        swatchX = x + width - swatchSize;
        swatchY = y + (height - swatchSize) * 0.5F;

        float titleW = Math.max(1.0F, swatchX - x - 10.0F * scale);
        float titleSize = OptionStyle.TITLE_SIZE * scale;
        float titleY = OptionStyle.centeredTextY(FontType.BOLD, displayName(), titleSize, y, height);
        renderScrollingText(graphics, "color:title", displayName(), x, titleY, titleSize,
                color(241, 239, 246, 255, alpha),
                x, y, titleW, height,
                x, y, titleW, height);

        picker.animate();
        Color swatchColor = picker.model().color();
        ColorSwatchRenderer.render(swatchX, swatchY, swatchSize, swatchColor, alpha);
    }

    @Override
    public void renderOverlay(DrawContext graphics) {
        openAnimation.update();
        float progress = openAnimation.get();
        if (progress <= 0.01F || !popupAnchorVisible()) {
            return;
        }

        float popupAlpha = alpha * progress;
        popupWidth = POPUP_WIDTH * scale;
        popupHeight = POPUP_HEIGHT * scale;
        updatePopupBounds();
        picker.geometry(ColorPickerGeometry.clickGui(popupX, popupY, scale));

        float radius = 3.2F * scale;
        Render2D.rect(popupX, popupY, popupWidth, popupHeight, radius,
                color(8, 8, 11, 255, popupAlpha));
        new GuiSurface(
                popupX, popupY, popupWidth, popupHeight, radius,
                popupAlpha, 0.22F,
                ColorUtil.rgba(9, 9, 12, 255),
                ThemeColors.clickGuiAccentColor(255, 1.0F)
        ).render(graphics);
        Render2D.outline(popupX, popupY, popupWidth, popupHeight, radius, 1.05F * scale,
                color(6, 6, 9, 238, popupAlpha));
        picker.animate();
        ColorPickerRenderer.render(picker.geometry(), picker.visual(), popupAlpha, scale);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event == null || event.button() != 0) {
            return false;
        }

        double mouseX = event.x();
        double mouseY = event.y();
        if (isPopupVisible() && popupAnchorVisible()) {
            popupWidth = POPUP_WIDTH * scale;
            popupHeight = POPUP_HEIGHT * scale;
            updatePopupBounds();
            picker.geometry(ColorPickerGeometry.clickGui(popupX, popupY, scale));
            if (picker.press(mouseX, mouseY, ColorPickerInteraction.HueAxis.HORIZONTAL, 0.0F, this::applyPickerColor)) {
                return true;
            }
        }

        if (hovered(mouseX, mouseY,
                swatchX - 3.0F * scale, swatchY - 3.0F * scale,
                swatchSize + 6.0F * scale, swatchSize + 6.0F * scale)) {
            setOpen(!open);
            return true;
        }

        if (open) {
            setOpen(false);
        }
        return false;
    }

    @Override
    public boolean wantsMousePriority(MouseButtonEvent event) {
        return event != null && popupAnchorVisible() && isPopupVisible()
                && hovered(event.x(), event.y(), popupX, popupY, popupWidth, popupHeight);
    }

    @Override
    public boolean hasOpenPopup() {
        return isPopupVisible();
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (!picker.dragging()) {
            return false;
        }
        picker.cancelDrag();
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (event == null || !picker.dragging()) {
            return false;
        }
        picker.drag(event.x(), event.y(), ColorPickerInteraction.HueAxis.HORIZONTAL, this::applyPickerColor);
        return true;
    }

    @Override
    public void closePopup() {
        picker.cancelDrag();
        if (open) {
            setOpen(false);
        }
    }

    private void updatePopupBounds() {
        float margin = 4.0F * scale;
        float gap = 9.1F * scale;
        float screenWidth = popupScreenWidth();
        float screenHeight = popupScreenHeight();
        float rightX = x + width + gap;
        float leftX = x - popupWidth - gap;
        float maxX = Math.max(margin, screenWidth - popupWidth - margin);
        if (rightX + popupWidth <= screenWidth - margin) {
            popupX = rightX;
        } else if (leftX >= margin) {
            popupX = leftX;
        } else {
            popupX = clamp(rightX, margin, maxX);
        }
        float centeredY = y + height * 0.5F - popupHeight * 0.5F;
        float maxY = Math.max(margin, screenHeight - popupHeight - margin);
        popupY = clamp(centeredY, margin, maxY);
    }

    private void syncPickerFromSetting() {
        Color value = new Color(setting.getValue(), true);
        picker.model().sync(value, readAlphaValue(value));
    }

    private float readAlphaValue(Color color) {
        if (linkedAlphaSetting == null) {
            return color.getAlpha() / 255.0F;
        }
        double min = linkedAlphaSetting.getMin();
        double max = linkedAlphaSetting.getMax();
        if (max <= min) {
            return 1.0F;
        }
        return clamp((float) ((linkedAlphaSetting.getValue() - min) / (max - min)), 0.0F, 1.0F);
    }

    private void applyPickerColor(Color color) {
        if (color == null) {
            return;
        }
        if (linkedAlphaSetting == null) {
            setting.setValue(color.getRGB());
            return;
        }
        setting.setValue(new Color(color.getRed(), color.getGreen(), color.getBlue(), 255).getRGB());
        double min = linkedAlphaSetting.getMin();
        double max = linkedAlphaSetting.getMax();
        linkedAlphaSetting.setValue(min + (max - min) * (color.getAlpha() / 255.0D));
    }

    private boolean isPopupVisible() {
        return open || openAnimation.get() > 0.01F || openAnimation.isAlive();
    }

    private void setOpen(boolean open) {
        if (this.open == open) {
            return;
        }
        this.open = open;
        if (!open) {
            picker.cancelDrag();
        }
        openAnimation.run(open ? 1.0D : 0.0D, 0.16D, Easings.CUBIC_OUT);
    }

    @Override
    protected int color(int red, int green, int blue, int alpha, float alphaMultiplier) {
        return ColorUtil.rgba(red, green, blue, (int) (alpha * alphaMultiplier));
    }
}
