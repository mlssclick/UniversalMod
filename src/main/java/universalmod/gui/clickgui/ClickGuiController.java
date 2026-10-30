package universalmod.gui.clickgui;



import universalmod.gui.ui.UiMetrics;
import universalmod.gui.clickgui.panel.HwEventsPanel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.gui.DrawContext;
import universalmod.gui.clickgui.CharacterEvent;
import universalmod.gui.clickgui.KeyEvent;
import universalmod.gui.clickgui.MouseButtonEvent;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleManager;
import universalmod.module.misc.ClickGuiModule;
import universalmod.module.misc.CustomCrosshair;
import universalmod.module.render.ItemReplacer;
import universalmod.config.ConfigManager;
import universalmod.lifecycle.Manager;
import universalmod.service.language.LanguageCode;
import universalmod.service.language.LanguageManager;
import universalmod.gui.clickgui.ClickGui;
import universalmod.gui.clickgui.panel.CustomCrosshairPanel;
import universalmod.gui.clickgui.panel.ItemReplacerPanel;
import universalmod.gui.clickgui.component.ModuleOption;
import universalmod.gui.clickgui.component.ModuleOptionFactory;
import universalmod.render.animation.Easings;
import universalmod.render.animation.SmoothAnimation;
import universalmod.render.color.ColorUtil;
import universalmod.render.item.RenderItem;
import universalmod.render.optimize.UiFrameContext;
import universalmod.render.Render2D;
import universalmod.render.Render2DCoordinateSpace;
import universalmod.render.font.FontType;
import universalmod.theme.ThemeColors;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class ClickGuiController {
    static final ClickGuiSession SESSION = new ClickGuiSession();

    static final DateTimeFormatter FOOTER_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    static final DateTimeFormatter PROFILE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    static final List<CategoryTab> TABS = List.of(
            new CategoryTab("Render", ModuleCategory.RENDER, false),
            new CategoryTab("Utils", ModuleCategory.UTILS, false),
            new CategoryTab("Misc", ModuleCategory.MISC, false),
            new CategoryTab("Hw Events", ModuleCategory.UTILS, true),
            new CategoryTab("Configs", ModuleCategory.CONFIGS, false)
    );

    static final float GUI_WIDTH = UiMetrics.GUI_WIDTH;
    static final float GUI_HEIGHT = UiMetrics.GUI_HEIGHT;
    static final float SIDE_WIDTH = UiMetrics.SIDE_WIDTH;
    static final float HEADER_HEIGHT = UiMetrics.HEADER_HEIGHT;
    static final float BODY_HEIGHT = UiMetrics.BODY_HEIGHT;
    static final float MAIN_WIDTH = UiMetrics.MAIN_WIDTH;
    static final int MODULE_COLUMNS = UiMetrics.MODULE_COLUMNS;
    static final float MODULE_PANEL_PADDING_X = UiMetrics.MODULE_PANEL_PADDING_X;
    static final float MODULE_PANEL_PADDING_Y = UiMetrics.MODULE_PANEL_PADDING_Y;
    static final float MODULE_GAP_X = UiMetrics.MODULE_GAP_X;
    static final float MODULE_GAP_Y = UiMetrics.MODULE_GAP_Y;
    static final float MODULE_WIDTH = UiMetrics.MODULE_WIDTH;
    static final float MODULE_HEIGHT = UiMetrics.MODULE_HEIGHT;
    static final float SEARCH_WIDTH = UiMetrics.SEARCH_WIDTH;
    static final float SEARCH_HEIGHT = UiMetrics.SEARCH_HEIGHT;
    static final float SEARCH_RESULTS_WIDTH = UiMetrics.SEARCH_RESULTS_WIDTH;
    static final float SEARCH_RESULTS_ROW_HEIGHT = UiMetrics.SEARCH_RESULTS_ROW_HEIGHT;
    static final float SEARCH_RESULTS_GAP = UiMetrics.SEARCH_RESULTS_GAP;
    static final int SEARCH_RESULTS_LIMIT = UiMetrics.SEARCH_RESULTS_LIMIT;
    static final long SEARCH_HIGHLIGHT_MS = UiMetrics.SEARCH_HIGHLIGHT_MS;
    static final float CONFIGS_TOOLBAR_HEIGHT = UiMetrics.CONFIGS_TOOLBAR_HEIGHT;
    static final float CONFIGS_TOOLBAR_TOP = UiMetrics.CONFIGS_TOOLBAR_TOP;
    static final int CONFIG_CARD_COLUMNS = UiMetrics.CONFIG_CARD_COLUMNS;
    static final float CONFIG_CARD_WIDTH = UiMetrics.CONFIG_CARD_WIDTH;
    static final float CONFIG_CARD_HEIGHT = UiMetrics.CONFIG_CARD_HEIGHT;
    static final float CONFIG_CARD_GAP_X = UiMetrics.CONFIG_CARD_GAP_X;
    static final float CONFIG_CARD_GAP_Y = UiMetrics.CONFIG_CARD_GAP_Y;
    static final float CONFIG_CARD_RADIUS = UiMetrics.CONFIG_CARD_RADIUS;
    static final float CONFIG_ACTION_SIZE = UiMetrics.CONFIG_ACTION_SIZE;
    static final float CONFIG_ACTION_GAP = UiMetrics.CONFIG_ACTION_GAP;
    static final float CONFIG_RENAME_SIZE = UiMetrics.CONFIG_RENAME_SIZE;
    static final float CONFIG_RENAME_GAP = UiMetrics.CONFIG_RENAME_GAP;
    static final float CONFIG_CARD_SIDE_PADDING = UiMetrics.CONFIG_CARD_SIDE_PADDING;
    static final float SCROLL_STEP = UiMetrics.SCROLL_STEP;
    static final float DESIGN_SCALE = UiMetrics.DESIGN_SCALE;
    static final String SIDEBAR_AVATAR = UiMetrics.SIDEBAR_AVATAR;
    static final float MAIN_PADDING = UiMetrics.MAIN_PADDING;
    static final float MODULE_LIST_WIDTH = UiMetrics.MODULE_LIST_WIDTH;
    static final float PANE_GAP = UiMetrics.PANE_GAP;
    static final float SETTINGS_PANEL_WIDTH = UiMetrics.SETTINGS_PANEL_WIDTH;
    static final float MODULE_ROW_HEIGHT = UiMetrics.MODULE_ROW_HEIGHT;
    static final float MODULE_ROW_GAP = UiMetrics.MODULE_ROW_GAP;
    static final float SETTINGS_HEADER_HEIGHT = UiMetrics.SETTINGS_HEADER_HEIGHT;
    static final float SETTINGS_PADDING = UiMetrics.SETTINGS_PADDING;
    static final float SIDEBAR_PROFILE_HEIGHT = UiMetrics.SIDEBAR_PROFILE_HEIGHT;
    static final float SIDEBAR_CATEGORY_TOP = UiMetrics.SIDEBAR_CATEGORY_TOP;
    static final float SIDEBAR_CATEGORY_HEIGHT = UiMetrics.SIDEBAR_CATEGORY_HEIGHT;
    static final float SIDEBAR_CATEGORY_GAP = UiMetrics.SIDEBAR_CATEGORY_GAP;

    private ClickGuiController() {
    }

    public static universalmod.gui.clickgui.component.ModuleBindingCoordinator bindingCoordinator() {
        return SESSION.bindingCoordinator;
    }

    public static void init() {
        SESSION.closing = false;
        SESSION.overlayActive = true;
        SESSION.pendingOpenAnimation = true;
        SESSION.searchFocused = false;
        SESSION.configSearchFocused = false;
        SESSION.searchCursor = SESSION.query.length();
        SESSION.configSearchCursor = SESSION.configQuery.length();
        SESSION.searchSelectAll = false;
        SESSION.configSearchSelectAll = false;
        SESSION.bindingCoordinator.clear();
        ClickGuiThemePopup.reset();
        SESSION.hwEventsPanel.resetInteraction();
        SESSION.customCrosshairEditorOpen = false;
        SESSION.itemReplacerEditorOpen = false;
        SESSION.scrollTarget = 0.0f;
        SESSION.scrollAnimation.set(0.0);
        SESSION.settingsScrollTarget = 0.0f;
        SESSION.settingsScrollLimit = 0.0f;
        SESSION.settingsScrollAnimation.set(0.0);
        SESSION.selectedModule = null;
        ClickGuiProfileMedia.ensureDirectory();
        SESSION.configDeleteHoverAnimations.clear();
        SESSION.configFolderHoverAnimations.clear();
        SESSION.configRenameHoverAnimations.clear();
        ClickGuiModulePanel.resetMotion();
        SESSION.openAnimation.set(0.0);
        SESSION.backdropAnimation.set(0.0);
        SESSION.searchResultsAnimation.set(0.0);
        SESSION.categorySelectionInitialized = false;
        SESSION.highlightedModule = null;
        SESSION.highlightedModuleUntil = 0L;
        SESSION.editingConfigCursor = 0;
        SESSION.editingConfigSelectAll = false;
    }

    public static void renderPanels(DrawContext graphics, int screenWidth, int screenHeight) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.currentScreen instanceof ClickGui && !isClickGuiAvailable()) {
            resetOverlayState();
            client.setScreen(null);
            return;
        }
        validateModulePolicyState();

        UiFrameContext.begin(graphics);
        try {
            updateMousePosition();
            float coordinateScale = coordinateScale();
            float designWidth = Math.max(1.0f, screenWidth / coordinateScale);
            float designHeight = Math.max(1.0f, screenHeight / coordinateScale);
            float guiScale = Math.min(designWidth / GUI_WIDTH, designHeight / GUI_HEIGHT) * DESIGN_SCALE;
            guiScale = Math.min(1.5f, Math.max(0.28f, guiScale * ThemeColors.clickGuiScale()));
            float guiX = (designWidth - GUI_WIDTH * guiScale) * 0.5f;
            float guiY = (designHeight - GUI_HEIGHT * guiScale) * 0.5f;

            if (SESSION.pendingOpenAnimation) {
                SESSION.pendingOpenAnimation = false;
                SESSION.openAnimation.set(0.0);
                SESSION.backdropAnimation.set(0.0);
                SESSION.openAnimation.run(1.0, 0.50, Easings.QUINTIC_OUT);
                SESSION.backdropAnimation.run(1.0, 0.50, Easings.QUINTIC_OUT);
            }
            SESSION.openAnimation.update();
            SESSION.backdropAnimation.update();
            float progress = clamp(SESSION.openAnimation.get(), 0.0f, 1.0f);
            float backdropProgress = clamp(SESSION.backdropAnimation.get(), 0.0f, 1.0f);
            float contentAlpha = (float) Math.pow(progress, 1.7D);
            float systemScale = 0.93f + 0.07f * progress;
            float animatedScale = guiScale * systemScale;
            float centerX = guiX + GUI_WIDTH * guiScale * 0.5f;
            float centerY = guiY + GUI_HEIGHT * guiScale * 0.5f;
            SESSION.lastGuiScale = animatedScale;
            SESSION.lastGuiX = centerX - GUI_WIDTH * animatedScale * 0.5f;
            SESSION.lastGuiY = centerY - GUI_HEIGHT * animatedScale * 0.5f + (1.0f - progress) * 18.0f * guiScale;
            if (SESSION.closing && !SESSION.openAnimation.isAlive() && progress <= 0.01f) {
                SESSION.closing = false;
                SESSION.overlayActive = false;
                return;
            }
            if (progress <= 0.01f && !SESSION.closing) {
                return;
            }

            updateModuleAnimations();
            updateScroll();

            RenderItem.beginFrame(graphics);
            Render2D.beginFrame(graphics);
            try {
                renderScaled(graphics, SESSION.lastGuiX, SESSION.lastGuiY, SESSION.lastGuiScale, contentAlpha, backdropProgress);
            } finally {
                Render2D.flush();
                RenderItem.flush();
            }
        } finally {
            UiFrameContext.end();
        }
    }

    static void renderScaled(DrawContext graphics, float guiX, float guiY, float scale, float alpha, float backdropAlpha) {
        Render2D.pushScissor(graphics, guiX, guiY, GUI_WIDTH * scale, GUI_HEIGHT * scale);
        try {
            renderPanelShader(graphics, guiX, guiY, scale, backdropAlpha);
            renderSeparator(guiX + SIDE_WIDTH * scale, guiY, 0.45f, GUI_HEIGHT * scale, color(92, 87, 105, 68, alpha));
            renderSeparator(guiX + SIDE_WIDTH * scale, guiY + HEADER_HEIGHT * scale, MAIN_WIDTH * scale, 0.45f, color(92, 87, 105, 58, alpha));
            renderHeaders(graphics, guiX, guiY, scale, alpha);
            renderTabs(graphics, guiX, guiY, scale, alpha);
            renderSearch(graphics, guiX, guiY, scale, alpha);
            renderBody(graphics, guiX + SIDE_WIDTH * scale, guiY + HEADER_HEIGHT * scale, scale, alpha);
        } finally {
            Render2D.popScissor(graphics);
        }


        renderModuleOverlays(graphics);
        ClickGuiThemePopup.render(graphics, alpha, SESSION.pointerX, SESSION.pointerY);
        if (SESSION.selectedHwEvents) {
            SESSION.hwEventsPanel.renderOverlay(graphics, alpha, SESSION.pointerX, SESSION.pointerY);
        }
    }

    static void renderHeaders(DrawContext graphics, float guiX, float guiY, float scale, float alpha){
        ClickGuiChromeRenderer.renderHeaders(graphics, guiX, guiY, scale, alpha);
    }

    static void renderTabs(DrawContext graphics, float guiX, float guiY, float scale, float alpha){
        ClickGuiChromeRenderer.renderTabs(graphics, guiX, guiY, scale, alpha);
    }

    static void renderSearch(DrawContext graphics, float guiX, float guiY, float scale, float alpha){
        ClickGuiSearchPanel.renderSearch(graphics, guiX, guiY, scale, alpha);
    }

    static void renderSearchResults(DrawContext graphics, float guiX, float guiY, float scale, float alpha){
        ClickGuiSearchPanel.renderSearchResults(graphics, guiX, guiY, scale, alpha);
    }

    static String animatedSearchPlaceholder(){
        return ClickGuiSearchPanel.animatedSearchPlaceholder();
    }

    static String animatedPlaceholder(String text){
        return ClickGuiSearchPanel.animatedPlaceholder(text);
    }

    static void renderSearchIcon(float centerX, float centerY, float scale, float alpha){
        ClickGuiSearchPanel.renderSearchIcon(centerX, centerY, scale, alpha);
    }

    static void renderBody(DrawContext graphics, float panelX, float panelY, float scale, float alpha){
        ClickGuiModulePanel.renderBody(graphics, panelX, panelY, scale, alpha);
    }

    static void renderModules(DrawContext graphics, float panelX, float panelY, float scale, float alpha){
        ClickGuiModulePanel.renderModules(graphics, panelX, panelY, scale, alpha);
    }

    static void renderConfigsPanel(DrawContext graphics, float panelX, float panelY, float scale, float alpha){
        ClickGuiConfigPanel.renderConfigsPanel(graphics, panelX, panelY, scale, alpha);
    }

    static void renderConfigCards(DrawContext graphics, float panelX, float panelY, float scale, float alpha,
                                          float x, float y){
        ClickGuiConfigPanel.renderConfigCards(graphics, panelX, panelY, scale, alpha, x, y);
    }

    static void renderNewConfigCard(float x, float y, float width, float height,
                                            float scale, float alpha){
        ClickGuiConfigPanel.renderNewConfigCard(x, y, width, height, scale, alpha);
    }

    static void renderConfigCard(DrawContext graphics, ConfigManager.ProfileInfo profileInfo,
                                         float x, float y, float width, float height,
                                         float scale, float alpha, ConfigManager configManager){
        ClickGuiConfigPanel.renderConfigCard(graphics, profileInfo, x, y, width, height, scale, alpha, configManager);
    }

    static void renderModuleOverlays(DrawContext graphics){
        ClickGuiModulePanel.renderModuleOverlays(graphics);
    }

    static float columnOffset(List<Module> modules, int index){
        return ClickGuiModulePanel.columnOffset(modules, index);
    }

    public static boolean warmupText() {
        return ClickGuiWarmup.warmupText();
    }

    public static boolean warmupGraphics(DrawContext graphics) {
        return ClickGuiWarmup.warmupGraphics(graphics);
    }

    public static boolean keyPressed(KeyEvent event){
        return ClickGuiInputRouter.keyPressed(event);
    }

    public static boolean charTyped(CharacterEvent event){
        return ClickGuiInputRouter.charTyped(event);
    }

    public static boolean mouseClicked(MouseButtonEvent event, boolean doubled){
        return ClickGuiInputRouter.mouseClicked(event, doubled);
    }

    public static boolean mouseReleased(MouseButtonEvent event){
        return ClickGuiInputRouter.mouseReleased(event);
    }

    public static boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY){
        return ClickGuiInputRouter.mouseDragged(event, dragX, dragY);
    }

    public static boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY){
        return ClickGuiInputRouter.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    public static boolean onFilesDrop(List<java.nio.file.Path> paths){
        return ClickGuiInputRouter.onFilesDrop(paths);
    }

    public static double mouseX() {
        return SESSION.pointerX;
    }

    public static double mouseY() {
        return SESSION.pointerY;
    }

    public static void startClosing() {
        if (SESSION.closing) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        universalmod.module.misc.ClickGuiModule.suppressReopenUntilKeyRelease();
        SESSION.closing = true;
        SESSION.overlayActive = true;
        SESSION.searchFocused = false;
        SESSION.configSearchFocused = false;
        SESSION.searchCursor = SESSION.query.length();
        SESSION.configSearchCursor = SESSION.configQuery.length();
        SESSION.searchSelectAll = false;
        SESSION.configSearchSelectAll = false;
        SESSION.bindingCoordinator.clear();
        ClickGuiThemePopup.reset();
        SESSION.hwEventsPanel.resetInteraction();
        SESSION.customCrosshairEditorOpen = false;
        SESSION.itemReplacerEditorOpen = false;
        SESSION.openAnimation.run(0.0, 0.42, Easings.CUBIC_OUT);
        SESSION.backdropAnimation.run(0.0, 0.24, Easings.CUBIC_IN);
        if (client != null && client.currentScreen instanceof ClickGui) {
            client.setScreen(null);
        }
    }

    public static void resetOverlayState() {
        SESSION.closing = false;
        SESSION.overlayActive = false;
        SESSION.pendingOpenAnimation = false;
        SESSION.searchFocused = false;
        SESSION.configSearchFocused = false;
        SESSION.searchCursor = SESSION.query.length();
        SESSION.configSearchCursor = SESSION.configQuery.length();
        SESSION.searchSelectAll = false;
        SESSION.configSearchSelectAll = false;
        SESSION.bindingCoordinator.clear();
        ClickGuiThemePopup.reset();
        SESSION.hwEventsPanel.resetInteraction();
        SESSION.customCrosshairEditorOpen = false;
        SESSION.itemReplacerEditorOpen = false;
        SESSION.openAnimation.set(0.0);
        SESSION.backdropAnimation.set(0.0);
    }

    public static boolean shouldRenderOverlay() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            resetOverlayState();
            return false;
        }
        if (client.currentScreen instanceof ClickGui && !isClickGuiAvailable()) {
            resetOverlayState();
            client.setScreen(null);
            return false;
        }
        if (client.currentScreen == null && !SESSION.closing) {
            resetOverlayState();
            return false;
        }
        if (client.currentScreen != null && !(client.currentScreen instanceof ClickGui)) {
            resetOverlayState();
            return false;
        }
        return SESSION.overlayActive || client.currentScreen instanceof ClickGui;
    }

    public static void validateState(MinecraftClient client) {
        if (client == null) {
            resetOverlayState();
            return;
        }
        if ((client.player == null || client.world == null) && client.currentScreen instanceof ClickGui) {
            resetOverlayState();
            client.setScreen(null);
            return;
        }
        if (client.currentScreen == null && !SESSION.closing && SESSION.overlayActive) {
            resetOverlayState();
        }
    }

    public static void tickMovementKeys() {
    }

    static void updateModuleAnimations() {
        if (isBodyEditorOpen()) {
            return;
        }


        List<Module> currentModules = visibleModules();
        if ((SESSION.selectedCategory != ModuleCategory.CONFIGS || (SESSION.query != null && !SESSION.query.isBlank())) && !SESSION.selectedHwEvents) {
            if (SESSION.selectedModule != null && !currentModules.contains(SESSION.selectedModule)) {
                SESSION.selectedModule = null;
                SESSION.settingsScrollTarget = 0.0f;
                SESSION.settingsScrollAnimation.set(0.0f);
            }
        }


        for (Module module : currentModules) {
            ModuleOption option = optionFor(module);
            option.updateAnimation(module == SESSION.selectedModule);
        }
        ModuleOption bindingOption = SESSION.bindingCoordinator.active();
        if (bindingOption != null && (SESSION.selectedModule == null || bindingOption != optionFor(SESSION.selectedModule))) {
            bindingOption.updateAnimation(false);
        }
    }

    static void updateScroll() {
        if (isBodyEditorOpen()) {
            SESSION.scrollLimit = 0.0f;
            SESSION.scrollTarget = 0.0f;
            SESSION.scrollAnimation.run(0.0f, 0.18, Easings.CUBIC_OUT, true);
            SESSION.scrollAnimation.update();
            return;
        }
        if (SESSION.selectedCategory == ModuleCategory.CONFIGS && !SESSION.selectedHwEvents && (SESSION.query == null || SESSION.query.isBlank())) {
            ConfigManager configManager = configManager();
            int count = 1 + (configManager == null ? 0 : filteredConfigProfiles(configManager).size());
            int rows = (count + CONFIG_CARD_COLUMNS - 1) / CONFIG_CARD_COLUMNS;
            float cardsHeight = rows <= 0
                    ? 0.0f
                    : rows * CONFIG_CARD_HEIGHT + Math.max(0, rows - 1) * CONFIG_CARD_GAP_Y;
            float viewportHeight = BODY_HEIGHT - CONFIGS_TOOLBAR_TOP - MODULE_PANEL_PADDING_Y
                    - CONFIGS_TOOLBAR_HEIGHT - CONFIG_CARD_GAP_Y;
            SESSION.scrollLimit = Math.max(0.0f, cardsHeight - viewportHeight);
            SESSION.scrollTarget = clamp(SESSION.scrollTarget, 0.0f, SESSION.scrollLimit);
            SESSION.scrollAnimation.run(SESSION.scrollTarget, 0.22, Easings.CUBIC_OUT, true);
            SESSION.scrollAnimation.update();
            return;
        }
        List<Module> modules = visibleModules();
        float contentHeight = modules.isEmpty()
                ? 0.0f
                : modules.size() * MODULE_ROW_HEIGHT + Math.max(0, modules.size() - 1) * MODULE_ROW_GAP;
        float viewportHeight = BODY_HEIGHT - 2.0f;
        SESSION.scrollLimit = Math.max(0.0f, contentHeight - viewportHeight);
        SESSION.scrollTarget = clamp(SESSION.scrollTarget, 0.0f, SESSION.scrollLimit);
        SESSION.scrollAnimation.run(SESSION.scrollTarget, 0.20, Easings.CUBIC_OUT, true);
        SESSION.scrollAnimation.update();

        float settingsContent = SESSION.selectedModule == null
                ? 0.0f
                : optionFor(SESSION.selectedModule).detachedSettingsHeight(ClickGuiModulePanel.INSPECTOR_OPTION_SCALE);
        float settingsViewport = BODY_HEIGHT - 2.0f - ClickGuiModulePanel.inspectorSyntheticContentHeight();
        SESSION.settingsScrollLimit = Math.max(0.0f, settingsContent - Math.max(1.0f, settingsViewport));
        SESSION.settingsScrollTarget = clamp(SESSION.settingsScrollTarget, 0.0f, SESSION.settingsScrollLimit);
        SESSION.settingsScrollAnimation.run(SESSION.settingsScrollTarget, 0.20, Easings.CUBIC_OUT, true);
        SESSION.settingsScrollAnimation.update();
    }

    static void resetScroll() {
        SESSION.scrollTarget = 0.0f;
        SESSION.settingsScrollTarget = 0.0f;
        SESSION.scrollAnimation.run(0.0, 0.18, Easings.CUBIC_OUT);
        SESSION.settingsScrollAnimation.run(0.0, 0.18, Easings.CUBIC_OUT);
    }

    static boolean handleSearchClick(MouseButtonEvent event){
        return ClickGuiSearchPanel.handleSearchClick(event);
    }

    static boolean handleConfigPanelClick(MouseButtonEvent event, boolean doubled){
        return ClickGuiConfigPanel.handleConfigPanelClick(event, doubled);
    }

    static ConfigManager configManager(){
        return ClickGuiConfigPanel.configManager();
    }

    static boolean handleConfigEditKey(KeyEvent event){
        return ClickGuiConfigPanel.handleConfigEditKey(event);
    }

    static void commitConfigRename(){
        ClickGuiConfigPanel.commitConfigRename();
    }

    static List<String> filteredConfigProfiles(ConfigManager configManager){
        return ClickGuiConfigPanel.filteredConfigProfiles(configManager);
    }

    static int compareNaturally(String left, String right){
        return ClickGuiConfigPanel.compareNaturally(left, right);
    }

    static boolean handleTabClick(MouseButtonEvent event){
        return ClickGuiInputRouter.handleTabClick(event);
    }

    static List<Module> visibleModules(){
        return ClickGuiModulePanel.visibleModules();
    }

    static List<Module> searchSuggestions(){
        return ClickGuiSearchPanel.searchSuggestions();
    }

    static String categoryLabel(ModuleCategory category){
        return ClickGuiSearchPanel.categoryLabel(category);
    }

    static boolean handleSearchResultClick(MouseButtonEvent event){
        return ClickGuiSearchPanel.handleSearchResultClick(event);
    }

    static void navigateToSearchResult(Module module){
        ClickGuiSearchPanel.navigateToSearchResult(module);
    }

    static boolean matchesSelectedTab(Module module){
        return ClickGuiSearchPanel.matchesSelectedTab(module);
    }

    static boolean isGlobalSearchCategory(Module module){
        return ClickGuiSearchPanel.isGlobalSearchCategory(module);
    }

    static Collection<Module> modules() {
        ModuleManager manager = Manager.getModules();
        return manager == null ? List.of() : manager.getModules();
    }

    static long modulesRevision() {
        ModuleManager manager = Manager.getModules();
        return manager == null ? 0L : manager.revision();
    }

    static ModuleOption optionFor(Module module) {
        return SESSION.options.computeIfAbsent(module, key -> ModuleOptionFactory.create(key, SESSION.bindingCoordinator));
    }

    static void selectModule(Module module) {
        if (module == null) return;
        ModuleManager manager = Manager.getModules();
        if (manager == null || !manager.isVisible(module)) return;
        Module previous = SESSION.selectedModule;
        if (previous == module) {
            optionFor(previous).hideSettings();
            SESSION.selectedModule = null;
            SESSION.settingsScrollTarget = 0.0f;
            SESSION.settingsScrollAnimation.set(0.0f);
            return;
        }
        if (previous != null) {
            optionFor(previous).hideSettings();
        }
        SESSION.selectedModule = module;
        optionFor(module).restartSettingsReveal();
        SESSION.settingsScrollTarget = 0.0f;
        SESSION.settingsScrollAnimation.set(0.0f);
    }

    public static void openCustomCrosshairEditor(CustomCrosshair module){
        ClickGuiEditorRouter.openCustomCrosshairEditor(module);
    }

    static void closeCustomCrosshairEditor(){
        ClickGuiEditorRouter.closeCustomCrosshairEditor();
    }

    public static void openItemReplacerEditor(ItemReplacer module){
        ClickGuiEditorRouter.openItemReplacerEditor(module);
    }

    static void closeItemReplacerEditor(){
        ClickGuiEditorRouter.closeItemReplacerEditor();
    }

    static boolean isBodyEditorOpen(){
        return ClickGuiEditorRouter.isBodyEditorOpen();
    }

    static void closeBodyEditor(){
        ClickGuiEditorRouter.closeBodyEditor();
    }

    static String activeTitle(){
        return ClickGuiEditorRouter.activeTitle();
    }

    static String activeDescription(){
        return ClickGuiEditorRouter.activeDescription();
    }

    static void renderPanelShader(DrawContext graphics, float x, float y, float scale, float alpha){
        ClickGuiChromeRenderer.renderPanelShader(graphics, x, y, scale, alpha);
    }

    static void renderFooter(DrawContext graphics, float guiX, float guiY, float scale, float alpha){
        ClickGuiChromeRenderer.renderFooter(graphics, guiX, guiY, scale, alpha);
    }

    static String todayDate(){
        return ClickGuiConfigPanel.todayDate();
    }

    static String profileDate(ConfigManager.ProfileInfo profileInfo){
        return ClickGuiConfigPanel.profileDate(profileInfo);
    }

    static void renderCurrentPlayerHead(float x, float y, float size, float alpha){
        ClickGuiChromeRenderer.renderCurrentPlayerHead(x, y, size, alpha);
    }

    static String currentPlayerName(){
        return ClickGuiChromeRenderer.currentPlayerName();
    }

    static String categoryIcon(String label){
        return ClickGuiChromeRenderer.categoryIcon(label);
    }

    static void renderCategoryIcon(String label, float x, float y, float size, int color){
        ClickGuiChromeRenderer.renderCategoryIcon(label, x, y, size, color);
    }

    static void renderIconNewGlyph(String icon, float x, float y, float size, int color){
        ClickGuiChromeRenderer.renderIconNewGlyph(icon, x, y, size, color);
    }

    static void renderDeltaActionGlyph(String glyph, float x, float y, float size, int color){
        ClickGuiChromeRenderer.renderDeltaActionGlyph(glyph, x, y, size, color);
    }

    static void renderRect(float rootX, float rootY, float x, float y, float width, float height, float radius, float scale, int color){
        ClickGuiChromeRenderer.renderRect(rootX, rootY, x, y, width, height, radius, scale, color);
    }

    static void renderRect(float x, float y, float width, float height, float r1, float r2, float r3, float r4, float scale, int color){
        ClickGuiChromeRenderer.renderRect(x, y, width, height, r1, r2, r3, r4, scale, color);
    }

    static void renderSeparator(float x, float y, float width, float height, int color){
        ClickGuiChromeRenderer.renderSeparator(x, y, width, height, color);
    }

    static MouseButtonEvent toDesignEvent(MouseButtonEvent event) {
        return ClickGuiCoordinateSupport.toDesignEvent(event);
    }

    static float coordinateScale() {
        return Render2DCoordinateSpace.guiIndependentScale();
    }

    static void updateMousePosition() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.mouse == null || client.getWindow() == null) {
            return;
        }
        float scale = coordinateScale();
        double guiMouseX = client.mouse.getX() / client.getWindow().getScaleFactor();
        double guiMouseY = client.mouse.getY() / client.getWindow().getScaleFactor();
        SESSION.pointerX = guiMouseX / scale;
        SESSION.pointerY = guiMouseY / scale;
    }

    static boolean inside(double px, double py, float x, float y, float width, float height) {
        return ClickGuiCoordinateSupport.inside(px, py, x, y, width, height);
    }

    static float animateConfigHover(Map<String, SmoothAnimation> animations, String profile,
                                            boolean hovered, double durationSeconds){
        return ClickGuiConfigPanel.animateConfigHover(animations, profile, hovered, durationSeconds);
    }

    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    static int color(int red, int green, int blue, int alpha, float alphaMultiplier){
        return ClickGuiChromeRenderer.color(red, green, blue, alpha, alphaMultiplier);
    }

    


    private static boolean isClickGuiAvailable() {
        ModuleManager manager = Manager.getModules();
        return manager != null && manager.getByType(ClickGuiModule.class).isPresent();
    }

    private static void validateModulePolicyState() {
        ModuleManager manager = Manager.getModules();
        if (manager != null) {
            if (SESSION.selectedModule != null && !manager.isVisible(SESSION.selectedModule)) {
                SESSION.selectedModule = null;
                SESSION.bindingCoordinator.clear();
                SESSION.settingsScrollTarget = 0.0f;
                SESSION.settingsScrollAnimation.set(0.0f);
            }
            if (SESSION.highlightedModule != null && !manager.isVisible(SESSION.highlightedModule)) {
                SESSION.highlightedModule = null;
                SESSION.highlightedModuleUntil = 0L;
            }
        }
        ClickGuiEditorRouter.validateVisibility();
    }
}
