package universalmod.gui.clickgui.panel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import universalmod.hud.element.CurrentEventsPanel;
import universalmod.render.Render2D;
import universalmod.render.animation.Easings;
import universalmod.render.animation.SmoothAnimation;
import universalmod.render.color.ColorUtil;
import universalmod.render.font.FontType;
import universalmod.render.optimize.UiFrameContext;
import universalmod.render.surface.GuiSurface;
import universalmod.service.language.LanguageManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


final class HwEventsPopup {
    private static final List<String> RANGE_KEYS = List.of("Solo", "Duo", "Trio", "Clan");
    private static final float POPUP_WIDTH = 282.0f;
    private static final float POPUP_HEIGHT = 166.0f;
    private static final float POPUP_CHIP_TEXT_SIZE = 6.65f;
    private static final float POPUP_CHIP_HEIGHT = 16.0f;
    private static final float POPUP_CHIP_GAP_X = 5.0f;
    private static final float POPUP_CHIP_GAP_Y = 4.5f;
    private static final int POPUP_CHIP_COLUMNS = 3;
    private static final float POPUP_RANGE_HEIGHT = 16.0f;
    private static final float POPUP_RANGE_INPUT_WIDTH = 43.0f;
    private static final float POPUP_TOP_OFFSET = 1.0f + (15.333333f * 1.43f) + 5.0f;

    private final CurrentEventsPanel events;
    private final SmoothAnimation animation = new SmoothAnimation();
    private final Map<String, SmoothAnimation> filterAnimations = new LinkedHashMap<>();
    private final Map<String, String> rangeDrafts = new LinkedHashMap<>();
    private final List<FilterChip> filterChipScratch = new ArrayList<>();
    private final List<RangeField> rangeFieldCache = new ArrayList<>(RANGE_KEYS.size());

    private List<CurrentEventsPanel.EventFilterSnapshot> filterLayoutSource = List.of();
    private PopupFilterLayout filterLayoutCache = new PopupFilterLayout(List.of(), 0.0f);
    private float filterLayoutLeft = Float.NaN;
    private float filterLayoutRight = Float.NaN;
    private float filterLayoutTop = Float.NaN;
    private float filterLayoutScale = Float.NaN;
    private float rangeLayoutLeft = Float.NaN;
    private float rangeLayoutRight = Float.NaN;
    private float rangeLayoutY = Float.NaN;
    private float rangeLayoutScale = Float.NaN;

    private boolean open;
    private int focusedRange = -1;
    private float panelX;
    private float panelY;
    private float panelWidth;
    private float scale = 1.0f;
    private float popupX;
    private float popupY;
    private float popupWidth;
    private float popupHeight;

    HwEventsPopup(CurrentEventsPanel events) {
        this.events = events;
    }

    void reset() {
        focusedRange = -1;
        open = false;
        animation.set(0.0);
    }

    void updateAnchor(float panelX, float panelY, float panelWidth, float scale) {
        this.panelX = panelX;
        this.panelY = panelY;
        this.panelWidth = panelWidth;
        this.scale = scale;
        updateBounds();
    }

    void render(GuiGraphics graphics, float alpha, double mouseX, double mouseY) {
        animation.run(open ? 1.0 : 0.0, 0.18, Easings.CUBIC_OUT, true);
        animation.update();
        float progress = animation.get();
        if (progress <= 0.01f) {
            return;
        }

        float popupAlpha = alpha * progress;
        updateBounds();
        renderSurface(graphics, alpha, popupAlpha);
        PopupFilterLayout filterLayout = renderFilters(popupAlpha, mouseX, mouseY);
        renderRanges(popupAlpha, filterLayout.bottom());
    }

    boolean isVisible() {
        return open || animation.get() > 0.01f;
    }

    boolean contains(double mouseX, double mouseY) {
        return inside(mouseX, mouseY, popupX, popupY, popupWidth, popupHeight);
    }

    void open() {
        open = true;
        focusedRange = -1;
        rangeDrafts.clear();
        for (String key : RANGE_KEYS) {
            rangeDrafts.put(key, events.anarchyRange(key));
        }
        animation.run(1.0, 0.18, Easings.CUBIC_OUT);
    }

