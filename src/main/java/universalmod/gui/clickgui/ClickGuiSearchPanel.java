package universalmod.gui.clickgui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.service.language.LanguageCode;
import universalmod.service.language.LanguageManager;
import universalmod.gui.clickgui.component.option.OptionStyle;
import universalmod.gui.ui.control.Bounds;
import universalmod.render.animation.Easings;
import universalmod.render.Render2D;
import universalmod.render.color.ColorUtil;
import universalmod.render.optimize.UiFrameContext;
import universalmod.render.font.FontType;
import universalmod.render.surface.GuiSurface;
import universalmod.theme.ThemeColors;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static universalmod.gui.clickgui.ClickGuiController.*;

final class ClickGuiSearchPanel {
    private static String suggestionsCacheNeedle = "";
    private static LanguageCode suggestionsCacheLanguage;
    private static int suggestionsCacheModuleCount = -1;
    private static long suggestionsCacheRevision = Long.MIN_VALUE;
    private static List<Module> suggestionsCache = List.of();

    private ClickGuiSearchPanel() {
    }

    static void renderSearch(GuiGraphics graphics, float guiX, float guiY, float scale, float alpha) {
        float x = guiX + 8.0f * scale;
        float y = guiY + 8.0f * scale;
        float width = (SIDE_WIDTH - 16.0f) * scale;
        float height = 22.0f * scale;
        float radius = 4.5f * scale;

        int searchBase = ColorUtil.lerpColor(
                ThemeColors.clickGuiPanelColor(255, 1.0f),
                ThemeColors.clickGuiModuleColor(255, 1.0f),
                0.24f
        );
        new GuiSurface(x, y, width, height, radius,
                alpha, SESSION.searchFocused ? 0.48f : 0.12f,
                searchBase, ThemeColors.clickGuiAccentColor(255, 1.0f)).render(graphics);

        boolean placeholder = SESSION.query.isEmpty() && !SESSION.searchFocused;
        String display = placeholder ? LanguageManager.translate("clickgui.search") : SESSION.query;
        int textColor = placeholder ? ThemeColors.clickGuiMutedColor(235, alpha) : ThemeColors.clickGuiTextColor(255, alpha);
        float textSize = 8.35f * scale;
        float textY = OptionStyle.centeredTextY(FontType.SEMIBOLD, display, textSize, y, height);
        float textX = x + 7.5f * scale;
        float iconArea = 20.0f * scale;
        Render2D.pushScissor(graphics, textX, y, Math.max(1.0f, width - 28.0f * scale), height);
        try {
            Render2D.text(FontType.SEMIBOLD, display, textX, textY, textSize, textColor);
        } finally {
            Render2D.popScissor(graphics);
        }
        renderSearchIcon(x + width - 11.0f * scale, y + height * 0.5f, 0.78f * scale, alpha);

        if (SESSION.searchFocused && (UiFrameContext.nowMillis() / 340L) % 2L == 0L) {
            int caretIndex = Math.max(0, Math.min(SESSION.searchCursor, SESSION.query.length()));
            float caretX = textX + Render2D.textWidth(FontType.SEMIBOLD, SESSION.query.substring(0, caretIndex), textSize);
            float caretH = Math.min(height - 7.0f * scale, 10.0f * scale);
            float caretY = y + (height - caretH) * 0.5f;
            Render2D.rect(Math.min(caretX + 0.8f * scale, x + width - iconArea), caretY,
                    0.8f * scale, caretH, 0.22f * scale, color(244, 240, 252, 245, alpha));
        }
    }
    static void renderSearchResults(GuiGraphics graphics, float guiX, float guiY, float scale, float alpha) {
        List<Module> suggestions = searchSuggestions();
        boolean show = SESSION.searchFocused && SESSION.query != null && !SESSION.query.isBlank() && !suggestions.isEmpty();
        SESSION.searchResultsAnimation.run(show ? 1.0D : 0.0D, 0.16D, Easings.CUBIC_OUT, true);
        SESSION.searchResultsAnimation.update();
        float reveal = (float) SESSION.searchResultsAnimation.get();
        if (reveal <= 0.01F || suggestions.isEmpty()) {
            return;
        }

        float searchX = guiX + 8.0F * scale;
        float searchY = guiY + 8.0F * scale;
        float searchW = (SIDE_WIDTH - 16.0F) * scale;
        float searchH = 22.0F * scale;
        SearchResultsLayout layout = SearchResultsLayout.create(searchX, searchY, searchW, searchH, scale, suggestions.size());
        Bounds panel = layout.panel();
        float slide = (1.0F - reveal) * 5.0F * scale;
        float panelHeight = panel.height() * reveal;
        if (panelHeight <= 0.5F) {
            return;
        }

        Render2D.pushScissor(graphics, panel.x(), panel.y() - slide, panel.width(), panelHeight);
        try {
            for (int i = 0; i < suggestions.size(); i++) {
                Module module = suggestions.get(i);
                Bounds row = layout.row(i).translated(0.0F, -slide);
                boolean hover = row.contains(SESSION.pointerX, SESSION.pointerY);
                float hoverMix = hover ? 0.58F : 0.16F;
                new GuiSurface(row.x(), row.y(), row.width(), row.height(), 4.2F * scale,
                        alpha * reveal, hoverMix,
                        ThemeColors.clickGuiPanelColor(255, 1.0F),
                        ThemeColors.clickGuiAccentColor(255, 1.0F)).render(graphics);

                float textX = row.x() + 7.0F * scale;
                float nameSize = 7.8F * scale;
                String name = module.getDisplayName();
                if (name == null || name.isBlank()) {
                    name = module.getName();
                }
                float nameY = row.y() + 4.0F * scale;
                Render2D.text(FontType.SEMIBOLD, name, textX, nameY, nameSize,
                        ThemeColors.clickGuiTextColor(255, alpha * reveal));

                String category = categoryLabel(module.getCategory());
                float categorySize = 5.7F * scale;
                Render2D.text(FontType.SEMIBOLD, category, textX, row.y() + 14.0F * scale, categorySize,
                        ThemeColors.clickGuiMutedColor(218, alpha * reveal));

                if (module.isEnabled()) {
                    float dot = 3.0F * scale;
                    Render2D.circle(row.right() - 7.0F * scale + dot * 0.5F, row.centerY(),
                            dot, ThemeColors.clickGuiAccentColor(255, alpha * reveal));
                }
            }
        } finally {
            Render2D.popScissor(graphics);
        }
    }
    static String animatedSearchPlaceholder() {
        return animatedPlaceholder(LanguageManager.translate("clickgui.search"));
    }
    static String animatedPlaceholder(String text) {
        int length = text.length();
        int pauseFrames = 5;
        int totalFrames = length + pauseFrames + length + pauseFrames;
        int frame = (int) ((UiFrameContext.nowMillis() / 120L) % totalFrames);
        int count;
        if (frame < length) {
            count = frame + 1;
        } else if (frame < length + pauseFrames) {
            count = length;
        } else if (frame < length + pauseFrames + length) {
            count = Math.max(0, length - (frame - length - pauseFrames) - 1);
        } else {
            count = 0;
        }
        return text.substring(0, Math.min(length, count));
    }
    static void renderSearchIcon(float centerX, float centerY, float scale, float alpha) {
        int iconColor = ThemeColors.clickGuiTextColor(248, alpha);
        float circle = 7.2f * scale;
        float cx = centerX - 2.0f * scale;
        float cy = centerY - 2.0f * scale;
        Render2D.circleOutline(cx, cy, circle, 1.15f * scale, iconColor);

        float dot = 1.45f * scale;
        Render2D.circle(cx + 2.4f * scale + dot * 0.5f, cy + 2.4f * scale + dot * 0.5f, dot, iconColor);
        Render2D.circle(cx + 3.35f * scale + dot * 0.5f, cy + 3.35f * scale + dot * 0.5f, dot, iconColor);
        Render2D.circle(cx + 4.30f * scale + dot * 0.5f, cy + 4.30f * scale + dot * 0.5f, dot, iconColor);
    }
    static boolean handleSearchClick(MouseButtonEvent event) {
        float searchX = SESSION.lastGuiX + 8.0f * SESSION.lastGuiScale;
        float searchY = SESSION.lastGuiY + 8.0f * SESSION.lastGuiScale;
        float searchW = (SIDE_WIDTH - 16.0f) * SESSION.lastGuiScale;
        float searchH = 22.0f * SESSION.lastGuiScale;
        if (event.button() == 0 && inside(event.x(), event.y(), searchX, searchY, searchW, searchH)) {
            SESSION.searchFocused = true;
            SESSION.searchCursor = Math.min(SESSION.searchCursor, SESSION.query.length());
            SESSION.searchSelectAll = false;
            SESSION.configSearchFocused = false;
            return true;
        }
        SESSION.searchFocused = false;
        SESSION.configSearchFocused = false;
        return false;
    }
    static List<Module> searchSuggestions() {
        String needle = SESSION.query == null ? "" : SESSION.query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            suggestionsCacheNeedle = "";
            suggestionsCache = List.of();
            return suggestionsCache;
        }

