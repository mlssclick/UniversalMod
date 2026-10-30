package universalmod.gui.clickgui.component.option;


import universalmod.service.language.LanguageManager;
import net.minecraft.client.gui.DrawContext;
import universalmod.gui.clickgui.KeyEvent;
import universalmod.gui.clickgui.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import universalmod.render.Render2D;
import universalmod.render.color.ColorUtil;
import universalmod.render.font.FontType;
import universalmod.render.surface.GuiSurface;
import universalmod.setting.BindSetting;
import universalmod.setting.bind.KeyBind;
import universalmod.theme.ThemeColors;


public class BindOption extends ClickGuiOption {
    private final BindSetting setting;
    private String key;
    private boolean listening;
    private float boxX;
    private float boxY;
    private float boxWidth;
    private float boxHeight;

    public BindOption(BindSetting setting) {
        super(setting.getName());
        this.setting = setting;
        this.key = display(setting.getValue());
    }

    @Override
    public float getHeight() {
        return OptionStyle.INLINE_OPTION_HEIGHT;
    }

    @Override
    protected void renderControl(DrawContext graphics, float x, float y, float width, float height, float scale, float alpha) {
        if (!listening) key = display(setting.getValue());
        String text = listening ? "..." : compact(key);
        float textSize = 6.45f * scale;
        float textWidth = Render2D.textWidth(FontType.BOLD, text, textSize);
        boxHeight = OptionStyle.INLINE_CONTROL_HEIGHT * scale;
        boxWidth = Math.max(20.0f * scale, Math.min(44.0f * scale, textWidth + 11.0f * scale));
        boxX = x + width - boxWidth;
        boxY = y + (height - boxHeight) * 0.5f;

        float titleW = Math.max(1.0f, boxX - x - 9.0f * scale);
        float titleSize = OptionStyle.TITLE_SIZE * scale;
        float titleY = OptionStyle.centeredTextY(FontType.BOLD, displayName(), titleSize, y, height);
        renderScrollingText(graphics, "bind:title", displayName(), x, titleY,
                titleSize,
                color(243, 241, 247, 255, alpha),
                x, y, titleW, height,
                x, y, titleW, height);

        float radius = boxHeight * 0.5f;
        new GuiSurface(boxX, boxY, boxWidth, boxHeight, radius,
                alpha, listening ? 0.62f : 0.08f,
                ColorUtil.rgba(25, 23, 29, 255), ThemeColors.clickGuiAccentColor(255, 1.0f)).render(graphics);
        Render2D.outline(boxX, boxY, boxWidth, boxHeight, radius, 0.36f * scale,
                color(116, 108, 123, listening ? 88 : 50, alpha));

        float textY = OptionStyle.centeredTextY(FontType.BOLD, text, textSize, boxY, boxHeight);
        Render2D.text(FontType.BOLD, text,
                boxX + (boxWidth - textWidth) * 0.5f,
                textY,
                textSize,
                listening ? color(246, 242, 251, 255, alpha) : color(218, 214, 224, 250, alpha));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (listening) {
            setting.setValue(KeyBind.mouse(event.button()));
            key = display(setting.getValue());
            listening = false;
            return true;
        }
        if (event.button() == 0 && hovered(event.x(), event.y(), boxX - 2.0f * scale, boxY - 2.0f * scale,
                boxWidth + 4.0f * scale, boxHeight + 4.0f * scale)) {
            listening = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!listening) return false;
        if (event.key() == GLFW.GLFW_KEY_DELETE || event.key() == GLFW.GLFW_KEY_ESCAPE || event.key() == GLFW.GLFW_KEY_BACKSPACE) {
            setting.setValue(KeyBind.NONE);
        } else {
            setting.setValue(KeyBind.keyboard(event.key()));
        }
        key = display(setting.getValue());
        listening = false;
        return true;
    }

    private static String display(KeyBind value) {
        return value != null && value.isBound() ? value.getDisplayName() : LanguageManager.translate("clickgui.none");
    }

    private String compact(String value) {
        if (value == null || value.isBlank()) return LanguageManager.translate("clickgui.none");
        return value.replace("LEFT ", "L").replace("RIGHT ", "R").replace("MOUSE ", "M");
    }
}
