package universalmod.gui.clickgui;

import net.minecraft.client.gui.DrawContext;
import universalmod.gui.clickgui.component.ModuleOption;
import universalmod.gui.clickgui.component.option.OptionStyle;
import universalmod.lifecycle.Manager;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleManager;
import universalmod.render.Render2D;
import universalmod.render.animation.Easings;
import universalmod.render.animation.SmoothAnimation;
import universalmod.render.optimize.UiFrameContext;
import universalmod.render.color.ColorUtil;
import universalmod.render.font.FontType;
import universalmod.render.surface.GuiSurface;
import universalmod.service.language.LanguageCode;
import universalmod.service.language.LanguageManager;
import universalmod.theme.ThemeColors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static universalmod.gui.clickgui.ClickGuiController.*;

final class ClickGuiModulePanel {
    static final float INSPECTOR_SYNTH_ROW_HEIGHT = 0.0f;
    static final float INSPECTOR_ROW_GAP = 0.0f;
    static final float INSPECTOR_OPTION_SCALE = 1.0f;

    private static final Map<Module, CardMotionState> CARD_MOTION = new IdentityHashMap<>();
    private static Set<Module> lastVisibleModules = Collections.newSetFromMap(new IdentityHashMap<>());

    private ClickGuiModulePanel() {}

    static void resetMotion() {
        CARD_MOTION.clear();
        lastVisibleModules = Collections.newSetFromMap(new IdentityHashMap<>());
    }

    static float inspectorSyntheticContentHeight() {
        return 0.0f;
    }

    static void renderBody(DrawContext graphics, float panelX, float panelY, float scale, float alpha) {
        double mouseX = SESSION.pointerX;
        double mouseY = SESSION.pointerY;
        if (SESSION.customCrosshairEditorOpen) {
            SESSION.customCrosshairPanel.render(graphics, panelX, panelY, MAIN_WIDTH * scale, BODY_HEIGHT * scale, scale, alpha, mouseX, mouseY);
            return;
        }
        if (SESSION.itemReplacerEditorOpen) {
            SESSION.itemReplacerPanel.render(graphics, panelX, panelY, MAIN_WIDTH * scale, BODY_HEIGHT * scale, scale, alpha, mouseX, mouseY);
            return;
        }
        renderModules(graphics, panelX, panelY, scale, alpha);
    }

    static void renderModules(DrawContext graphics, float panelX, float panelY, float scale, float alpha) {
        double mouseX = SESSION.pointerX;
        double mouseY = SESSION.pointerY;
        if (SESSION.selectedHwEvents) {
            SESSION.hwEventsPanel.setSearchQuery(SESSION.query);
            SESSION.hwEventsPanel.render(graphics, panelX, panelY, MAIN_WIDTH * scale, BODY_HEIGHT * scale,
                    scale, alpha, mouseX, mouseY);
            return;
        }
        if (SESSION.selectedCategory == ModuleCategory.CONFIGS && (SESSION.query == null || SESSION.query.isBlank())) {
            renderConfigsPanel(graphics, panelX, panelY, scale, alpha);
            return;
        }

        List<Module> modules = visibleModules();

        if (SESSION.selectedModule != null && !modules.contains(SESSION.selectedModule)) {
            SESSION.selectedModule = null;
        }

        float inset = MAIN_PADDING * scale;
        float listX = panelX + inset;
        float listY = panelY + 1.0f * scale;
        float listW = MODULE_LIST_WIDTH * scale;
        float viewportH = BODY_HEIGHT * scale - 2.0f * scale;
        float settingsX = listX + listW + PANE_GAP * scale;
        float settingsW = SETTINGS_PANEL_WIDTH * scale;
        float settingsY = listY;

        float dividerX = listX + listW + (PANE_GAP * 0.5f) * scale;
        Render2D.rect(dividerX, listY, 0.55f * scale, viewportH, 0.2f * scale,
                color(88, 82, 94, 65, alpha));

        renderModuleList(graphics, modules, listX, listY, listW, viewportH, scale, alpha);
        renderInspector(graphics, settingsX, settingsY, settingsW, viewportH, scale, alpha);
    }

