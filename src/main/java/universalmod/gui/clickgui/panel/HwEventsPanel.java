package universalmod.gui.clickgui.panel;

import universalmod.service.language.LanguageManager;
import net.minecraft.client.gui.DrawContext;
import universalmod.gui.clickgui.CharacterEvent;
import universalmod.gui.clickgui.KeyEvent;
import universalmod.gui.clickgui.MouseButtonEvent;
import universalmod.hud.element.CurrentEventsPanel;
import universalmod.render.animation.Easings;
import universalmod.render.animation.SmoothAnimation;
import universalmod.render.color.ColorUtil;
import universalmod.service.AnarchySwitcher;
import universalmod.render.Render2D;
import universalmod.render.font.FontType;
import universalmod.render.surface.GuiSurface;
import universalmod.theme.ThemeColors;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class HwEventsPanel {
    private static final List<String> FILTER_KEYS = List.of("hw_events.filter.all", "hw_events.filter.solo", "hw_events.filter.duo", "hw_events.filter.trio", "hw_events.filter.clan");
    private static final List<String> RANGE_KEYS = List.of("Solo", "Duo", "Trio", "Clan");
    private static final float TOOLBAR_BUTTON_SCALE = 1.43f;
    private static final float TOOLBAR_HEIGHT = 15.333333f * TOOLBAR_BUTTON_SCALE;
    private static final float TOOLBAR_BUTTON_WIDTH = 48.0f * TOOLBAR_BUTTON_SCALE;
    private static final float TOOLBAR_BUTTON_GAP = 3.5f;
    private static final float SIDE_PADDING = 16.0f;
    private static final float TOP_PADDING = 1.0f;
    private static final float CARD_GAP_X = 14.0f;
    private static final float CARD_GAP_Y = 8.5f;
    private static final float CARD_HEIGHT = 35.1f;
    private static final int COLUMNS = 3;
    private static final float EVENT_PLAY_BUTTON_SIZE = 12.0f;
    private static final float EVENT_PLAY_BUTTON_RIGHT_PADDING = 7.0f;
    private static final float EVENT_PLAY_ALPHA_LEFT = 1.0f / 12.0f;
    private static final float EVENT_PLAY_ALPHA_TOP = 1.0f / 12.0f;
    private static final float EVENT_PLAY_ALPHA_RIGHT = 10.0f / 12.0f;
    private static final float EVENT_PLAY_ALPHA_BOTTOM = 11.0f / 12.0f;
    private static final String PLAY_ICON = "music_player/play.png";

    private final CurrentEventsPanel events = CurrentEventsPanel.getInstance();
    private final SmoothAnimation scrollAnimation = new SmoothAnimation();
    private final HwEventsPopup popup = new HwEventsPopup(events);
    private final List<EventPlayHitTarget> playHitTargets = new ArrayList<>();
    private int playHitTargetCount;
    private final List<CurrentEventsPanel.EventSnapshot> filteredScratch = new ArrayList<>();
    private List<CurrentEventsPanel.EventSnapshot> filteredSource = List.of();
    private List<CurrentEventsPanel.EventSnapshot> filteredCache = List.of();
    private String filteredNeedle = "";
    private int filteredSelected = -1;
    private String query = "";
    private int selectedFilter;
    private float scrollTarget;
    private float scrollLimit;
    private float panelX;
    private float panelY;
    private float panelWidth;
    private float panelHeight;
    private float scale = 1.0f;

    public void resetInteraction() {
        popup.reset();
        playHitTargetCount = 0;
    }

    public void setSearchQuery(String query) {
        this.query = query == null ? "" : query;
    }

    public void render(DrawContext graphics, float x, float y, float width, float height,
                float scale, float alpha, double mouseX, double mouseY) {
        this.panelX = x;
        this.panelY = y;
        this.panelWidth = width;
        this.panelHeight = height;
        this.scale = scale;
        popup.updateAnchor(x, y, width, scale);

        List<CurrentEventsPanel.EventSnapshot> visible = filteredEvents();
        updateScroll(visible.size());
        renderToolbar(graphics, alpha, mouseX, mouseY);
        renderCards(graphics, visible, alpha);
    }

    public void renderOverlay(DrawContext graphics, float alpha, double mouseX, double mouseY) {
        popup.render(graphics, alpha, mouseX, mouseY);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (popup.isVisible()) {
            if (popup.mouseClicked(event)) {
                return true;
            }
            if (!popup.contains(event.x(), event.y())) {
                popup.close();
            }
            return true;
        }

        float toolbarY = panelY + TOP_PADDING * scale;
        float buttonWidth = toolbarButtonWidth();
        float gap = TOOLBAR_BUTTON_GAP * scale;
        float toolbarX = toolbarStartX();
        if (event.button() == 0 && inside(event.x(), event.y(), toolbarX, toolbarY,
                buttonWidth, TOOLBAR_HEIGHT * scale)) {
            popup.open();
            return true;
        }

        if (event.button() == 0) {
            for (int i = 0; i < FILTER_KEYS.size(); i++) {
                float x = toolbarX + (i + 1) * (buttonWidth + gap);
                if (inside(event.x(), event.y(), x, toolbarY, buttonWidth, TOOLBAR_HEIGHT * scale)) {
                    selectedFilter = i;
                    scrollTarget = 0.0f;
                    return true;
                }
            }

            for (int i = 0; i < playHitTargetCount; i++) {
                EventPlayHitTarget target = playHitTargets.get(i);
                if (!target.contains(event.x(), event.y())) {
                    continue;
                }
                if (doubled && target.anarchyNumber >= 0) {
                    AnarchySwitcher.getInstance().start(target.anarchyNumber);
                }
                return true;
            }
        }
        return false;
    }

    public boolean keyPressed(KeyEvent event) {
        return popup.keyPressed(event);
    }

    public boolean charTyped(CharacterEvent event) {
        return popup.charTyped(event);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (popup.isVisible()) return true;
        if (!inside(mouseX, mouseY, panelX, panelY, panelWidth, panelHeight) || scrollLimit <= 0.0f) {
            return false;
        }
        scrollTarget = clamp(scrollTarget - (float) scrollY * 27.0f, 0.0f, scrollLimit);
        return true;
    }

    boolean hasOpenPopup() {
        return popup.isVisible();
    }

    private void renderToolbar(DrawContext graphics, float alpha, double mouseX, double mouseY) {
        float y = panelY + TOP_PADDING * scale;
        float startX = toolbarStartX();
        float buttonWidth = toolbarButtonWidth();
        float gap = TOOLBAR_BUTTON_GAP * scale;
        float height = TOOLBAR_HEIGHT * scale;
        int buttonCount = FILTER_KEYS.size() + 1;
        for (int i = 0; i < buttonCount; i++) {
            float x = startX + i * (buttonWidth + gap);
            String label = i == 0 ? LanguageManager.translate("hw_events.settings_button") : LanguageManager.translate(FILTER_KEYS.get(i - 1));
            boolean hovered = inside(mouseX, mouseY, x, y, buttonWidth, height);
            if (i == 0) {
                float radius = 4.5f * TOOLBAR_BUTTON_SCALE * scale;
                new GuiSurface(
                        x, y, buttonWidth, height, radius,
                        alpha * 0.99f, hovered ? 0.54f : 0.42f,
                        ColorUtil.rgba(34, 30, 43, 255),
                        ThemeColors.clickGuiAccentColor(255, 1.0f)
                ).render(graphics);
                Render2D.rect(x, y, buttonWidth, height, radius,
                        color(103, 76, 166, hovered ? 88 : 68, alpha));
                Render2D.outline(x, y, buttonWidth, height, radius, 0.42f * scale,
                        ThemeColors.clickGuiAccentColor(hovered ? 126 : 92, alpha));
                drawCentered(label, x, y, buttonWidth, height,
                        4.866667f * TOOLBAR_BUTTON_SCALE * scale,
                        color(252, 251, 255, 250, alpha));
                continue;
            }

            boolean selected = selectedFilter == i - 1;
            float radius = 4.0f * TOOLBAR_BUTTON_SCALE * scale;


            new GuiSurface(
                    x, y, buttonWidth, height, radius,
                    alpha * 0.99f,
                    selected ? 0.34f : hovered ? 0.22f : 0.12f,
                    ColorUtil.rgba(selected ? 29 : 22, selected ? 26 : 21, selected ? 35 : 24, 255),
                    ColorUtil.rgba(selected ? 170 : 242, selected ? 136 : 241, selected ? 246 : 245, 255)
            ).render(graphics);
            Render2D.outline(x, y, buttonWidth, height, radius,
                    (selected ? 0.58f : 0.34f) * scale,
                    selected ? ThemeColors.clickGuiAccentColor(202, alpha)
                            : color(244, 242, 248, hovered ? 54 : 34, alpha));
            drawCentered(label, x, y, buttonWidth, height,
                    4.8f * TOOLBAR_BUTTON_SCALE * scale,
                    color(244, 244, 249, selected ? 255 : hovered ? 246 : 230, alpha));
        }
    }

    private float toolbarButtonWidth() {
        int buttonCount = FILTER_KEYS.size() + 1;
        float gap = TOOLBAR_BUTTON_GAP * scale;
        float desired = TOOLBAR_BUTTON_WIDTH * scale;
        float available = Math.max(1.0f, panelWidth - 18.0f * scale - gap * (buttonCount - 1));
        return Math.min(desired, available / buttonCount);
    }

    private float toolbarStartX() {
        int buttonCount = FILTER_KEYS.size() + 1;
        float buttonWidth = toolbarButtonWidth();
        float totalWidth = buttonCount * buttonWidth
                + (buttonCount - 1) * TOOLBAR_BUTTON_GAP * scale;
        return panelX + (panelWidth - totalWidth) * 0.5f;
    }

    private void renderCards(DrawContext graphics, List<CurrentEventsPanel.EventSnapshot> visible, float alpha) {
        playHitTargetCount = 0;
        float cardsX = panelX + SIDE_PADDING * scale;
        float cardsY = panelY + (TOP_PADDING + TOOLBAR_HEIGHT + CARD_GAP_Y) * scale;
        float bottom = panelY + panelHeight - 1.0f * scale;
        float cardWidth = (panelWidth - (SIDE_PADDING * 2.0f + CARD_GAP_X * (COLUMNS - 1)) * scale) / COLUMNS;
        float cardHeight = CARD_HEIGHT * scale;
        Render2D.pushScissor(graphics, panelX, cardsY, panelWidth, Math.max(1.0f, bottom - cardsY));
        try {
            if (visible.isEmpty()) {
                String empty = LanguageManager.translate("current_events.empty");
                float size = 7.2f * scale;
                float width = Render2D.textWidth(FontType.SEMIBOLD, empty, size);
                Render2D.text(FontType.SEMIBOLD, empty, panelX + (panelWidth - width) * 0.5f,
                        cardsY + 12.0f * scale, size, color(170, 170, 188, 220, alpha));
                return;
            }
            for (int i = 0; i < visible.size(); i++) {
                int row = i / COLUMNS;
                int col = i % COLUMNS;
                float x = cardsX + col * (cardWidth + CARD_GAP_X * scale);
                float y = cardsY + row * (cardHeight + CARD_GAP_Y * scale) - scrollAnimation.get() * scale;
                if (y + cardHeight < cardsY || y > bottom) continue;
                float playSize = EVENT_PLAY_BUTTON_SIZE * scale;
                float playX = x + cardWidth - playSize - EVENT_PLAY_BUTTON_RIGHT_PADDING * scale;
                float playY = y + (cardHeight - playSize) * 0.5f;
                float visibleLeft = playX + playSize * EVENT_PLAY_ALPHA_LEFT;
                float visibleRight = playX + playSize * EVENT_PLAY_ALPHA_RIGHT;
                float visibleTop = Math.max(playY + playSize * EVENT_PLAY_ALPHA_TOP, cardsY);
                float visibleBottom = Math.min(playY + playSize * EVENT_PLAY_ALPHA_BOTTOM, bottom);
                if (visibleBottom > visibleTop) {
                    setPlayHitTarget(playHitTargetCount++, visible.get(i).anarchyNumber(),
                            visibleLeft, visibleTop, visibleRight - visibleLeft, visibleBottom - visibleTop);
                }
                renderEventCard(graphics, visible.get(i), x, y, cardWidth, cardHeight, alpha);
            }
        } finally {
            Render2D.popScissor(graphics);
        }
    }

    private void renderEventCard(DrawContext graphics, CurrentEventsPanel.EventSnapshot event, float x, float y,
                                 float width, float height, float alpha) {
        float radius = 6.0f * scale;
        Render2D.rect(x, y, width, height, radius, color(7, 10, 21, 56, alpha));
        Render2D.outline(x, y, width, height, radius, 0.4f * scale,
                color(133, 119, 198, 22, alpha));
        float titleSize = 7.4f * scale;
        float detailsSize = 6.96f * scale;
        float left = x + 7.0f * scale;
        float playSize = EVENT_PLAY_BUTTON_SIZE * scale;
        float playX = x + width - playSize - EVENT_PLAY_BUTTON_RIGHT_PADDING * scale;
        float playY = y + (height - playSize) * 0.5f;
        float contentRight = playX - 5.0f * scale;
        Render2D.pushScissor(graphics, left, y, Math.max(1.0f, contentRight - left), height);
        try {
            String name = event.displayName();
            Render2D.text(FontType.BOLD, name, left, y + 6.0f * scale, titleSize,
                    withAlpha(event.color(), 0.98f, alpha));
            if (event.anarchyNumber() >= 0) {
                String tag = "#" + event.anarchyNumber();
                float tagX = left + Render2D.textWidth(FontType.BOLD, name, titleSize) + 3.0f * scale;
                Render2D.text(FontType.BOLD, tag, tagX, y + 6.0f * scale, titleSize,
                        color(216, 102, 31, 252, alpha));
            }
            String rarity = event.rarity().isBlank() ? "Обычный" : event.rarity();
            Render2D.text(FontType.SEMIBOLD, rarity, left, y + 21.5f * scale, detailsSize,
                    rarityColor(rarity, alpha));
        } finally {
            Render2D.popScissor(graphics);
        }
        Render2D.image(PLAY_ICON, playX, playY,
                playSize, playSize, 0.0f,
                color(255, 255, 255, 230, alpha));
    }

    private void setPlayHitTarget(int index, int anarchyNumber, float x, float y, float width, float height) {
        EventPlayHitTarget target;
        if (index < playHitTargets.size()) {
            target = playHitTargets.get(index);
        } else {
            target = new EventPlayHitTarget();
            playHitTargets.add(target);
        }
        target.set(anarchyNumber, x, y, width, height);
    }

    private List<CurrentEventsPanel.EventSnapshot> filteredEvents() {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<CurrentEventsPanel.EventSnapshot> source = events.visibleEventSnapshots();
        if (source == filteredSource && selectedFilter == filteredSelected && needle.equals(filteredNeedle)) {
            return filteredCache;
        }

        filteredScratch.clear();
        String rangeKey = selectedFilter > 0 ? RANGE_KEYS.get(selectedFilter - 1) : null;
        for (CurrentEventsPanel.EventSnapshot event : source) {
            if (rangeKey != null && !events.matchesAnarchyRange(rangeKey, event.anarchyNumber())) {
                continue;
            }
            if (!needle.isEmpty()) {
                String displayName = event.displayName();
                String id = event.id();
                String rarity = event.rarity();
                String haystack = ((displayName == null ? "" : displayName) + " "
                        + (id == null ? "" : id) + " "
                        + (rarity == null ? "" : rarity) + " "
                        + event.anarchyNumber()).toLowerCase(Locale.ROOT);
                if (!haystack.contains(needle)) continue;
            }
            filteredScratch.add(event);
        }
        filteredScratch.sort(Comparator.comparingInt(CurrentEventsPanel.EventSnapshot::anarchyNumber)
                .thenComparing(CurrentEventsPanel.EventSnapshot::displayName, String.CASE_INSENSITIVE_ORDER));
        filteredSource = source;
        filteredSelected = selectedFilter;
        filteredNeedle = needle;
        filteredCache = List.copyOf(filteredScratch);
        return filteredCache;
    }

    private void updateScroll(int count) {
        int rows = (count + COLUMNS - 1) / COLUMNS;
        float cardsHeight = rows <= 0 ? 0.0f : rows * CARD_HEIGHT + Math.max(0, rows - 1) * CARD_GAP_Y;
        float viewport = panelHeight / Math.max(0.0001f, scale)
                - TOP_PADDING - TOOLBAR_HEIGHT - CARD_GAP_Y - 1.0f;
        scrollLimit = Math.max(0.0f, cardsHeight - viewport);
        scrollTarget = clamp(scrollTarget, 0.0f, scrollLimit);
        scrollAnimation.run(scrollTarget, 0.22, Easings.CUBIC_OUT, true);
        scrollAnimation.update();
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
        return ColorUtil.rgba(r, g, b, Math.round(a * Math.max(0.0f, Math.min(1.0f, alpha))));
    }

    private static int withAlpha(int value, float opacity, float alpha) {
        int sourceAlpha = (value >>> 24) & 0xFF;
        int resultAlpha = Math.round(sourceAlpha * opacity * Math.max(0.0f, Math.min(1.0f, alpha)));
        return (resultAlpha << 24) | (value & 0x00FFFFFF);
    }

    private static int rarityColor(String rarity, float alpha) {
        String value = rarity == null ? "" : rarity.trim().toLowerCase(Locale.ROOT);
        if (containsAny(value, "миф", "myth", "кров", "адск")) {
            return color(255, 72, 88, 245, alpha);
        }
        if (containsAny(value, "легендар", "legend", "смертель", "deadly", "роскош", "luxur")) {
            return color(255, 205, 72, 245, alpha);
        }
        if (containsAny(value, "эпич", "epic", "зажит", "взрыв", "explosive")) {
            return color(180, 104, 255, 245, alpha);
        }
        if (containsAny(value, "обыч", "normal", "default", "редк", "rare", "мирн", "peaceful")) {
            return color(92, 196, 255, 245, alpha);
        }
        return color(92, 196, 255, 235, alpha);
    }

    private static boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) return true;
        }
        return false;
    }

    private static boolean inside(double mouseX, double mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }


    private static final class EventPlayHitTarget {
        private int anarchyNumber;
        private float x;
        private float y;
        private float width;
        private float height;

        private void set(int anarchyNumber, float x, float y, float width, float height) {
            this.anarchyNumber = anarchyNumber;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        private boolean contains(double mouseX, double mouseY) {
            return inside(mouseX, mouseY, x, y, width, height);
        }
    }
}
