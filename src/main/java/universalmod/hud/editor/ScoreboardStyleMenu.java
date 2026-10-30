package universalmod.hud.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import universalmod.hud.ElementScreen;
import universalmod.gui.ui.control.Bounds;
import universalmod.gui.ui.control.ColorPickerGeometry;
import universalmod.gui.ui.control.ColorPickerInteraction;
import universalmod.gui.ui.control.ColorPickerRenderer;
import universalmod.module.render.Scoreboard;
import universalmod.setting.Setting;
import universalmod.setting.BooleanSetting;
import universalmod.setting.ColorSetting;
import universalmod.setting.ModeSetting;
import universalmod.setting.NumberSetting;
import universalmod.render.animation.Easings;
import universalmod.render.animation.SmoothAnimation;
import universalmod.render.animation.ReversibleAnimationTimeline;
import universalmod.render.color.ColorUtil;
import universalmod.gui.ui.control.SmoothSwitchRenderer;
import universalmod.gui.ui.control.SmoothFloatSliderRenderer;
import universalmod.render.Render2D;
import universalmod.render.Render2DCoordinateSpace;
import universalmod.render.optimize.UiFrameContext;
import universalmod.render.font.FontType;
import universalmod.gui.ui.control.ColorSwatchRenderer;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ScoreboardStyleMenu {
    private static final ScoreboardStyleMenu INSTANCE = new ScoreboardStyleMenu();
    private static final float WIDTH = 170.0F;
    private static final float HEADER = 18.0F;
    private static final float BOOLEAN_ROW = 18.0F;
    private static final float MODE_ROW = 18.0F;
    private static final float NUMBER_ROW = 24.0F;
    private static final float COLOR_ROW = 18.0F;
    private static final float OPTION = 14.0F;
    private static final float PICKER_WIDTH = 84.0F;
    private static final float PICKER_HEIGHT = 86.0F;
    private static final float PICKER_GAP = 6.0F;
    private static final float PADDING = 6.0F;
    private static final float CONTROL_WIDTH = 72.0F;
    private static final float SIZE_CONTROL_WIDTH = 142.0F;
    private static final float GAP = 8.0F;
    private static final float MARGIN = 4.0F;
    private static final float SCROLL_STEP = 28.0F;
    private static final float MAX_VIEWPORT = 238.0F;
    private final SmoothAnimation appear = new SmoothAnimation();
    private final SmoothAnimation dropdown = new SmoothAnimation();
    private final SmoothAnimation picker = new SmoothAnimation();
    private final SmoothAnimation scroll = new SmoothAnimation();
    private final Map<BooleanSetting, ReversibleAnimationTimeline> booleanAnimations = new HashMap<>();
    private final Map<NumberSetting, SmoothAnimation> numberAnimations = new HashMap<>();
    private final Map<NumberSetting, SmoothAnimation> numberPressAnimations = new HashMap<>();
    private final List<Setting<?>> visibleSettingsScratch = new ArrayList<>();
    private long visibleSettingsFrame = Long.MIN_VALUE;
    private Scoreboard scoreboard;
    private ModeSetting openMode;
    private boolean modeExpanded;
    private ColorSetting openColor;
    private NumberSetting activeSlider;
    private float activeSliderTrackX;
    private float activeSliderTrackWidth;
    private ScoreboardStyleMenuPlacement.Side side = ScoreboardStyleMenuPlacement.Side.RIGHT;
    private float x;
    private float y;
    private float scrollTarget;
    private final ColorPickerInteraction colorPicker = new ColorPickerInteraction();
    private float pickerPanelX;
    private float pickerPanelY;

    private ScoreboardStyleMenu() {
        appear.set(0.0);
        dropdown.set(0.0);
        picker.set(0.0);
        scroll.set(0.0);
    }

    public static ScoreboardStyleMenu getInstance() {
        return INSTANCE;
    }

    public boolean isOpen() {
        return scoreboard != null;
    }

    public void open(Scoreboard scoreboard, float mouseX) {
        if (scoreboard == null || !scoreboard.isEnabled()) {
            close();
            return;
        }
        ScoreboardStyleMenuPlacement.Side next = mouseX < scoreboard.editorX() + scoreboard.editorWidth() * 0.5F
                ? ScoreboardStyleMenuPlacement.Side.LEFT
                : ScoreboardStyleMenuPlacement.Side.RIGHT;
        if (this.scoreboard == scoreboard && side == next) {
            return;
        }
        this.scoreboard = scoreboard;
        side = next;
        openMode = null;
        modeExpanded = false;
        openColor = null;
        activeSlider = null;
        colorPicker.cancelDrag();
        scrollTarget = 0.0F;
        appear.set(0.0);
        dropdown.set(0.0);
        picker.set(0.0);
        scroll.set(0.0);
        place(true);
        appear.run(1.0, 0.20, Easings.BAKEK);
    }

    public void close() {
        scoreboard = null;
        openMode = null;
        modeExpanded = false;
        openColor = null;
        activeSlider = null;
        colorPicker.cancelDrag();
        scrollTarget = 0.0F;
        appear.set(0.0);
        dropdown.set(0.0);
        picker.set(0.0);
        scroll.set(0.0);
    }

    public void render(GuiGraphics graphics) {
        if (!isOpen()) {
            return;
        }
        if (scoreboard == null || !scoreboard.isEnabled()) {
            close();
            return;
        }
        appear.update();
        dropdown.update();
        picker.update();
        if (openMode != null && !openMode.isVisible() && modeExpanded) {
            modeExpanded = false;
            dropdown.run(0.0, 0.16, Easings.CUBIC_OUT, true);
        }
        if (openMode != null && !modeExpanded && dropdown.get() <= 0.001F && !dropdown.isAlive()) {
            openMode = null;
        }
        if (openColor != null && !openColor.isVisible()) {
            openColor = null;
            colorPicker.cancelDrag();
            picker.run(0.0, 0.16, Easings.CUBIC_OUT, true);
        } else if (openColor != null && !colorPicker.dragging()) {
            loadColor(openColor.getValue());
        }
        updatePointerDrag();
        clampScroll();
        scroll.run(scrollTarget, activeSlider != null || colorPicker.dragging() ? 0.08 : 0.18, Easings.CUBIC_OUT, true);
        scroll.update();
        place(false);
        float a = clamp01(appear.get());
        if (a <= 0.001F) {
            return;
        }
        float height = popupHeight();
        scoreboard.renderEditorPopupBackground(graphics, x, y, WIDTH, height, 5.0F);
        Render2D.text(FontType.BOLD, universalmod.service.language.LanguageManager.moduleName("Custom Scoreboard"), x + PADDING, y + 4.4F, 7.6F, alpha(0xFFF3F6FA, a));
        float viewportY = y + HEADER;
        float viewportHeight = height - HEADER - PADDING;
        Render2D.pushScissor(graphics, x + 1.0F, viewportY, WIDTH - 2.0F, viewportHeight);
        try {
            float rowY = viewportY - scroll.get();
            for (Setting<?> setting : visibleSettings()) {
                renderSetting(graphics, setting, rowY, a);
                rowY += settingHeight(setting);
            }
        } finally {
            Render2D.popScissor(graphics);
        }
        renderScrollIndicator(a, viewportY, viewportHeight);
        if (openColor != null && picker.get() > 0.001F && colorAnchorVisible()) {
            updatePickerBounds();
            renderColorPicker(graphics, clamp01(picker.get()), a);
        }
    }

    public boolean mouseClicked(MouseButtonEvent event) {
        if (scoreboard != null && !scoreboard.isEnabled()) {
            close();
        }
        if (!isOpen() || event == null) {
            return false;
        }
        place(false);
        float mx = (float) event.x();
        float my = (float) event.y();
        boolean pickerVisible = openColor != null && picker.get() > 0.20F && colorAnchorVisible();
        if (pickerVisible) {
            updatePickerBounds();
            if (inside(mx, my, pickerPanelX, pickerPanelY, PICKER_WIDTH, PICKER_HEIGHT)) {
                if (event.button() == 0) {
                    handleColorPickerClick(mx, my);
                }
                return true;
            }
        }
        if (!inside(mx, my, x, y, WIDTH, popupHeight())) {
            if (event.button() == 0 || event.button() == 1) {
                close();
                return true;
            }
            return false;
        }
        if (event.button() != 0) {
            return true;
        }
        float viewportY = y + HEADER;
        float viewportHeight = popupHeight() - HEADER - PADDING;
        if (!inside(mx, my, x, viewportY, WIDTH, viewportHeight)) {
            return true;
        }
        float rowY = viewportY - scroll.get();
        for (Setting<?> setting : visibleSettings()) {
            float h = settingHeight(setting);
            if (setting instanceof BooleanSetting booleanSetting && inside(mx, my, x + PADDING, rowY, WIDTH - PADDING * 2.0F, BOOLEAN_ROW)) {
                closeExpanded();
                booleanSetting.setValue(!booleanSetting.getValue());
                return true;
            }
            if (setting instanceof ModeSetting modeSetting) {
                if (inside(mx, my, controlX(), rowY + 2.0F, CONTROL_WIDTH, 13.0F)) {
                    toggleMode(modeSetting);
                    return true;
                }
                if (openMode == modeSetting && modeExpanded && dropdown.get() > 0.20F) {
                    int index = modeOptionIndex(modeSetting, mx, my, rowY + MODE_ROW);
                    if (index >= 0) {
                        modeSetting.setValue(modeSetting.getModes().get(index));
                        modeExpanded = false;
                        dropdown.run(0.0, 0.16, Easings.CUBIC_OUT, true);
                        clampScroll();
                        return true;
                    }
                }
            }
            if (setting instanceof NumberSetting numberSetting) {
                float sliderX = numberSliderX(numberSetting);
                float sliderWidth = numberSliderWidth(numberSetting);
                float sliderY = rowY + 16.0F;
                SmoothFloatSliderRenderer.Layout sliderBounds = SmoothFloatSliderRenderer.layout(sliderX, sliderY, sliderWidth, 1.0F);
                if (sliderBounds.hit().contains(mx, my)) {
                    closeExpanded();
                    activeSlider = numberSetting;
                    activeSliderTrackX = sliderX;
                    activeSliderTrackWidth = sliderWidth;
                    updateNumber(numberSetting, mx);
                    return true;
                }
            }
            if (setting instanceof ColorSetting colorSetting && inside(mx, my, controlX() + CONTROL_WIDTH - 17.0F, rowY + 2.0F, 18.0F, 14.0F)) {
                toggleColor(colorSetting);
                return true;
            }
            rowY += h;
        }
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (scoreboard == null || !scoreboard.isEnabled()) {
            close();
            return false;
        }
        if (!isOpen()) {
            return false;
        }
        if (openColor != null && picker.get() > 0.20F && colorAnchorVisible()) {
            updatePickerBounds();
            if (inside((float) mouseX, (float) mouseY, pickerPanelX, pickerPanelY, PICKER_WIDTH, PICKER_HEIGHT)) {
                return true;
            }
        }
        if (!inside((float) mouseX, (float) mouseY, x, y, WIDTH, popupHeight())) {
            return false;
        }
        float max = maxScroll();
        if (max <= 0.01F) {
            return true;
        }
        scrollTarget = clamp(scrollTarget - (float) amount * SCROLL_STEP, 0.0F, max);
        scroll.run(scrollTarget, 0.18, Easings.CUBIC_OUT, true);
        return true;
    }

    private void renderSetting(GuiGraphics graphics, Setting<?> setting, float rowY, float a) {
        if (setting instanceof BooleanSetting booleanSetting) {
            renderBoolean(rowY, booleanSetting, a);
            return;
        }
        if (setting instanceof ModeSetting modeSetting) {
            renderMode(graphics, rowY, modeSetting, a);
            return;
        }
        if (setting instanceof NumberSetting numberSetting) {
            renderNumber(rowY, numberSetting, a);
            return;
        }
        if (setting instanceof ColorSetting colorSetting) {
            renderColor(graphics, rowY, colorSetting, a);
        }
    }

    private void renderBoolean(float rowY, BooleanSetting setting, float a) {
        Render2D.text(FontType.BOLD, setting.getDisplayName(), x + PADDING, rowY + 5.0F, 6.7F, alpha(0xFFDDE3EA, a));
        float sw = SmoothSwitchRenderer.WIDTH;
        float sh = SmoothSwitchRenderer.HEIGHT;
        float sx = x + WIDTH - PADDING - sw;
        float sy = rowY + 4.5F;
        ReversibleAnimationTimeline animation = booleanAnimation(setting);
        animation.update();
        animation.run(setting.getValue() ? 1.0 : 0.0, 0.50, Easings.EXPO_OUT, false);
        SmoothSwitchRenderer.renderSized(sx, sy, sw, sh, a, clamp01(animation.get()));
    }

    private void renderMode(GuiGraphics graphics, float rowY, ModeSetting setting, float a) {
        Render2D.text(FontType.BOLD, setting.getDisplayName(), x + PADDING, rowY + 5.0F, 6.7F, alpha(0xFFDDE3EA, a));
        float bx = controlX();
        boolean expanded = openMode == setting && modeExpanded;
        boolean dropdownVisible = openMode == setting && dropdown.get() > 0.001F;
        Render2D.rect(bx, rowY + 2.0F, CONTROL_WIDTH, 13.0F, 3.5F,
                ColorUtil.rgba(255, 255, 255, Math.round((expanded ? 40.0F : 24.0F) * a)));
        renderClippedText(graphics, setting.getDisplayValue(), bx + 4.0F, rowY + 4.4F, CONTROL_WIDTH - 8.0F, 6.2F,
                alpha(expanded ? 0xFFFFFFFF : 0xFFCDD3DA, a));
        if (dropdownVisible) {
            renderModeOptions(graphics, rowY + MODE_ROW, setting, clamp01(dropdown.get()), a);
        }
    }

    private void renderModeOptions(GuiGraphics graphics, float optionY, ModeSetting setting, float progress, float a) {
        float bx = controlX();
        float fullHeight = modeOptionsHeight(setting);
        float visible = fullHeight * progress;
        scoreboard.renderEditorPopupBackground(graphics, bx, optionY, CONTROL_WIDTH, visible, 3.5F);
        Render2D.pushScissor(graphics, bx, optionY, CONTROL_WIDTH, visible);
        try {
            float slide = (1.0F - progress) * 4.0F;
            List<String> modes = setting.getModes();
            for (int i = 0; i < modes.size(); i++) {
                float oy = optionY + 1.0F + i * OPTION - slide;
                String mode = modes.get(i);
                boolean selected = setting.is(mode);
                if (selected) {
                    Render2D.rect(bx + 2.0F, oy + 1.0F, CONTROL_WIDTH - 4.0F, OPTION - 2.0F, 2.5F,
                            ColorUtil.rgba(255, 255, 255, Math.round(22.0F * a * progress)));
                }
                renderClippedText(graphics, setting.getDisplayMode(mode), bx + 4.0F, oy + 3.0F, CONTROL_WIDTH - 8.0F, 6.1F,
                        alpha(selected ? 0xFFFFFFFF : 0xFFBFC7CF, a * progress));
            }
        } finally {
            Render2D.popScissor(graphics);
        }
    }

    private void renderNumber(float rowY, NumberSetting setting, float a) {
        boolean size = setting == scoreboard.editorScaleSetting();
        String label = size ? universalmod.service.language.LanguageManager.ui("hud_editor.size") : setting.getDisplayName();
        Render2D.text(FontType.BOLD, label, x + PADDING, rowY + 7.0F, 6.7F, alpha(0xFFDDE3EA, a));
        float sx = numberSliderX(setting);
        float sw = numberSliderWidth(setting);
        float sy = rowY + 16.0F;
        float target = numberProgress(setting);
        SmoothAnimation animation = numberAnimation(setting);
        animation.update();
        if (activeSlider == setting) {
            animation.set(target);
        } else {
            animation.run(target, 0.25, Easings.EXPO_OUT, true);
        }
        float p = clamp01(animation.get());

        SmoothAnimation pressAnimation = numberPressAnimation(setting);
        pressAnimation.update();
        pressAnimation.run(activeSlider == setting ? 1.0 : 0.0, 0.15, Easings.QUAD_OUT, true);
        float press = clamp01(pressAnimation.get());

        String value = formatNumber(setting, size);
        float tw = Render2D.textWidth(FontType.BOLD, value, 6.1F);
        Render2D.text(FontType.BOLD, value, x + WIDTH - PADDING - tw, rowY + 4.0F, 6.1F, alpha(0xFFF2F5F8, a));
        SmoothFloatSliderRenderer.render(sx, sy, sw, 1.0F, a, p, press);
    }

    private void renderColor(GuiGraphics graphics, float rowY, ColorSetting setting, float a) {
        Render2D.text(FontType.BOLD, setting.getDisplayName(), x + PADDING, rowY + 5.0F, 6.7F, alpha(0xFFDDE3EA, a));
        Color color = setting.getValue();
        float size = 12.0F;
        float sx = controlX() + CONTROL_WIDTH - size - 1.0F;
        float sy = rowY + 3.0F;
        ColorSwatchRenderer.render(sx, sy, size, color, a);
    }

    private void renderColorPicker(GuiGraphics graphics, float progress, float a) {
        float popupAlpha = a * progress;
        float slide = (1.0F - progress) * 4.0F;
        float py = pickerPanelY + slide;
        scoreboard.renderEditorPopupBackground(graphics, pickerPanelX, py, PICKER_WIDTH, PICKER_HEIGHT, 4.0F);
        float paletteX = pickerPanelX + 7.0F;
        float paletteY = py + 7.0F;
        float paletteSize = 58.0F;
        Bounds palette = new Bounds(paletteX, paletteY, paletteSize, paletteSize);
        Bounds hue = new Bounds(palette.right() + 5.0F, paletteY, 7.0F, paletteSize);
        Bounds alpha = new Bounds(paletteX, palette.bottom() + 7.0F, paletteSize + 12.0F, 7.0F);
        colorPicker.geometry(new ColorPickerGeometry(palette, hue, alpha));
        colorPicker.animate();
        ColorPickerRenderer.palette(palette, colorPicker.visual().hue(), colorPicker.visual().saturation(), colorPicker.visual().brightness(), popupAlpha, 1.0F);
        ColorPickerRenderer.hue(hue, colorPicker.visual().hue(), popupAlpha, 1.0F, ColorPickerRenderer.HueAxis.VERTICAL);
        ColorPickerRenderer.alpha(alpha, colorPicker.visual().hue(), colorPicker.visual().saturation(), colorPicker.visual().brightness(), colorPicker.visual().alpha(), popupAlpha, 1.0F);
    }

    private void renderScrollIndicator(float a, float viewportY, float viewportHeight) {
        float max = maxScroll();
        if (max <= 0.5F) {
            return;
        }
        float content = contentHeight();
        float thumbHeight = Math.max(18.0F, viewportHeight * viewportHeight / content);
        float track = viewportHeight - thumbHeight;
        float p = max <= 0.0F ? 0.0F : clamp01(scroll.get() / max);
        Render2D.rect(x + WIDTH - 2.2F, viewportY + track * p, 1.2F, thumbHeight, 0.6F,
                ColorUtil.rgba(235, 239, 244, Math.round(95.0F * a)));
    }

    private void renderClippedText(GuiGraphics graphics, String text, float tx, float ty, float width, float size, int color) {
        Render2D.pushScissor(graphics, tx, ty - 1.0F, Math.max(1.0F, width), size + 4.0F);
        try {
            Render2D.text(FontType.BOLD, text, tx, ty, size, color);
        } finally {
            Render2D.popScissor(graphics);
        }
    }

    private ReversibleAnimationTimeline booleanAnimation(BooleanSetting setting) {
        return booleanAnimations.computeIfAbsent(setting, key -> {
            ReversibleAnimationTimeline animation = new ReversibleAnimationTimeline();
            animation.set(key.getValue() ? 1.0 : 0.0);
            return animation;
        });
    }

    private SmoothAnimation numberAnimation(NumberSetting setting) {
        return numberAnimations.computeIfAbsent(setting, key -> {
            SmoothAnimation animation = new SmoothAnimation();
            animation.set(numberProgress(key));
            return animation;
        });
    }

    private SmoothAnimation numberPressAnimation(NumberSetting setting) {
        return numberPressAnimations.computeIfAbsent(setting, key -> {
            SmoothAnimation animation = new SmoothAnimation();
            animation.set(activeSlider == key ? 1.0 : 0.0);
            return animation;
        });
    }

    private void updatePointerDrag() {
        if (!isOpen() || activeSlider == null && !colorPicker.dragging()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.mouseHandler == null || client.getWindow() == null) {
            activeSlider = null;
            colorPicker.cancelDrag();
            return;
        }
        if (GLFW.glfwGetMouseButton(client.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            activeSlider = null;
            colorPicker.cancelDrag();
            return;
        }
        float coordinateScale = Math.max(0.0001F, Render2DCoordinateSpace.guiIndependentScale());
        float mouseX = (float) client.mouseHandler.getScaledXPos(client.getWindow()) / coordinateScale;
        float mouseY = (float) client.mouseHandler.getScaledYPos(client.getWindow()) / coordinateScale;
        if (activeSlider != null) {
            updateNumber(activeSlider, mouseX);
        }
        if (openColor != null && colorPicker.dragging()) {
            colorPicker.drag(mouseX, mouseY, ColorPickerInteraction.HueAxis.VERTICAL, this::applyPickerColor);
        }
    }

    private boolean handleColorPickerClick(float mx, float my) {
        if (picker.get() <= 0.45F) {
            return false;
        }
        return colorPicker.press(mx, my, ColorPickerInteraction.HueAxis.VERTICAL, 2.0F, this::applyPickerColor);
    }

    private void toggleMode(ModeSetting setting) {
        if (openMode == setting) {
            modeExpanded = !modeExpanded;
            dropdown.run(modeExpanded ? 1.0 : 0.0, 0.16, Easings.CUBIC_OUT);
            return;
        }
        openColor = null;
        picker.run(0.0, 0.16, Easings.CUBIC_OUT, true);
        openMode = setting;
        modeExpanded = true;
        dropdown.set(0.0);
        dropdown.run(1.0, 0.16, Easings.CUBIC_OUT);
    }

    private void toggleColor(ColorSetting setting) {
        if (openColor == setting) {
            openColor = null;
            colorPicker.cancelDrag();
            picker.run(0.0, 0.16, Easings.CUBIC_OUT, true);
            return;
        }
        if (openMode != null) {
            modeExpanded = false;
            dropdown.run(0.0, 0.16, Easings.CUBIC_OUT);
        }
        openColor = setting;
        colorPicker.cancelDrag();
        loadColor(setting.getValue());
        colorPicker.resetVisual();
        picker.set(0.0);
        picker.run(1.0, 0.16, Easings.CUBIC_OUT, true);
    }

    private void closeExpanded() {
        if (openMode != null) {
            modeExpanded = false;
            dropdown.run(0.0, 0.16, Easings.CUBIC_OUT);
        }
        if (openColor != null) {
            openColor = null;
            colorPicker.cancelDrag();
            picker.run(0.0, 0.16, Easings.CUBIC_OUT, true);
        }
    }

    private void loadColor(Color color) {
        colorPicker.sync(color);
    }

    private void applyPickerColor(Color color) {
        if (openColor != null && color != null) {
            openColor.setValue(color);
        }
    }

    private void updateNumber(NumberSetting setting, float mouseX) {
        float sliderX = activeSlider == setting ? activeSliderTrackX : numberSliderX(setting);
        float sliderWidth = activeSlider == setting ? activeSliderTrackWidth : numberSliderWidth(setting);
        float p = SmoothFloatSliderRenderer.progressFromMouse(mouseX, sliderX, sliderWidth, 1.0F);
        double raw = setting.getMin() + (setting.getMax() - setting.getMin()) * p;
        setting.setValue(raw);
    }

    private int modeOptionIndex(ModeSetting setting, float mx, float my, float optionY) {
        float height = modeOptionsHeight(setting) * clamp01(dropdown.get());
        if (!inside(mx, my, controlX(), optionY, CONTROL_WIDTH, height)) {
            return -1;
        }
        float slide = (1.0F - clamp01(dropdown.get())) * 4.0F;
        int index = (int) ((my - optionY - 1.0F + slide) / OPTION);
        return index >= 0 && index < setting.getModes().size() ? index : -1;
    }


    private float settingRowY(Setting<?> target) {
        float rowY = y + HEADER - scroll.get();
        for (Setting<?> setting : visibleSettings()) {
            if (setting == target) {
                return rowY;
            }
            rowY += settingHeight(setting);
        }
        return Float.NaN;
    }

    private boolean colorAnchorVisible() {
        if (openColor == null) {
            return false;
        }
        float rowY = settingRowY(openColor);
        if (!Float.isFinite(rowY)) {
            return false;
        }
        float top = y + HEADER;
        float bottom = top + popupHeight() - HEADER - PADDING;
        return rowY + COLOR_ROW > top + 1.0F && rowY < bottom - 1.0F;
    }

    private void updatePickerBounds() {
        if (openColor == null) {
            return;
        }
        ScoreboardStyleMenuPlacement.PickerPlacement placement = ScoreboardStyleMenuPlacement.resolvePicker(
                x, settingRowY(openColor), WIDTH, PICKER_WIDTH, PICKER_HEIGHT, PICKER_GAP, MARGIN
        );
        pickerPanelX = placement.x();
        pickerPanelY = placement.y();
    }

    private float settingHeight(Setting<?> setting) {
        if (setting instanceof BooleanSetting) {
            return BOOLEAN_ROW;
        }
        if (setting instanceof NumberSetting) {
            return NUMBER_ROW;
        }
        if (setting instanceof ColorSetting) {
            return COLOR_ROW;
        }
        if (setting instanceof ModeSetting modeSetting) {
            float extra = openMode == modeSetting ? modeOptionsHeight(modeSetting) * clamp01(dropdown.get()) : 0.0F;
            return MODE_ROW + extra;
        }
        return 0.0F;
    }

    private float modeOptionsHeight(ModeSetting setting) {
        return setting.getModes().size() * OPTION + 2.0F;
    }

    private List<Setting<?>> visibleSettings() {
        long frame = UiFrameContext.active() ? UiFrameContext.frameId() : Long.MIN_VALUE;
        if (frame != Long.MIN_VALUE && visibleSettingsFrame == frame) {
            return visibleSettingsScratch;
        }
        visibleSettingsScratch.clear();
        if (scoreboard != null) {
            for (Setting<?> setting : scoreboard.editorSettings()) {
                if (setting.isVisible()) visibleSettingsScratch.add(setting);
            }
        }
        visibleSettingsFrame = frame;
        return visibleSettingsScratch;
    }

    private float contentHeight() {
        float height = 0.0F;
        for (Setting<?> setting : visibleSettings()) {
            height += settingHeight(setting);
        }
        return height;
    }

    private float popupHeight() {
        ElementScreen screen = ElementScreen.current();
        float maxViewport = Math.min(MAX_VIEWPORT, Math.max(80.0F, screen.height() - HEADER - PADDING - MARGIN * 2.0F));
        return HEADER + Math.min(contentHeight(), maxViewport) + PADDING;
    }

    private float maxScroll() {
        return Math.max(0.0F, contentHeight() - (popupHeight() - HEADER - PADDING));
    }

    private void clampScroll() {
        scrollTarget = clamp(scrollTarget, 0.0F, maxScroll());
    }

    private float controlX() {
        return ScoreboardStyleMenuMetrics.controlX(x, WIDTH, PADDING, CONTROL_WIDTH);
    }

    private float numberSliderX(NumberSetting setting) {
        return ScoreboardStyleMenuMetrics.numberSliderX(
                setting, scoreboard, x, WIDTH, PADDING, CONTROL_WIDTH, SIZE_CONTROL_WIDTH
        );
    }

    private float numberSliderWidth(NumberSetting setting) {
        return ScoreboardStyleMenuMetrics.numberSliderWidth(setting, scoreboard, CONTROL_WIDTH, SIZE_CONTROL_WIDTH);
    }

    private float numberProgress(NumberSetting setting) {
        return ScoreboardStyleMenuMetrics.numberProgress(setting);
    }

    private String formatNumber(NumberSetting setting, boolean size) {
        return ScoreboardStyleMenuMetrics.formatNumber(setting, size);
    }

    private void place(boolean resolveSide) {
        ScoreboardStyleMenuPlacement.MenuPlacement placement = ScoreboardStyleMenuPlacement.resolveMenu(
                scoreboard, WIDTH, popupHeight(), GAP, MARGIN, side, resolveSide
        );
        side = placement.side();
        x = placement.x();
        y = placement.y();
    }

    private static int alpha(int color, float a) {
        int value = Math.round(((color >>> 24) & 0xFF) * clamp01(a));
        return (color & 0x00FFFFFF) | (value << 24);
    }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
    private static float clamp01(float value) { return Math.max(0.0F, Math.min(1.0F, value)); }
    private static boolean inside(float px, float py, float x, float y, float width, float height) { return px >= x && px <= x + width && py >= y && py <= y + height; }

}