    private static void renderModuleList(DrawContext graphics, List<Module> modules,
                                         float x, float y, float width, float height,
                                         float scale, float alpha) {
        double mouseX = SESSION.pointerX;
        double mouseY = SESSION.pointerY;
        float inner = 0.0f;
        float rowW = width;
        float rowH = MODULE_ROW_HEIGHT * scale;
        float gap = MODULE_ROW_GAP * scale;
        float cursorY = y - SESSION.scrollAnimation.get() * scale;
        long now = UiFrameContext.nowMillis();

        Set<Module> currentVisible = Collections.newSetFromMap(new IdentityHashMap<>());
        currentVisible.addAll(modules);
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            CardMotionState state = CARD_MOTION.computeIfAbsent(module, ignored -> new CardMotionState());
            if (!lastVisibleModules.contains(module)) {
                state.beginEntrance(now, Math.min(i, 6));
            }
        }
        lastVisibleModules = currentVisible;

        Render2D.pushScissor(graphics, x, y, width, height);
        try {
            if (modules.isEmpty()) {
                Render2D.text(FontType.BOLD, LanguageManager.translate("clickgui.no_modules"), x + 7.0f * scale, y + 8.0f * scale,
                        7.8f * scale, color(128, 124, 136, 225, alpha));
                return;
            }

            for (Module module : modules) {
                float rowY = cursorY;
                cursorY += rowH + gap;
                CardMotionState motion = CARD_MOTION.computeIfAbsent(module, ignored -> new CardMotionState());
                boolean inViewport = !(rowY + rowH < y || rowY > y + height);
                if (inViewport && !motion.wasInViewport()) {
                    int visibleSlot = Math.max(0, Math.round((rowY - y) / Math.max(1.0f, rowH + gap)));
                    motion.beginEntrance(now, Math.min(visibleSlot, 5));
                }
                motion.setInViewport(inViewport);
                if (!inViewport) continue;

                ModuleOption option = optionFor(module);
                option.beginInteractionFrame();
                boolean hovered = inside(mouseX, mouseY, x + inner, rowY, rowW, rowH);
                float enabled = option.enabledProgress();

                motion.updateHover(hovered);
                float hover = motion.hover();
                float entrance = motion.entrance(now);

                float centerX = x + inner + rowW * 0.5f;
                float centerY = rowY + rowH * 0.5f;
                float pointerX = clampMotion((float) ((mouseX - centerX) / Math.max(1.0f, rowW * 0.5f)));
                float pointerY = clampMotion((float) ((mouseY - centerY) / Math.max(1.0f, rowH * 0.5f)));
                float dockOffset = (1.0f - entrance) * 11.05f * scale;
                float visualX = x + inner + dockOffset + pointerX * 0.34f * scale * hover;
                float visualY = rowY + (1.0f - entrance) * 3.64f * scale
                        - 0.34f * scale * hover + pointerY * 0.10f * scale * hover;
                float visualW = rowW - (1.0f - entrance) * 2.85f * scale;
                float cardAlpha = alpha * entrance;

                float activeLift = 0.46f * enabled;

                new GuiSurface(
                        visualX, visualY, visualW, rowH, 3.4f * scale,
                        cardAlpha * (0.985f + 0.015f * enabled),
                        activeLift,
                        ThemeColors.clickGuiModuleColor(255, 1.0f),
                        ColorUtil.rgba(244, 244, 247, 255)
                ).render(graphics);

                if (enabled > 0.01f) {
                    Render2D.rect(visualX, visualY, visualW, rowH, 3.4f * scale,
                            ThemeColors.clickGuiEnabledModuleColor(Math.round(25.0f * enabled), cardAlpha));
                }
                int outlineAlpha = Math.round(52.0f + 34.0f * enabled + 20.0f * hover);
                int outlineColor = enabled > 0.01f
                        ? ThemeColors.clickGuiEnabledModuleColor(outlineAlpha, cardAlpha)
                        : ThemeColors.clickGuiMutedColor(outlineAlpha, cardAlpha);
                ClickGuiOutline.draw(visualX, visualY, visualW, rowH, 5.0f * scale,
                        0.72f * scale, outlineColor);

                String title = module.getDisplayName();
                String description = module.getDisplayDescription();
                float titleSize = 8.65f * scale;
                float descSize = 5.85f * scale;

                float contentLeft = visualX + 7.2f * scale;
                float rightReserve = ModuleBindBadge.leftReservedWidth(option, scale)
                        + ModuleSettingsIcon.reservedWidth(option, scale);
                float contentRight = visualX + visualW - rightReserve;
                float availableW = Math.max(8.0f, contentRight - contentLeft);

                float titleTop = visualY + 7.15f * scale;
                float descTop = visualY + 20.15f * scale;
                float titleY = OptionStyle.centeredTextY(
                        FontType.BOLD, title, titleSize, titleTop, 8.9f * scale
                );
                float descY = OptionStyle.centeredTextY(
                        FontType.SEMIBOLD, description, descSize, descTop, 7.2f * scale
                );

                Render2D.pushScissor(graphics, contentLeft, visualY, availableW, rowH);
                try {
                    Render2D.text(FontType.BOLD, title, contentLeft, titleY, titleSize,
                            ThemeColors.clickGuiTextColor(enabled > 0.01f ? 255 : 252, cardAlpha));
                    Render2D.text(FontType.SEMIBOLD, description, contentLeft, descY, descSize,
                            ThemeColors.clickGuiMutedColor(225, cardAlpha));
                } finally {
                    Render2D.popScissor(graphics);
                }

                ModuleSettingsIcon.render(graphics, visualX, visualY, visualW, rowH, option, scale, cardAlpha);
                ModuleBindBadge.render(graphics, visualX, visualY, visualW, rowH, option, scale, cardAlpha);
            }
        } finally {
            Render2D.popScissor(graphics);
        }
    }

    private static float clampMotion(float value) {
        return Math.max(-1.0f, Math.min(1.0f, value));
    }

    private static final class CardMotionState {
        private static final long ENTRANCE_DURATION_MS = 250L;
        private static final long STAGGER_MS = 18L;
        private static final long MAX_STAGGER_MS = 108L;

        private final SmoothAnimation hoverAnimation = new SmoothAnimation();
        private long entranceStart = Long.MIN_VALUE;
        private boolean inViewport;

        private CardMotionState() {
            hoverAnimation.set(0.0);
        }

        private void beginEntrance(long now, int index) {
            long delay = Math.min(MAX_STAGGER_MS, Math.max(0L, index * STAGGER_MS));
            entranceStart = now + delay;
        }

        private boolean wasInViewport() {
            return inViewport;
        }

        private void setInViewport(boolean value) {
            inViewport = value;
        }

        private void updateHover(boolean hovered) {
            hoverAnimation.run(hovered ? 1.0 : 0.0, hovered ? 0.18 : 0.22,
                    Easings.CUBIC_OUT, true);
            hoverAnimation.update();
        }

        private float hover() {
            return Math.max(0.0f, Math.min(1.0f, hoverAnimation.get()));
        }

        private float entrance(long now) {
            if (entranceStart == Long.MIN_VALUE) return 1.0f;
            float p = Math.max(0.0f, Math.min(1.0f,
                    (now - entranceStart) / (float) ENTRANCE_DURATION_MS));
            if (p >= 1.0f) {
                entranceStart = Long.MIN_VALUE;
                return 1.0f;
            }

            float cubic = 1.0f - (float) Math.pow(1.0f - p, 3.0);
            float settle = (float) Math.sin(p * Math.PI) * (1.0f - p) * 0.035f;
            return Math.max(0.0f, Math.min(1.0f, cubic + settle));
        }
    }

    private static void renderInspector(DrawContext graphics,
                                        float x, float y, float width, float height,
                                        float scale, float alpha) {
        Module module = SESSION.selectedModule;
        if (module == null) {
            String emptyText = LanguageManager.translate("clickgui.select_module");
            float textSize = 7.2f * scale;
            float textW = Render2D.textWidth(FontType.SEMIBOLD, emptyText, textSize);
            float textH = Render2D.textHeight(FontType.SEMIBOLD, emptyText, textSize);
            Render2D.text(FontType.SEMIBOLD, emptyText,
                    x + (width - textW) * 0.5f, y + (height - textH) * 0.5f,
                    textSize, ThemeColors.clickGuiMutedColor(235, alpha));
            return;
        }

        ModuleOption option = optionFor(module);
        if (!option.hasSettings()) {
            String emptyText = LanguageManager.translate("clickgui.no_settings_hint");
            float textSize = 7.2f * scale;
            float textW = Render2D.textWidth(FontType.SEMIBOLD, emptyText, textSize);
            float textH = Render2D.textHeight(FontType.SEMIBOLD, emptyText, textSize);
            Render2D.text(FontType.SEMIBOLD, emptyText,
                    x + (width - textW) * 0.5f, y + (height - textH) * 0.5f,
                    textSize, ThemeColors.clickGuiMutedColor(235, alpha));
            return;
        }
        option.revealSettings();

        Render2D.pushScissor(graphics, x, y, width, height);
        try {
            option.renderDetachedSettings(graphics, x, y, width, height,
                    scale, alpha, SESSION.settingsScrollAnimation.get());
        } finally {
            Render2D.popScissor(graphics);
        }

        if (SESSION.settingsScrollLimit > 0.1f) {
            float trackHeight = Math.max(1.0f, height - 8.0f*scale);
            float thumbHeight = Math.max(15.0f*scale,
                    trackHeight*height/(height+SESSION.settingsScrollLimit*scale));
            float progress = Math.max(0.0f,Math.min(1.0f,
                    SESSION.settingsScrollAnimation.get()/SESSION.settingsScrollLimit));
            float barX = x + width - 2.7f*scale;
            Render2D.rect(barX, y+4.0f*scale,1.2f*scale,trackHeight,
                    0.55f*scale,ThemeColors.clickGuiMutedColor(36,alpha));
            Render2D.rect(barX, y+4.0f*scale+progress*(trackHeight-thumbHeight),
                    1.2f*scale,thumbHeight,0.55f*scale,
                    ThemeColors.clickGuiAccentColor(190,alpha));
        }
    }

    static void renderModuleOverlays(DrawContext graphics) {
        if ((SESSION.selectedCategory == ModuleCategory.CONFIGS && (SESSION.query == null || SESSION.query.isBlank())) || SESSION.selectedHwEvents || isBodyEditorOpen()) return;
        if (SESSION.selectedModule != null) optionFor(SESSION.selectedModule).renderDetachedOverlay(graphics);
    }

    static float columnOffset(List<Module> modules, int index) {
        if (index < 0) return 0.0f;
        return index * (MODULE_ROW_HEIGHT + MODULE_ROW_GAP);
    }

    static List<Module> visibleModules() {
        Collection<Module> source = modules();
        LanguageCode language = LanguageManager.current();
        String needle = SESSION.query == null ? "" : SESSION.query.trim().toLowerCase(Locale.ROOT);
        long sourceRevision = modulesRevision();
        if (SESSION.visibleModulesCacheCategory == SESSION.selectedCategory
                && SESSION.visibleModulesCacheHwEvents == SESSION.selectedHwEvents
                && SESSION.visibleModulesCacheLanguage == language
                && SESSION.visibleModulesCacheCount == source.size()
                && SESSION.visibleModulesCacheRevision == sourceRevision
                && needle.equals(SESSION.visibleModulesCacheQuery)) {
            return SESSION.visibleModulesCache;
        }

        List<Module> result = new ArrayList<>();
        for (Module module : source) {

            if (needle.isEmpty()) {
                if (!matchesSelectedTab(module)) continue;
            } else if (!isGlobalSearchCategory(module)) {
                continue;
            }

            if (!needle.isEmpty()) {
                String display = module.getDisplayName() == null ? "" : module.getDisplayName();
                String raw = module.getName() == null ? "" : module.getName();
                String displayLower = display.toLowerCase(Locale.ROOT);
                String rawLower = raw.toLowerCase(Locale.ROOT);
                if (!displayLower.contains(needle) && !rawLower.contains(needle)) {
                    continue;
                }
            }
            result.add(module);
        }
        result.sort(Comparator.comparing(Module::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        SESSION.visibleModulesCache = List.copyOf(result);
        SESSION.visibleModulesCacheQuery = needle;
        SESSION.visibleModulesCacheCategory = SESSION.selectedCategory;
        SESSION.visibleModulesCacheHwEvents = SESSION.selectedHwEvents;
        SESSION.visibleModulesCacheLanguage = language;
        SESSION.visibleModulesCacheCount = source.size();
        SESSION.visibleModulesCacheRevision = sourceRevision;
        return SESSION.visibleModulesCache;
    }
}
