package universalmod.hud.element;

import universalmod.hud.editor.EventMenuScreen;
import universalmod.hud.editor.HudPanel;
import net.minecraft.client.MinecraftClient;
import universalmod.hud.HudMouseEvent;
import net.minecraft.client.gui.screen.Screen;
import universalmod.module.render.Hud;
import universalmod.service.language.LanguageManager;
import universalmod.render.color.ColorUtil;
import universalmod.render.Render2D;
import universalmod.render.font.FontType;

import java.util.List;

public final class CurrentEventsPanel extends HudPanel {
    private static final float PANEL_WIDTH = 154.0F;
    private static final float PANEL_MIN_HEIGHT = 77.0F;
    private static final float DEFAULT_X = 18.0F;
    private static final float DEFAULT_Y = 18.0F;
    private static final float TITLE_HEIGHT = 18.0F;
    private static final float PANEL_GAP = 3.0F;
    private static final float BODY_MIN_HEIGHT = PANEL_MIN_HEIGHT - TITLE_HEIGHT - PANEL_GAP;
    private static final float CONTENT_PADDING_TOP = 4.0F;
    private static final float ROW_HEIGHT = 14.0F;
    private static final float CONTENT_PADDING_BOTTOM = 8.0F;
    private static final int MAX_VISIBLE_EVENTS = 10;
    private static final String LEGACY_NEW_ANARCHY_FILTER_LABEL = "1.20 Events";
    private static final int TITLE_COLOR = 0xFFF3F6FA;
    private static final int EMPTY_COLOR = 0xFF96A0AA;
    private static final int TAG_COLOR = 0xFFE7EDF3;
    private static final float EVENT_TEXT_SIZE = 6.8F;
    private static final float GEAR_SIZE = 9.0F;
    private static final float TAG_COLUMN_CENTER_X = PANEL_WIDTH - 10.0F;
    private static final float GEAR_HIT_PADDING = 4.0F;
    private static final String GEAR_TEXTURE = "universalmod:textures/hud/event_gear.png";
    private static final List<FilterDefinition> FILTER_DEFINITIONS = List.of(
            new FilterDefinition("boss", "current_events.filter.boss", "Босс", 0xFFB861FF, new String[]{"босс"}),
            new FilterDefinition("typo", "current_events.filter.typo", "Тыпо", 0xFF7CFF3A, new String[]{"опытный типо", "опытный тыпо", "типо", "тыпо", "jaycob"}),
            new FilterDefinition("ship", "current_events.filter.ship", "Корабль", 0xFF8A5A2B, new String[]{"таинственный корабль", "корабль", "ship"}),
            new FilterDefinition("container", "current_events.filter.container", "Контейнер", 0xFF5DBBFF, new String[]{"контейнер", "container"}),
            new FilterDefinition("fever", "current_events.filter.fever", "Лихорадка", 0xFFF1C24A, new String[]{"золотая лихорадка", "лихорадка", "fever"}),
            new FilterDefinition("parcel", "current_events.filter.parcel", "Посылка", 0xFF8A5A2B, new String[]{"посылка", "parcel"}),
            new FilterDefinition("cargo", "current_events.filter.cargo", "Груз", 0xFFF34B4B, new String[]{"ценный груз", "груз", "cargo"}),
            new FilterDefinition("flower_glade", "current_events.filter.flower_glade", "Цветочная поляна", 0xFF4CAF50, new String[]{"цветочная поляна", "snowquarry", "glade"}),
            new FilterDefinition("deadly_mine", "current_events.filter.deadly_mine", "Смертельная шахта", 0xFF8B1E1E, new String[]{"смертельная шахта", "mine"}),
            new FilterDefinition("cube", "current_events.filter.cube", "Кубик", 0xFF5DAEFF, new String[]{"кубик", "cube"}),
            new FilterDefinition(CurrentEventsState.NEW_ANARCHY_FILTER_ID, "current_events.filter.anarchy_120", LEGACY_NEW_ANARCHY_FILTER_LABEL, 0xFFB388FF, new String[0])
    );

    private static final CurrentEventsPanel INSTANCE = new CurrentEventsPanel();

    private final CurrentEventsState state;
    private int scrollOffset;
    private volatile CurrentEventsService service;

    public CurrentEventsPanel() {
        super("event", "Event", DEFAULT_X, DEFAULT_Y, PANEL_WIDTH, PANEL_MIN_HEIGHT);
        java.util.ArrayList<FilterEntry> filterEntries = new java.util.ArrayList<>(FILTER_DEFINITIONS.size());
        for (FilterDefinition definition : FILTER_DEFINITIONS) {
            filterEntries.add(new FilterEntry(
                    definition.id(),
                    definition.labelKey(),
                    definition.legacyLabel(),
                    definition.color(),
                    definition.aliases()
            ));
        }
        state = new CurrentEventsState(filterEntries);
    }