    void close() {
        commitFocusedRange();
        for (String key : RANGE_KEYS) {
            String value = rangeDrafts.get(key);
            if (CurrentEventsPanel.isValidAnarchyRange(value)) {
                events.setAnarchyRange(key, value);
            } else {
                rangeDrafts.put(key, events.anarchyRange(key));
            }
        }
        focusedRange = -1;
        open = false;
        animation.run(0.0, 0.18, Easings.CUBIC_OUT);
    }

    boolean mouseClicked(MouseButtonEvent event) {
        if (event.button() != 0 || !contains(event.x(), event.y())) {
            return false;
        }
        List<CurrentEventsPanel.EventFilterSnapshot> filters = events.filterSnapshots();
        float left = popupX + 14.0f * scale;
        float right = popupX + popupWidth - 14.0f * scale;
        PopupFilterLayout filterLayout = filterLayout(filters, left, right, popupY + 25.0f * scale);
        for (FilterChip chip : filterLayout.chips()) {
            if (inside(event.x(), event.y(), chip.x(), chip.y(), chip.width(), chip.height())) {
                events.toggleFilter(chip.filter().key());
                return true;
            }
        }
        float fieldsY = filterLayout.bottom() + 26.0f * scale;
        for (RangeField field : rangeFieldLayout(left, right, fieldsY)) {
            if (inside(event.x(), event.y(), field.inputX(), field.inputY(), field.inputWidth(), field.inputHeight())) {
                commitFocusedRange();
                focusedRange = field.index();
                return true;
            }
        }
        commitFocusedRange();
        focusedRange = -1;
        return true;
    }

