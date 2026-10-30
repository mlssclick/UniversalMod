package universalmod.gui.clickgui.component.option;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import universalmod.gui.clickgui.CharacterEvent;
import universalmod.gui.clickgui.KeyEvent;
import universalmod.gui.clickgui.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import universalmod.gui.clickgui.ClickGuiController;
import universalmod.gui.ui.control.SmoothFloatSliderRenderer;
import universalmod.gui.ui.control.SliderControl;
import universalmod.gui.ui.control.MarqueeTextControl;
import universalmod.render.Render2D;
import universalmod.render.color.ColorUtil;
import universalmod.render.font.FontType;
import universalmod.setting.SliderSetting;
import universalmod.theme.ThemeColors;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class SliderOption extends ClickGuiOption {
    private static final float KNOB_SIZE = SmoothFloatSliderRenderer.BASE_KNOB_SIZE;
    private static final float ROW_HEIGHT = 31.0F;
    private static final float LABEL_SIZE = OptionStyle.TITLE_SIZE;
    private static final float VALUE_SIZE = 7.0F;

    private final SliderSetting setting;
    private final String suffix;
    private final SliderControl slider = new SliderControl();
    private boolean editing;
    private boolean replaceOnType;
    private String editText = "";
    private int cursor;
    private float inputScroll;

    private final MarqueeTextControl labelMarquee = new MarqueeTextControl();

    private float chipX;
    private float chipY;
    private float chipW;
    private float chipH;
    private float trackX;
    private float trackY;
    private float trackW;

    public SliderOption(SliderSetting setting, String suffix) {
        super(setting.getName());
        this.setting = setting;
        this.suffix = suffix == null ? "" : suffix;
        this.slider.reset(progress());
        syncEditorFromValue();
    }

    @Override
    public float getHeight() {
        return ROW_HEIGHT;
    }

    @Override
    protected void renderControl(DrawContext graphics, float x, float y, float width, float height, float scale, float alpha) {
        if (!editing) {
            syncEditorFromValue();
        }

        layout(x, y, width, height, scale);
        renderLabel(graphics, x, y, width, height, alpha, scale);
        renderValueChip(graphics, alpha, scale);
        slider.render(trackX, trackY, trackW, scale, alpha, progress());
    }

    private void layout(float x, float y, float width, float height, float scale) {
        chipW = chipWidth(scale);
        chipH = LABEL_SIZE * scale;
        chipH = LABEL_SIZE * scale;
        chipY = y + 5.0F * scale;
        chipX = x + width - chipW;

        trackX = x;
        trackY = y + height - 5.0F * scale - SmoothFloatSliderRenderer.BASE_TRACK_HEIGHT * scale;
        trackW = Math.max(20.0F * scale, width);
    }

    private void renderLabel(DrawContext graphics, float x, float y, float width, float height, float alpha, float scale) {
        String label = displayName();
        float textX = x;
        float maxWidth = Math.max(0.0F, chipX - textX - 6.0F * scale);
        float size = LABEL_SIZE * scale;
        float labelY = chipY + (chipH - size) * 0.5F;
        labelMarquee.render(graphics, label, textX, labelY, size, maxWidth,
                hovered(ClickGuiController.mouseX(), ClickGuiController.mouseY(), x, y, width, height),
                labelColor(alpha), scale);
    }

    private void renderValueChip(DrawContext graphics, float alpha, float scale) {
        if (!editing) {
            String value = formatValue(setting.getValue()) + suffix;
            float size = VALUE_SIZE * scale;
            float textW = Render2D.textWidth(FontType.SEMIBOLD, value, size);
            float textY = chipY + (chipH - size) * 0.5F;
            Render2D.text(FontType.SEMIBOLD, value, xRight() - textW, textY, size, valueColor(alpha));
            return;
        }

        float size = VALUE_SIZE * scale;
        float suffixW = suffix.isEmpty() ? 0.0F : Render2D.textWidth(FontType.SEMIBOLD, suffix, size);
        float suffixGap = suffix.isEmpty() ? 0.0F : 1.5F * scale;
        float inputW = Math.max(4.0F * scale, chipW - suffixW - suffixGap);
        float inputX = chipX;
        float inputY = chipY + chipH * 0.5F - 2.75F * scale;

        updateInputScroll(inputW, size);
        if (!editText.isEmpty()) {
            Render2D.pushScissor(graphics, inputX, inputY, inputW, size);
            try {
                Render2D.text(FontType.SEMIBOLD, editText, inputX - inputScroll, inputY, size, valueColor(alpha));
                float caretPrefix = textWidth(editText.substring(0, Math.min(cursor, editText.length())), size);
                float blink = (float) ((Math.sin(System.currentTimeMillis() / 250.0D) + 1.0D) * 0.5D);
                Render2D.rect(inputX + caretPrefix - inputScroll, inputY, 0.5F * scale, size,
                        withAlpha(valueColor(alpha), blink));
            } finally {
                Render2D.popScissor(graphics);
            }
        } else {
            inputScroll = 0.0F;
        }

        if (!suffix.isEmpty()) {
            float suffixSize = 6.5F * scale;
            float suffixY = chipY + chipH * 0.5F - suffixSize * 0.5F;
            Render2D.text(FontType.SEMIBOLD, suffix, chipX + inputW + suffixGap,
                    suffixY, suffixSize, valueColor(alpha));
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event == null || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }

        if (hovered(event.x(), event.y(), chipX, chipY, chipW, chipH)) {
            boolean wasEditing = editing;
            if (!wasEditing) {
                syncEditorFromValue();
            }
            editing = true;
            replaceOnType = !wasEditing;
            slider.cancel();
            return true;
        }

        if (editing) {
            commitEditor();
            editing = false;
            replaceOnType = false;
            syncEditorFromValue();
        }

        return slider.press(event.x(), event.y(), trackX, trackY, trackW, scale,
                normalized -> SliderControl.apply(setting, normalized));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {

        if (event == null) {
            return false;
        }
        return slider.drag(event.x(), normalized -> SliderControl.apply(setting, normalized));
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        slider.release();
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!editing || event == null) {
            return false;
        }

        int key = event.key();
        boolean ctrl = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            editing = false;
            replaceOnType = false;
            syncEditorFromValue();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            commitEditor();
            editing = false;
            replaceOnType = false;
            syncEditorFromValue();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_V) {
            insertNumeric(getClipboard());
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            replaceOnType = false;
            if (ctrl) {
                editText = "";
                cursor = 0;
            } else {
                deleteAt(cursor - 1);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE) {
            replaceOnType = false;
            deleteAt(cursor);
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT) {
            replaceOnType = false;
            cursor = ctrl ? editText.length() : cursor + 1;
            clampCursor();
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT) {
            replaceOnType = false;
            cursor = ctrl ? 0 : cursor - 1;
            clampCursor();
            return true;
        }
        if (key == GLFW.GLFW_KEY_HOME) {
            replaceOnType = false;
            cursor = 0;
            return true;
        }
        if (key == GLFW.GLFW_KEY_END) {
            replaceOnType = false;
            cursor = editText.length();
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!editing || event == null) {
            return false;
        }
        String text = event.codepointAsString();
        if (text == null || text.isEmpty()) {
            return true;
        }
        insertNumeric(text);
        return true;
    }

    @Override
    public void closePopup() {
        slider.cancel();
        editing = false;
        replaceOnType = false;
        syncEditorFromValue();
    }

    private float progress() {
        return SliderControl.progress(setting);
    }

    private void syncEditorFromValue() {
        String value = formatValue(setting.getValue());
        if (!value.equals(editText)) {
            editText = value;
            cursor = value.length();
            inputScroll = 0.0F;
        } else {
            clampCursor();
        }
    }

    private void commitEditor() {
        String text = editText.trim().replace(',', '.');
        if (text.isEmpty() || text.equals("-") || text.equals(".") || text.equals("-.")) {
            return;
        }
        try {
            double value = Double.parseDouble(text);
            if (Double.isFinite(value)) {
                setting.setValue(value);
            }
        } catch (NumberFormatException malformedNumber) {
            return;
        }
    }

    private void insertNumeric(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (replaceOnType) {
            editText = "";
            cursor = 0;
            replaceOnType = false;
        }

        String filtered = filterNumeric(text);
        if (filtered.isEmpty()) {
            return;
        }
        editText = new StringBuilder(editText).insert(cursor, filtered).toString();
        cursor += filtered.length();
        clampCursor();
    }

    private String filterNumeric(String text) {
        StringBuilder out = new StringBuilder();
        boolean hasDecimal = editText.indexOf('.') >= 0 || editText.indexOf(',') >= 0;
        boolean hasMinus = editText.indexOf('-') >= 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                out.append(c);
            } else if ((c == '.' || c == ',') && !hasDecimal) {
                out.append('.');
                hasDecimal = true;
            } else if (c == '-' && !hasMinus && cursor == 0 && out.length() == 0) {
                out.append(c);
                hasMinus = true;
            }
        }
        return out.toString();
    }

    private void deleteAt(int index) {
        if (index >= 0 && index < editText.length()) {
            editText = new StringBuilder(editText).deleteCharAt(index).toString();
            if (cursor > index) {
                cursor--;
            }
        }
        clampCursor();
    }

    private void clampCursor() {
        cursor = Math.max(0, Math.min(cursor, editText.length()));
    }

    private void updateInputScroll(float availableWidth, float textSize) {
        clampCursor();
        float caret = textWidth(editText.substring(0, cursor), textSize);
        float full = textWidth(editText, textSize);
        if (caret - inputScroll > availableWidth) {
            inputScroll = caret - availableWidth;
        }
        if (caret - inputScroll < 0.0F) {
            inputScroll = caret;
        }
        float maxScroll = Math.max(0.0F, full - availableWidth);
        if (inputScroll > maxScroll) {
            inputScroll = maxScroll;
        }
        if (inputScroll < 0.0F) {
            inputScroll = 0.0F;
        }
    }

    private float chipWidth(float scale) {
        String max = formatValue(setting.getMax());
        String min = formatValue(setting.getMin());
        String longest = max.length() >= min.length() ? max : min;
        String zeroShape = longest.replaceAll("\\d", "0");
        float valueW = textWidth(zeroShape, VALUE_SIZE * scale);
        float suffixW = suffix.isEmpty() ? 0.0F : textWidth(suffix, VALUE_SIZE * scale);
        return Math.max(1.0F * scale, valueW + suffixW + (suffix.isEmpty() ? 0.0F : 1.5F) * scale);
    }

    private String formatValue(double value) {
        float mapped = (float) value;
        int decimals = stepDecimals();
        if (decimals < 0) {
            return mapped == (int) mapped ? Integer.toString((int) mapped) : Float.toString(mapped);
        }
        return new BigDecimal(Float.toString(mapped)).setScale(decimals, RoundingMode.HALF_UP).toPlainString();
    }

    private int stepDecimals() {
        double step = setting.getStep();
        if (step <= 0.0D) {
            return -1;
        }
        BigDecimal decimal = new BigDecimal(Float.toString((float) step)).stripTrailingZeros();
        return Math.max(0, decimal.scale());
    }

    private int labelColor(float alpha) {
        return blendAccentWhite(0.92F, alpha);
    }

    private int valueColor(float alpha) {
        return blendAccentWhite(0.68F, alpha);
    }

    private float xRight() {
        return chipX + chipW;
    }

    private int blendAccentWhite(float amount, float alpha) {
        int accent = ThemeColors.clickGuiAccentColor(255, 1.0F);
        float t = clamp(amount, 0.0F, 1.0F);
        int r = Math.round(ColorUtil.getRed(accent) + (255.0F - ColorUtil.getRed(accent)) * t);
        int g = Math.round(ColorUtil.getGreen(accent) + (255.0F - ColorUtil.getGreen(accent)) * t);
        int b = Math.round(ColorUtil.getBlue(accent) + (255.0F - ColorUtil.getBlue(accent)) * t);
        return ColorUtil.rgba(r, g, b, Math.round(255.0F * clamp(alpha, 0.0F, 1.0F)));
    }

    private int withAlpha(int color, float multiplier) {
        return ColorUtil.rgba(ColorUtil.getRed(color), ColorUtil.getGreen(color), ColorUtil.getBlue(color),
                Math.round(ColorUtil.getAlpha(color) * clamp(multiplier, 0.0F, 1.0F)));
    }

    private String getClipboard() {
        MinecraftClient client = MinecraftClient.getInstance();
        String clipboard = GLFW.glfwGetClipboardString(client.getWindow().getHandle());
        return clipboard == null ? "" : clipboard;
    }
}