    public static CurrentEventsPanel getInstance() {
        return INSTANCE;
    }

    public void bindService(CurrentEventsService service) {
        this.service = service;
    }

    @Override
    protected float visualBaseScale() {
        return 1.0F;
    }

    @Override
    public void render() {


        CurrentEventsService boundService = service;
        if (boundService != null) {
            state.updateLiveEvents(boundService.events());
        }
        List<LiveEvent> visibleEvents = state.visibleEvents();
        int displayCount = Math.min(MAX_VISIBLE_EVENTS, visibleEvents.size());
        int maxScroll = Math.max(0, visibleEvents.size() - MAX_VISIBLE_EVENTS);
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
        if (scrollOffset < 0) {
            scrollOffset = 0;
        }

        float alpha = contentAlpha(true);
        if (alpha <= 0.0F) {
            return;
        }

        float panelHeight = calculatePanelHeight(displayCount, false);
        size(PANEL_WIDTH, panelHeight);

        float x = drag.x();
        float y = drag.y();
        float bodyY = y + TITLE_HEIGHT;
        float bodyHeight = Math.max(BODY_MIN_HEIGHT, panelHeight - TITLE_HEIGHT);
        String translatedTitle = LanguageManager.ui("current_events.title");
        float gearY = y + (TITLE_HEIGHT - GEAR_SIZE) * 0.5F;
        float gearX = x + TAG_COLUMN_CENTER_X - GEAR_SIZE * 0.5F;
        int panelBackgroundColor = ColorUtil.rgba(10, 12, 16, Math.round(255.0F * alpha));

        Hud.renderHudBackground(x, y, PANEL_WIDTH, panelHeight, 5.0F, 4.0F, 0.55F, panelBackgroundColor);
        HudVisualStyle.title(translatedTitle, x + 5.0F, y + 4.0F, alpha);
        Render2D.image(GEAR_TEXTURE, gearX, gearY, GEAR_SIZE, GEAR_SIZE, 0.0F, 0xFFFFFFFF);
        Render2D.rect(x + 5.0F, bodyY - 0.5F, PANEL_WIDTH - 10.0F, 0.5F, 0.0F,
                ColorUtil.rgba(255, 255, 255, Math.round(20.0F * alpha)));

        float rowY = bodyY + CONTENT_PADDING_TOP;
        if (visibleEvents.isEmpty()) {
            Render2D.text(FontType.BOLD, LanguageManager.ui("current_events.empty"), x + 5.5F, rowY + 2.0F, EVENT_TEXT_SIZE, EMPTY_COLOR);
            return;
        }

        int endIndex = Math.min(visibleEvents.size(), scrollOffset + MAX_VISIBLE_EVENTS);
        List<LiveEvent> displayedEvents = visibleEvents.subList(scrollOffset, endIndex);
        for (LiveEvent event : displayedEvents) {
            Render2D.text(FontType.BOLD, event.displayLabel(), x + 5.5F, rowY + 1.7F, EVENT_TEXT_SIZE, brighten(event.color, 0.12F));
            String apiTag = event.apiTag();
            if (!apiTag.isEmpty()) {
                float tagWidth = Render2D.textWidth(FontType.BOLD, apiTag, EVENT_TEXT_SIZE);
                Render2D.text(FontType.BOLD, apiTag, x + TAG_COLUMN_CENTER_X - tagWidth * 0.5F, rowY + 1.0F, EVENT_TEXT_SIZE, TAG_COLOR);
            }
            rowY += ROW_HEIGHT;
        }
    }

    @Override
    public boolean mouseClicked(HudMouseEvent event, boolean doubled) {
        if (event == null || event.button() != 0 || !isGearHit(event.x(), event.y())) {
            return false;
        }

        openMenu(mc == null ? null : mc.currentScreen);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!drag.containsBounds((float) mouseX, (float) mouseY, 0.0F)) {
            return false;
        }

        List<LiveEvent> visibleEvents = state.visibleEvents();
        int maxScroll = Math.max(0, visibleEvents.size() - MAX_VISIBLE_EVENTS);
        if (maxScroll <= 0) {
            return false;
        }

        int nextScroll = scrollOffset - (scrollY > 0.0D ? 1 : -1);
        int clamped = Math.max(0, Math.min(maxScroll, nextScroll));
        if (clamped == scrollOffset) {
            return false;
        }