    boolean keyPressed(KeyEvent event) {
        if (!isVisible()) {
            return false;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (focusedRange >= 0) {
                commitFocusedRange();
                focusedRange = -1;
            } else {
                close();
            }
            return true;
        }
        if (focusedRange >= 0) {
            String key = RANGE_KEYS.get(focusedRange);
            String value = rangeDrafts.getOrDefault(key, "");
            if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                if (!value.isEmpty()) {
                    rangeDrafts.put(key, value.substring(0, value.length() - 1));
                }
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_DELETE) {
                rangeDrafts.put(key, "");
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                commitFocusedRange();
                focusedRange = -1;
                return true;
            }
        }
        return true;
    }

    boolean charTyped(CharacterEvent event) {
        if (focusedRange < 0 || !isVisible()) {
            return false;
        }
        String text = event.codepointAsString();
        if (text != null && text.length() == 1) {
            char character = text.charAt(0);
            String key = RANGE_KEYS.get(focusedRange);
            String value = rangeDrafts.getOrDefault(key, "");
            boolean asciiDigit = character >= '0' && character <= '9';
            if ((asciiDigit || character == '-') && value.length() < 7
                    && (character != '-' || !value.contains("-"))) {
                rangeDrafts.put(key, value + character);
            }
        }
        return true;
    }

    private void updateBounds() {
        popupWidth = POPUP_WIDTH * scale;
        popupHeight = POPUP_HEIGHT * scale;
        popupX = panelX + (panelWidth - popupWidth) * 0.5f;
        popupY = panelY + POPUP_TOP_OFFSET * scale;
    }

    private void renderSurface(GuiGraphics graphics, float alpha, float popupAlpha) {
        float radius = 8.0f * scale;
        Render2D.rect(popupX, popupY, popupWidth, popupHeight, radius, color(15, 14, 19, 255, alpha));
        new GuiSurface(
                popupX, popupY, popupWidth, popupHeight, radius,
                alpha, 0.30f,
                ColorUtil.rgba(20, 19, 24, 255),
                ColorUtil.rgba(145, 112, 220, 255)
        ).render(graphics);
        Render2D.rect(popupX + 0.7f * scale, popupY + 0.7f * scale,
                popupWidth - 1.4f * scale, popupHeight - 1.4f * scale,
                Math.max(0.0f, radius - 0.7f * scale),
                color(20, 19, 24, 246, alpha));
        Render2D.outline(popupX, popupY, popupWidth, popupHeight, radius, 0.72f * scale,
                color(151, 119, 225, 112, popupAlpha));
    }

    private PopupFilterLayout renderFilters(float popupAlpha, double mouseX, double mouseY) {
        List<CurrentEventsPanel.EventFilterSnapshot> filters = events.filterSnapshots();
        int enabled = 0;
        for (CurrentEventsPanel.EventFilterSnapshot filter : filters) {
            if (filter.enabled()) {
                enabled++;
            }
        }
        float left = popupX + 14.0f * scale;
        float right = popupX + popupWidth - 14.0f * scale;
        float headerY = popupY + 10.0f * scale;
        String count = LanguageManager.translateFormat("hw_events.display_count", enabled, filters.size());
        float headerSize = 7.0f * scale;
        float headerWidth = Render2D.textWidth(FontType.BOLD, count, headerSize);
        Render2D.text(FontType.BOLD, count, popupX + (popupWidth - headerWidth) * 0.5f, headerY, headerSize,
                color(245, 245, 252, 250, popupAlpha));

        PopupFilterLayout layout = filterLayout(filters, left, right, popupY + 25.0f * scale);
        for (FilterChip chip : layout.chips()) {
            renderFilterChip(chip, popupAlpha, mouseX, mouseY);
        }
        return layout;
    }

    private void renderFilterChip(FilterChip chip, float popupAlpha, double mouseX, double mouseY) {
        CurrentEventsPanel.EventFilterSnapshot filter = chip.filter();
        boolean hovered = inside(mouseX, mouseY, chip.x(), chip.y(), chip.width(), chip.height());
        SmoothAnimation selectionAnimation = filterAnimations.computeIfAbsent(filter.key(), key -> {
            SmoothAnimation selection = new SmoothAnimation();
            selection.set(filter.enabled() ? 1.0 : 0.0);
            return selection;
        });
        selectionAnimation.run(filter.enabled() ? 1.0 : 0.0, 0.16, Easings.CIRC_IN, true);
        selectionAnimation.update();
        float selection = clamp(selectionAnimation.get(), 0.0f, 1.0f);
        int fill = ColorUtil.lerpColor(
                color(47, 48, 57, hovered ? 224 : 205, popupAlpha),
                color(111, 92, 166, hovered ? 238 : 220, popupAlpha), selection);
        int outline = ColorUtil.lerpColor(
                color(109, 111, 124, hovered ? 110 : 78, popupAlpha),
                color(174, 151, 235, hovered ? 154 : 120, popupAlpha), selection);
        Render2D.rect(chip.x(), chip.y(), chip.width(), chip.height(), 3.0f * scale, fill);
        Render2D.outline(chip.x(), chip.y(), chip.width(), chip.height(), 3.0f * scale, 0.55f * scale, outline);
        drawCentered(filter.label(), chip.x(), chip.y(), chip.width(), chip.height(), POPUP_CHIP_TEXT_SIZE * scale,
                ColorUtil.lerpColor(
                        color(185, 187, 205, hovered ? 245 : 230, popupAlpha),
                        color(255, 255, 255, 245, popupAlpha), selection));
    }

    private void renderRanges(float popupAlpha, float filterBottom) {
        float left = popupX + 14.0f * scale;
        float right = popupX + popupWidth - 14.0f * scale;
        float separatorY = filterBottom + 7.0f * scale;
        Render2D.rect(left + 1.0f * scale, separatorY, right - left - 2.0f * scale, 0.5f * scale, 0.0f,
                color(122, 113, 151, 62, popupAlpha));
        float sectionY = separatorY + 7.0f * scale;
        String sectionLabel = LanguageManager.translate("hw_events.anarchies");
        float sectionSize = 7.0f * scale;
        float sectionWidth = Render2D.textWidth(FontType.BOLD, sectionLabel, sectionSize);
        Render2D.text(FontType.BOLD, sectionLabel, popupX + (popupWidth - sectionWidth) * 0.5f, sectionY, sectionSize,
                color(239, 239, 247, 245, popupAlpha));

        float fieldsY = sectionY + 12.0f * scale;
        for (RangeField field : rangeFieldLayout(left, right, fieldsY)) {
            renderRangeField(field, popupAlpha);
        }
    }

    private void renderRangeField(RangeField field, float popupAlpha) {
        int index = field.index();
        String key = field.key();
        boolean focused = focusedRange == index;
        String value = rangeDrafts.getOrDefault(key, events.anarchyRange(key));
        boolean valid = CurrentEventsPanel.isValidAnarchyRange(value);
        drawCentered(LanguageManager.translate("hw_events.range." + key.toLowerCase(Locale.ROOT)),
                field.groupX(), field.labelY(), field.groupWidth(), 7.0f * scale, 6.0f * scale,
                color(189, 191, 205, 236, popupAlpha));
        Render2D.rect(field.inputX(), field.inputY(), field.inputWidth(), field.inputHeight(), 3.0f * scale,
                focused ? color(99, 83, 148, 232, popupAlpha) : color(48, 49, 57, 218, popupAlpha));
        Render2D.outline(field.inputX(), field.inputY(), field.inputWidth(), field.inputHeight(), 3.0f * scale, 0.55f * scale,
                valid
                        ? (focused ? color(175, 151, 235, 150, popupAlpha) : color(112, 114, 126, 86, popupAlpha))
                        : color(255, 78, 92, focused ? 205 : 135, popupAlpha));
        drawCentered(value, field.inputX(), field.inputY(), field.inputWidth(), field.inputHeight(), 6.05f * scale,
                focused ? color(255, 255, 255, 248, popupAlpha) : color(212, 214, 225, 240, popupAlpha));
        if (focused && (UiFrameContext.nowMillis() / 340L) % 2L == 0L) {
            renderRangeCaret(field, value, popupAlpha);
        }
    }

    private void renderRangeCaret(RangeField field, String value, float popupAlpha) {
        float textSize = 6.05f * scale;
        Render2D.TextVisualBounds bounds = Render2D.textVisualBounds(FontType.BOLD, value, textSize);
        float textWidth = bounds.empty() ? Render2D.textWidth(FontType.BOLD, value, textSize) : bounds.width();
        float renderedTextX = bounds.empty()
                ? field.inputX() + (field.inputWidth() - textWidth) * 0.5f
                : field.inputX() + (field.inputWidth() - bounds.width()) * 0.5f - bounds.minX();
        float renderedTextY = bounds.empty()
                ? field.inputY() + (field.inputHeight() - textSize) * 0.5f
                : field.inputY() + (field.inputHeight() - bounds.height()) * 0.5f - bounds.minY();
        float glyphRight = bounds.empty() ? renderedTextX + textWidth : renderedTextX + bounds.maxX();
        float glyphTop = bounds.empty() ? renderedTextY : renderedTextY + bounds.minY();
        float glyphHeight = bounds.empty() ? textSize : bounds.height();
        float caretX = Math.min(glyphRight + 1.0f * scale, field.inputX() + field.inputWidth() - 2.0f * scale);
        float caretY = Math.max(field.inputY() + 2.0f * scale, glyphTop - 0.45f * scale);
        float caretBottom = Math.min(field.inputY() + field.inputHeight() - 2.0f * scale,
                glyphTop + glyphHeight + 0.45f * scale);
        Render2D.rect(caretX, caretY, 0.75f * scale, Math.max(4.0f * scale, caretBottom - caretY),
                0.22f * scale, color(255, 255, 255, 238, popupAlpha));
    }

    private PopupFilterLayout filterLayout(
            List<CurrentEventsPanel.EventFilterSnapshot> filters,
            float left,
            float right,
            float top
    ) {
        if (filters == filterLayoutSource
                && left == filterLayoutLeft && right == filterLayoutRight
                && top == filterLayoutTop && scale == filterLayoutScale) {
            return filterLayoutCache;
        }

        filterChipScratch.clear();
        float availableWidth = Math.max(1.0f, right - left);
        int columns = Math.min(POPUP_CHIP_COLUMNS, Math.max(1, filters.size()));
        float gapX = POPUP_CHIP_GAP_X * scale;
        float gapY = POPUP_CHIP_GAP_Y * scale;
        float chipWidth = (availableWidth - gapX * (columns - 1)) / columns;
        float height = POPUP_CHIP_HEIGHT * scale;
        int rows = (filters.size() + columns - 1) / columns;
        for (int row = 0; row < rows; row++) {
            int rowStart = row * columns;
            int count = Math.min(columns, filters.size() - rowStart);
            float rowWidth = count * chipWidth + (count - 1) * gapX;
            float rowX = left + (availableWidth - rowWidth) * 0.5f;
            float y = top + row * (height + gapY);
            for (int col = 0; col < count; col++) {
                int index = rowStart + col;
                float x = rowX + col * (chipWidth + gapX);
                filterChipScratch.add(new FilterChip(filters.get(index), x, y, chipWidth, height));
            }
        }

        float bottom = rows <= 0 ? top : top + rows * height + Math.max(0, rows - 1) * gapY;
        filterLayoutSource = filters;
        filterLayoutLeft = left;
        filterLayoutRight = right;
        filterLayoutTop = top;
        filterLayoutScale = scale;
        filterLayoutCache = new PopupFilterLayout(List.copyOf(filterChipScratch), bottom);
        return filterLayoutCache;
    }

    private List<RangeField> rangeFieldLayout(float left, float right, float y) {
        if (left == rangeLayoutLeft && right == rangeLayoutRight && y == rangeLayoutY && scale == rangeLayoutScale) {
            return rangeFieldCache;
        }

        rangeFieldCache.clear();
        float totalWidth = Math.max(1.0f, right - left);
        float groupGap = 6.0f * scale;
        float groupWidth = (totalWidth - groupGap * (RANGE_KEYS.size() - 1)) / RANGE_KEYS.size();
        float inputWidth = Math.min(POPUP_RANGE_INPUT_WIDTH * scale, groupWidth - 4.0f * scale);
        float labelY = y;
        float inputY = y + 8.5f * scale;
        float inputHeight = POPUP_RANGE_HEIGHT * scale;

        for (int i = 0; i < RANGE_KEYS.size(); i++) {
            String key = RANGE_KEYS.get(i);
            float groupX = left + i * (groupWidth + groupGap);
            float inputX = groupX + (groupWidth - inputWidth) * 0.5f;
            rangeFieldCache.add(new RangeField(i, key, groupX, groupWidth, labelY,
                    inputX, inputWidth, inputY, inputHeight));
        }
        rangeLayoutLeft = left;
        rangeLayoutRight = right;
        rangeLayoutY = y;
        rangeLayoutScale = scale;
        return rangeFieldCache;
    }

    private void commitFocusedRange() {
        if (focusedRange < 0 || focusedRange >= RANGE_KEYS.size()) {
            return;
        }
        String key = RANGE_KEYS.get(focusedRange);
        String value = rangeDrafts.getOrDefault(key, "");
        if (events.setAnarchyRange(key, value)) {
            rangeDrafts.put(key, events.anarchyRange(key));
        }
    }

    private void drawCentered(String text, float x, float y, float width, float height, float size, int color) {
        float textWidth = Render2D.textWidth(FontType.BOLD, text, size);
        Render2D.TextVisualBounds bounds = Render2D.textVisualBounds(FontType.BOLD, text, size);
        float textX = bounds.empty()
                ? x + (width - textWidth) * 0.5f
                : x + (width - bounds.width()) * 0.5f - bounds.minX();
        float textY = bounds.empty()
                ? y + (height - size) * 0.5f
                : y + (height - bounds.height()) * 0.5f - bounds.minY();
        Render2D.text(FontType.BOLD, text, textX, textY, size, color);
    }

    private static int color(int r, int g, int b, int a, float alpha) {
        return ColorUtil.rgba(r, g, b, Math.round(a * clamp(alpha, 0.0f, 1.0f)));
    }

    private static boolean inside(double mouseX, double mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private record FilterChip(CurrentEventsPanel.EventFilterSnapshot filter,
                              float x, float y, float width, float height) {
    }

    private record PopupFilterLayout(List<FilterChip> chips, float bottom) {
    }

    private record RangeField(int index, String key, float groupX, float groupWidth, float labelY,
                              float inputX, float inputWidth, float inputY, float inputHeight) {
    }
}
