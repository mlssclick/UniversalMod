package universalmod.gui.clickgui.component.option;


import universalmod.service.language.LanguageManager;
import net.minecraft.client.gui.DrawContext;
import universalmod.gui.clickgui.MouseButtonEvent;
import universalmod.render.Render2D;
import universalmod.render.animation.Easings;
import universalmod.render.animation.SmoothAnimation;
import universalmod.render.color.ColorUtil;
import universalmod.render.font.FontType;
import universalmod.render.surface.GuiSurface;
import universalmod.setting.ButtonSetting;
import universalmod.theme.ThemeColors;


public class ButtonOption extends ClickGuiOption {
    private final String action;
    private final SmoothAnimation pressAnimation = new SmoothAnimation();
    private final ButtonSetting setting;
    private float controlX;
    private float controlY;
    private float controlWidth;
    private float controlHeight;

    public ButtonOption(ButtonSetting setting, String action) {
        super(setting.getName());
        this.setting = setting;
        this.action = action == null || action.isBlank() ? "Action" : action;
        pressAnimation.set(1.0);
    }

    @Override
    public float getHeight() {
        return 34.0f;
    }

    @Override
    protected void renderControl(DrawContext graphics, float x, float y, float width, float height, float scale, float alpha) {
        float titleSize = OptionStyle.TITLE_SIZE * scale;
        float titleY = OptionStyle.centeredTextY(FontType.BOLD, displayName(), titleSize, y, 13.0f * scale);
        Render2D.text(FontType.BOLD, displayName(), x, titleY, titleSize,
                color(241, 239, 246, 255, alpha));

        controlX = x;
        controlY = y + 17.0f * scale;
        controlWidth = width;
        controlHeight = 13.0f * scale;

        pressAnimation.update();
        float p = clamp(pressAnimation.get(), 0.0f, 1.0f);
        float rw = controlWidth * (0.96f + 0.04f * p);
        float rh = controlHeight * (0.94f + 0.06f * p);
        float rx = controlX + (controlWidth - rw) * 0.5f;
        float ry = controlY + (controlHeight - rh) * 0.5f;

        new GuiSurface(rx, ry, rw, rh, 3.2f * scale,
                alpha, 0.22f,
                ColorUtil.rgba(42, 36, 49, 255), ThemeColors.clickGuiAccentColor(255, 1.0f)).render(graphics);
        Render2D.outline(rx, ry, rw, rh, 3.2f * scale, 0.34f * scale,
                color(118, 106, 128, 60, alpha));

        float textSize = 6.4f * scale;
        String actionLabel = "Editor".equals(action)
                ? LanguageManager.translate("clickgui.editor")
                : "Action".equals(action) ? LanguageManager.translate("clickgui.action") : action;
        String fitted = fitText(actionLabel, textSize, rw - 10.0f * scale);
        float tw = Render2D.textWidth(FontType.BOLD, fitted, textSize);
        float textY = OptionStyle.centeredTextY(FontType.BOLD, fitted, textSize, ry, rh);
        Render2D.text(FontType.BOLD, fitted, rx + (rw - tw) * 0.5f,
                textY, textSize, color(230, 225, 236, 252, alpha));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() != 0) return false;
        if (hovered(event.x(), event.y(), controlX, controlY, controlWidth, controlHeight)) {
            pressAnimation.set(0.0);
            pressAnimation.run(1.0, 0.18, Easings.CUBIC_OUT);
            setting.press();
            return true;
        }
        return false;
    }
}