        scrollOffset = clamped;
        return true;
    }

    public List<FilterEntry> filters() {
        return state.filters();
    }

    public List<EventFilterSnapshot> filterSnapshots() {
        return state.filterSnapshots();
    }

    public List<EventSnapshot> visibleEventSnapshots() {
        return state.visibleEventSnapshots();
    }

    public void toggleFilter(String stateKey) {
        state.toggleFilter(stateKey);
    }

    public String anarchyRange(String category) {
        return state.anarchyRange(category);
    }

    public boolean setAnarchyRange(String category, String range) {
        return state.setAnarchyRange(category, range);
    }

    public boolean matchesAnarchyRange(String category, int anarchyNumber) {
        return state.matchesAnarchyRange(category, anarchyNumber);
    }

    public static boolean isValidAnarchyRange(String range) {
        return CurrentEventsRules.isValidRange(range);
    }

    public void toggleEntry(FilterEntry entry) {
        state.toggleEntry(entry);
    }

    public boolean isEnabled(String displayName) {
        return state.isEnabled(displayName);
    }

    public void openMenu(Screen parent) {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft == null) {
            return;
        }

        minecraft.setScreen(new EventMenuScreen(parent));
    }

    private static float calculatePanelHeight(int visibleEntryCount, boolean split) {
        visibleEntryCount = Math.min(MAX_VISIBLE_EVENTS, visibleEntryCount);
        float bodyHeight = Math.max(
                BODY_MIN_HEIGHT,
                CONTENT_PADDING_TOP + (visibleEntryCount * ROW_HEIGHT) + CONTENT_PADDING_BOTTOM
        );
        return TITLE_HEIGHT + (split ? PANEL_GAP : 0.0F) + bodyHeight;
    }

    private static int brighten(int color, float amount) {
        return CurrentEventsRules.brighten(color, amount);
    }
    private static String normalize(String value) {
        return CurrentEventsRules.normalize(value);
    }

    private static boolean inside(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public boolean isGearHit(double mouseX, double mouseY) {
        float x = drag.x();
        float y = drag.y();

        float fx = localMouseX((float) mouseX);
        float fy = localMouseY((float) mouseY);
        float gearX = x + TAG_COLUMN_CENTER_X - GEAR_SIZE * 0.5F;
        float gearY = y + (TITLE_HEIGHT - GEAR_SIZE) * 0.5F;
        return inside(fx, fy,
                gearX - GEAR_HIT_PADDING,
                gearY - GEAR_HIT_PADDING,
                GEAR_SIZE + (GEAR_HIT_PADDING * 2.0F),
                GEAR_SIZE + (GEAR_HIT_PADDING * 2.0F));
    }

    public static final class FilterEntry {
        private final String id;
        private final String labelKey;
        private final String legacyLabel;
        public final int color;
        final String[] aliases;
        public boolean enabled = true;

        FilterEntry(String id, String labelKey, String legacyLabel, int color, String[] aliases) {
            this.id = id == null ? "" : id;
            this.labelKey = labelKey == null ? "" : labelKey;
            this.legacyLabel = legacyLabel == null ? "" : legacyLabel;
            this.color = color;
            this.aliases = aliases == null ? new String[0] : aliases;
        }

        public String label() {
            return LanguageManager.ui(labelKey);
        }

        String stateKey() {
            return id;
        }

        String legacyStateKey() {
            return normalize(legacyLabel);
        }

        boolean matches(String normalizedDisplayName) {
            if (normalizedDisplayName == null || normalizedDisplayName.isBlank()) {
                return false;
            }
            if (legacyStateKey().equals(normalizedDisplayName)) {
                return true;
            }
            for (String alias : aliases) {
                if (normalize(alias).equals(normalizedDisplayName)) {
                    return true;
                }
            }
            return false;
        }
    }

    private record FilterDefinition(
            String id,
            String labelKey,
            String legacyLabel,
            int color,
            String[] aliases
    ) {
    }

    public record EventFilterSnapshot(String key, String label, boolean enabled) {
    }

    public record EventSnapshot(String id, String displayName, String rarity, int anarchyNumber, int color) {
    }

    static final class LiveEvent {
        final String apiKey;
        final String id;
        final String displayName;
        final String rare;
        final int color;
        final int apiNumber;

        LiveEvent(String apiKey, String id, String displayName, String rare, int color) {
            this.apiKey = apiKey == null ? "" : apiKey;
            this.id = id == null ? "" : id;
            this.displayName = displayName == null ? "" : displayName;
            this.rare = rare == null ? "" : rare;
            this.color = color;
            this.apiNumber = parseApiNumber(this.apiKey);
        }

        String displayLabel() {
            String normalizedRare = normalizeRare(displayName, rare);
            if (normalizedRare.isEmpty()) {
                return displayName;
            }
            return displayName + " - " + normalizedRare;
        }
        private static String normalizeRare(String displayName, String rare) {
            return CurrentEventsRules.normalizeRare(displayName, rare);
        }

        String apiTag() {
            return apiNumber >= 0 ? Integer.toString(apiNumber) : "";
        }

        String rarityLabel() {
            return normalizeRare(displayName, rare);
        }
        private static int parseApiNumber(String apiKey) {
            return CurrentEventsRules.parseApiNumber(apiKey);
        }
    }
}
