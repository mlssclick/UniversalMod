package universalmod.gui.clickgui.component.option;

import net.minecraft.client.gui.DrawContext;
import universalmod.gui.clickgui.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import universalmod.gui.clickgui.ClickGuiController;
import universalmod.gui.ui.control.MarqueeTextControl;
import universalmod.gui.ui.control.SmoothSwitchRenderer;
import universalmod.gui.ui.control.SwitchControl;
import universalmod.render.color.ColorUtil;
import universalmod.setting.BooleanSetting;
import universalmod.theme.ThemeColors;

public final class BooleanOption extends ClickGuiOption {
    private static final float ROW_HEIGHT = 15.0F;
    private static final float LABEL_SIZE = OptionStyle.TITLE_SIZE;
    private static final float LABEL_TO_SWITCH_GAP = 6.0F;

    private final SwitchControl toggleControl = new SwitchControl();
    private final BooleanSetting setting;

    private final MarqueeTextControl labelMarquee = new MarqueeTextControl();

    public BooleanOption(BooleanSetting setting) {
        super(setting.getName());
        this.setting = setting;
        toggleControl.reset(setting.getValue());
    }

    @Override
    public float getHeight() {
        return ROW_HEIGHT;
    }

    @Override
    protected void renderControl(DrawContext graphics, float x, float y, float width, float height, float scale, float alpha) {
        boolean enabled = setting.getValue();
        float progress = toggleControl.update(enabled);

        float switchW = SmoothSwitchRenderer.WIDTH * 1.5F * scale;
        float switchH = SmoothSwitchRenderer.HEIGHT * 1.5F * scale;
        float switchX = x + width - switchW;
        float switchY = y + height * 0.5F - switchH * 0.5F;

        float availableTextWidth = Math.max(0.0F, switchX - LABEL_TO_SWITCH_GAP * scale - x);
        float textSize = LABEL_SIZE * scale;
        float textY = y + height * 0.5F - textSize * 0.5F;
        labelMarquee.render(
                graphics,
                displayName(),
                x,
                textY,
                textSize,
                availableTextWidth,
                hovered(ClickGuiController.mouseX(), ClickGuiController.mouseY(), x, y, width, height),
                labelColor(alpha, progress),
                scale
        );

        SmoothSwitchRenderer.renderSized(switchX, switchY, switchW, switchH, alpha, progress);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event == null || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        
        if (!hovered(event.x(), event.y(), x, y, width, height)) {
            return false;
        }
        setting.setValue(!setting.getValue());
        return true;
    }

    private int labelColor(float alpha, float progress) {
        int accent = ColorUtil.withAlpha(ThemeColors.clickGuiAccentColor(255, 1.0F), 255);
        int base = ColorUtil.lerpColor(accent, 0xFFFFFFFF, 0.92F);
        int half = ColorUtil.multAlpha(base, 0.5F);
        int animated = ColorUtil.lerpColor(half, base, clamp(progress, 0.0F, 1.0F));
        return ColorUtil.multAlpha(animated, clamp(alpha, 0.0F, 1.0F));
    }
}
