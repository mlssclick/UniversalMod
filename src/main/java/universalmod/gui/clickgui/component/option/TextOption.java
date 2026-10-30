package universalmod.gui.clickgui.component.option;


import universalmod.service.language.LanguageManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import universalmod.gui.clickgui.CharacterEvent;
import universalmod.gui.clickgui.KeyEvent;
import universalmod.gui.clickgui.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import universalmod.render.Render2D;
import universalmod.render.optimize.UiFrameContext;
import universalmod.render.animation.Easings;
import universalmod.render.animation.SmoothAnimation;
import universalmod.render.color.ColorUtil;
import universalmod.render.font.FontType;
import universalmod.render.surface.GuiSurface;
import universalmod.setting.StringSetting;
import universalmod.theme.ThemeColors;


public class TextOption extends ClickGuiOption {
    private final SmoothAnimation scrollAnimation = new SmoothAnimation();
    private final SmoothAnimation outlineAnimation = new SmoothAnimation();
    private final StringSetting setting;
    private String value;
    private boolean editing;
    private boolean selectedAll;
    private int cursor;
    private String cachedCursorValue;
    private int cachedCursor = -1;
    private float cachedCursorTextSize;
    private float cachedCursorWidth;
    private float controlX;
    private float controlY;
    private float controlWidth;
    private float controlHeight;

    public TextOption(StringSetting setting) {
        super(setting.getName());
        this.setting = setting;
        this.value = setting.getValue();
        this.cursor = value.length();
        scrollAnimation.set(0.0);
        outlineAnimation.set(0.0);
    }

    @Override
    public float getHeight() {
        return 34.0f;
    }