        Collection<Module> source = modules();
        LanguageCode language = LanguageManager.current();
        long sourceRevision = modulesRevision();
        if (needle.equals(suggestionsCacheNeedle)
                && language == suggestionsCacheLanguage
                && source.size() == suggestionsCacheModuleCount
                && suggestionsCacheRevision == sourceRevision) {
            return suggestionsCache;
        }

        ArrayList<Module> result = new ArrayList<>();
        for (Module module : source) {
            if (!isGlobalSearchCategory(module)) {
                continue;
            }
            String display = module.getDisplayName() == null ? "" : module.getDisplayName();
            String name = module.getName() == null ? "" : module.getName();
            String displayLower = display.toLowerCase(Locale.ROOT);
            String nameLower = name.toLowerCase(Locale.ROOT);
            if (displayLower.contains(needle) || nameLower.contains(needle)) {
                result.add(module);
            }
        }
        result.sort(Comparator
                .comparingInt((Module module) -> {
                    String display = module.getDisplayName() == null ? "" : module.getDisplayName();
                    String name = module.getName() == null ? "" : module.getName();
                    return display.toLowerCase(Locale.ROOT).startsWith(needle)
                            || name.toLowerCase(Locale.ROOT).startsWith(needle) ? 0 : 1;
                })
                .thenComparing(Module::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        if (result.size() > SEARCH_RESULTS_LIMIT) {
            result.subList(SEARCH_RESULTS_LIMIT, result.size()).clear();
        }

        suggestionsCacheNeedle = needle;
        suggestionsCacheLanguage = language;
        suggestionsCacheModuleCount = source.size();
        suggestionsCacheRevision = sourceRevision;
        suggestionsCache = List.copyOf(result);
        return suggestionsCache;
    }
    static String categoryLabel(ModuleCategory category) {
        if (category == ModuleCategory.RENDER) return LanguageManager.categoryName("Render");
        if (category == ModuleCategory.UTILS) return LanguageManager.categoryName("Utils");
        if (category == ModuleCategory.MISC) return LanguageManager.categoryName("Misc");
        return category == null ? "" : category.name();
    }
    static boolean handleSearchResultClick(MouseButtonEvent event) {
        if (event == null || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT
                || !SESSION.searchFocused || SESSION.query == null || SESSION.query.isBlank()) {
            return false;
        }
        List<Module> suggestions = searchSuggestions();
        if (suggestions.isEmpty()) {
            return false;
        }
        float scale = SESSION.lastGuiScale;
        float searchX = SESSION.lastGuiX + 8.0F * scale;
        float searchY = SESSION.lastGuiY + 8.0F * scale;
        float searchW = (SIDE_WIDTH - 16.0F) * scale;
        float searchH = 22.0F * scale;
        SearchResultsLayout layout = SearchResultsLayout.create(searchX, searchY, searchW, searchH, scale, suggestions.size());
        int index = layout.hit(event.x(), event.y());
        if (index < 0) {
            return false;
        }
        navigateToSearchResult(suggestions.get(index));
        return true;
    }
    static void navigateToSearchResult(Module module) {
        if (module == null || !isGlobalSearchCategory(module)) {
            return;
        }
        SESSION.selectedCategory = module.getCategory();
        SESSION.selectedHwEvents = false;
        SESSION.customCrosshairEditorOpen = false;
        SESSION.itemReplacerEditorOpen = false;
        SESSION.hwEventsPanel.resetInteraction();
        SESSION.query = "";
        SESSION.searchFocused = false;
        SESSION.configSearchFocused = false;
        SESSION.highlightedModule = module;
        SESSION.highlightedModuleUntil = UiFrameContext.nowMillis() + SEARCH_HIGHLIGHT_MS;
        selectModule(module);
        SESSION.visibleModulesCacheCategory = null;
        List<Module> modules = visibleModules();
        updateScroll();
        int index = modules.indexOf(module);
        if (index >= 0) {
            float offset = columnOffset(modules, index);
            SESSION.scrollTarget = clamp(offset - 36.0f, 0.0f, SESSION.scrollLimit);
            SESSION.scrollAnimation.set(SESSION.scrollTarget);
        } else {
            resetScroll();
        }
    }
    static boolean isGlobalSearchCategory(Module module) {
        if (module == null) {
            return false;
        }
        ModuleCategory category = module.getCategory();
        return category == ModuleCategory.RENDER || category == ModuleCategory.UTILS || category == ModuleCategory.MISC;
    }
    static boolean matchesSelectedTab(Module module) {
        if (module == null || SESSION.selectedHwEvents) {
            return false;
        }
        return module.getCategory() == SESSION.selectedCategory;
    }
}
