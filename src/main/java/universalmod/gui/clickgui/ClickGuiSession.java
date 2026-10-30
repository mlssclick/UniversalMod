package universalmod.gui.clickgui;

import universalmod.gui.clickgui.component.ModuleOption;
import universalmod.gui.clickgui.component.ModuleBindingCoordinator;
import universalmod.gui.clickgui.panel.CustomCrosshairPanel;
import universalmod.gui.clickgui.panel.HwEventsPanel;
import universalmod.gui.clickgui.panel.ItemReplacerPanel;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.render.animation.SmoothAnimation;
import universalmod.service.language.LanguageCode;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

final class ClickGuiSession {
    final SmoothAnimation openAnimation = new SmoothAnimation();
    final SmoothAnimation backdropAnimation = new SmoothAnimation();
    final SmoothAnimation searchResultsAnimation = new SmoothAnimation();
    final SmoothAnimation categorySelectionAnimation = new SmoothAnimation();
    final SmoothAnimation scrollAnimation = new SmoothAnimation();
    final SmoothAnimation settingsScrollAnimation = new SmoothAnimation();

    final CustomCrosshairPanel customCrosshairPanel = new CustomCrosshairPanel();
    final ItemReplacerPanel itemReplacerPanel = new ItemReplacerPanel();
    final HwEventsPanel hwEventsPanel = new HwEventsPanel();

    final Map<Module, ModuleOption> options = new IdentityHashMap<>();
    final ModuleBindingCoordinator bindingCoordinator = new ModuleBindingCoordinator();
    final Map<String, SmoothAnimation> configDeleteHoverAnimations = new HashMap<>();
    final Map<String, SmoothAnimation> configFolderHoverAnimations = new HashMap<>();
    final Map<String, SmoothAnimation> configRenameHoverAnimations = new HashMap<>();

    ModuleCategory selectedCategory = ModuleCategory.RENDER;
    Module selectedModule;
    boolean selectedHwEvents;
    boolean categorySelectionInitialized;
    boolean customCrosshairEditorOpen;
    boolean itemReplacerEditorOpen;
    boolean closing;
    boolean overlayActive;
    boolean pendingOpenAnimation;
    boolean textWarmed;
    boolean warmupBasePrepared;
    float warmupScale = 0.84F;
    List<Module> warmupModules = List.of();
    int warmupModuleIndex;
    boolean searchFocused;
    boolean configSearchFocused;
    String query = "";
    String configQuery = "";
    int searchCursor;
    int configSearchCursor;
    boolean searchSelectAll;
    boolean configSearchSelectAll;
    Module highlightedModule;
    long highlightedModuleUntil;
    float scrollTarget;
    float scrollLimit;
    float settingsScrollTarget;
    float settingsScrollLimit;
    float lastGuiX;
    float lastGuiY;
    float lastGuiScale = 1.0F;
    double pointerX;
    double pointerY;
    int graphicsWarmupStage;
    String editingConfigName;
    String editingConfigValue = "";
    int editingConfigCursor;
    boolean editingConfigSelectAll;

    List<Module> visibleModulesCache = List.of();
    String visibleModulesCacheQuery;
    ModuleCategory visibleModulesCacheCategory;
    boolean visibleModulesCacheHwEvents;
    LanguageCode visibleModulesCacheLanguage;
    int visibleModulesCacheCount = -1;
    long visibleModulesCacheRevision = Long.MIN_VALUE;
}