    @Override
    protected void renderControl(DrawContext graphics, float x, float y, float width, float height, float scale, float alpha) {
        if (!editing) {
            value = setting.getValue();
            cursor = Math.min(cursor, value.length());
        }

        float titleSize = OptionStyle.TITLE_SIZE * scale;
        float titleY = OptionStyle.centeredTextY(FontType.BOLD, displayName(), titleSize, y, 13.0f * scale);
        Render2D.text(FontType.BOLD, displayName(), x, titleY, titleSize,
                color(241, 239, 246, 255, alpha));

        controlX = x;
        controlY = y + 17.0f * scale;
        controlWidth = width;
        controlHeight = 13.0f * scale;
        float textSize = 6.4f * scale;
        float textX = controlX + 6.0f * scale;
        float textY = OptionStyle.centeredTextY(FontType.SEMIBOLD, value.isEmpty() ? LanguageManager.translate("clickgui.type_text") : value, textSize, controlY, controlHeight);
        float available = controlWidth - 12.0f * scale;
        float valueWidth = textWidth(value, textSize);
        float targetScroll = valueWidth > available ? available - valueWidth - 1.0f * scale : 0.0f;
        scrollAnimation.run(targetScroll, 0.20, Easings.CUBIC_OUT, true);
        scrollAnimation.update();
        outlineAnimation.run(editing ? 1.0 : 0.0, 0.18, Easings.CUBIC_OUT, true);
        outlineAnimation.update();

        new GuiSurface(controlX, controlY, controlWidth, controlHeight, 5.0f * scale,
                alpha, 0.07f + 0.50f * outlineAnimation.get(),
                ColorUtil.rgba(29, 27, 31, 255), ThemeColors.clickGuiAccentColor(255, 1.0f)).render(graphics);
        Render2D.outline(controlX, controlY, controlWidth, controlHeight, 5.0f * scale, 0.34f * scale,
                color(104, 98, 110, editing ? 80 : 42, alpha));

        Render2D.pushScissor(graphics, controlX + 5.0f * scale, controlY,
                Math.max(1.0f, available + 2.0f * scale), controlHeight);
        try {
            float renderedTextX = textX + scrollAnimation.get();
            if (editing && selectedAll && !value.isEmpty()) {
                float selectW = Math.max(2.0f, valueWidth);
                Render2D.rect(renderedTextX - 1.0f * scale, controlY + 2.0f * scale,
                        selectW + 2.0f * scale, 9.0f * scale, 1.0f * scale,
                        color(159, 146, 195, 72, alpha));
            }
            String shown = value.isEmpty() && !editing ? LanguageManager.translate("clickgui.type_text") : value;
            int textColor = value.isEmpty() && !editing
                    ? color(123, 119, 130, 218, alpha)
                    : color(221, 217, 227, 245, alpha);
            Render2D.text(FontType.SEMIBOLD, shown, renderedTextX, textY, textSize, textColor);

            if (editing && !selectedAll && (UiFrameContext.nowMillis() / 420L) % 2L == 0L) {
                Render2D.TextVisualBounds bounds = Render2D.textVisualBounds(FontType.SEMIBOLD, value, textSize);
                float caretX = renderedTextX + cursorWidth(bounds, textSize);
                Render2D.rect(caretX, controlY + 2.0f * scale, 0.75f * scale,
                        9.0f * scale, 0.3f * scale, color(246, 243, 249, 235, alpha));
            }
        } finally {
            Render2D.popScissor(graphics);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() != 0) return false;
        if (hovered(event.x(), event.y(), controlX, controlY, controlWidth, controlHeight)) {
            editing = true;
            selectedAll = false;
            cursor = value.length();
            return true;
        }
        editing = false;
        selectedAll = false;
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!editing) return false;
        int key = event.key();
        boolean ctrl = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && key == GLFW.GLFW_KEY_A) {
            selectedAll = true;
            cursor = value.length();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_C) {
            setClipboard(value);
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_V) {
            insertText(getClipboard());
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (selectedAll) {
                value = "";
                cursor = 0;
                selectedAll = false;
            } else if (cursor > 0) {
                int from = ctrl ? previousWordBoundary(value, cursor) : cursor - 1;
                value = value.substring(0, from) + value.substring(cursor);
                cursor = from;
            }
            syncSetting();
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE) {
            if (selectedAll) {
                value = "";
                cursor = 0;
                selectedAll = false;
            } else if (cursor < value.length()) {
                int to = ctrl ? nextWordBoundary(value, cursor) : cursor + 1;
                value = value.substring(0, cursor) + value.substring(to);
            }
            syncSetting();
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT) {
            cursor = ctrl ? previousWordBoundary(value, cursor) : Math.max(0, cursor - 1);
            selectedAll = false;
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT) {
            cursor = ctrl ? nextWordBoundary(value, cursor) : Math.min(value.length(), cursor + 1);
            selectedAll = false;
            return true;
        }
        if (key == GLFW.GLFW_KEY_HOME) {
            cursor = 0;
            selectedAll = false;
            return true;
        }
        if (key == GLFW.GLFW_KEY_END) {
            cursor = value.length();
            selectedAll = false;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_ESCAPE) {
            editing = false;
            selectedAll = false;
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!editing || !event.isAllowedChatCharacter()) return false;
        insertText(event.codepointAsString());
        return true;
    }

    private void insertText(String text) {
        if (text == null || text.isEmpty()) return;
        String cleaned = text.replace("\r", "").replace("\n", " ");
        if (selectedAll) {
            value = cleaned;
            cursor = value.length();
            selectedAll = false;
        } else {
            value = value.substring(0, cursor) + cleaned + value.substring(cursor);
            cursor += cleaned.length();
        }
        syncSetting();
    }

    private String getClipboard() {
        MinecraftClient client = MinecraftClient.getInstance();
        String clipboard = GLFW.glfwGetClipboardString(client.getWindow().getHandle());
        return clipboard == null ? "" : clipboard;
    }

    private void setClipboard(String text) {
        MinecraftClient client = MinecraftClient.getInstance();
        GLFW.glfwSetClipboardString(client.getWindow().getHandle(), text);
    }

    private void syncSetting() {
        setting.setValue(value);
        value = setting.getValue();
        cursor = Math.min(cursor, value.length());
        invalidateCursorWidth();
    }

    private float cursorWidth(Render2D.TextVisualBounds valueBounds, float textSize) {
        int safeCursor = Math.min(cursor, value.length());
        if (safeCursor == 0) return 0.0f;
        if (cachedCursorValue != value || cachedCursor != safeCursor || cachedCursorTextSize != textSize) {
            cachedCursorValue = value;
            cachedCursor = safeCursor;
            cachedCursorTextSize = textSize;
            String prefix = value.substring(0, safeCursor);


            cachedCursorWidth = textWidth(prefix, textSize);
        }
        return cachedCursorWidth;
    }


    private static int previousWordBoundary(String text, int cursor) {
        int i = Math.max(0, Math.min(cursor, text.length()));
        while (i > 0 && Character.isWhitespace(text.charAt(i - 1))) i--;
        while (i > 0 && !Character.isWhitespace(text.charAt(i - 1))) i--;
        return i;
    }

    private static int nextWordBoundary(String text, int cursor) {
        int i = Math.max(0, Math.min(cursor, text.length()));
        while (i < text.length() && !Character.isWhitespace(text.charAt(i))) i++;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
        return i;
    }

    private void invalidateCursorWidth() {
        cachedCursorValue = null;
        cachedCursor = -1;
        cachedCursorTextSize = 0.0f;
        cachedCursorWidth = 0.0f;
    }
}
