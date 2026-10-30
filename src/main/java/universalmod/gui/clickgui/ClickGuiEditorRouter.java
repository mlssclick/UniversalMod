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

import static universalmod.gui.clickgui.ClickGuiController.*;

final class ClickGuiEditorRouter {
    private ClickGuiEditorRouter() {
    }

    public static void openCustomCrosshairEditor(CustomCrosshair module) {
        if (!canOpen(module)) return;
        SESSION.customCrosshairEditorOpen = true;
        SESSION.itemReplacerEditorOpen = false;
        SESSION.searchFocused = false;
        SESSION.configSearchFocused = false;
        SESSION.query = "";
        resetScroll();
        SESSION.customCrosshairPanel.open(module);
    }
    static void closeCustomCrosshairEditor() {
        SESSION.customCrosshairEditorOpen = false;
        SESSION.searchFocused = false;
        SESSION.configSearchFocused = false;
        resetScroll();
    }
    public static void openItemReplacerEditor(ItemReplacer module) {
        if (!canOpen(module)) return;
        SESSION.itemReplacerEditorOpen = true;
        SESSION.customCrosshairEditorOpen = false;
        SESSION.searchFocused = false;
        SESSION.configSearchFocused = false;
        SESSION.query = "";
        resetScroll();
        SESSION.itemReplacerPanel.open(module);
    }
    static void closeItemReplacerEditor() {
        SESSION.itemReplacerEditorOpen = false;
        SESSION.searchFocused = false;
        SESSION.configSearchFocused = false;
        resetScroll();
    }
    static boolean isBodyEditorOpen() {
        return SESSION.customCrosshairEditorOpen || SESSION.itemReplacerEditorOpen;
    }
    static void closeBodyEditor() {
        if (SESSION.customCrosshairEditorOpen) {
            closeCustomCrosshairEditor();
        }
        if (SESSION.itemReplacerEditorOpen) {
            closeItemReplacerEditor();
        }
    }
    static String activeEditorTitle() {
        if (SESSION.customCrosshairEditorOpen) {
            return LanguageManager.translate("crosshair_editor.title");
        }
        if (SESSION.itemReplacerEditorOpen) {
            return LanguageManager.translate("item_replacer.title");
        }
        return "";
    }

    static String activeTitle() {
        for (CategoryTab tab : TABS) {
            if (tab.active()) {
                return tab.displayLabel();
            }
        }
        return LanguageManager.categoryName("Render");
    }
    static String activeDescription() {
        for (CategoryTab tab : TABS) {
            if (!tab.active()) continue;
            return switch (tab.label()) {
                case "Render" -> "Visuals feature for gameplay";
                case "Utils" -> "Utilites for best game experience";
                case "Misc" -> "Miscellanius for xz";
                case "Hw Events" -> "Events on holyworld";
                case "Configs" -> "Profile and config management";
                default -> "";
            };
        }
        return "";
    }

    static void validateVisibility() {
        if (SESSION.customCrosshairEditorOpen && !canOpen(CustomCrosshair.class)) {
            closeCustomCrosshairEditor();
        }
        if (SESSION.itemReplacerEditorOpen && !canOpen(ItemReplacer.class)) {
            closeItemReplacerEditor();
        }
    }

    private static boolean canOpen(Module module) {
        ModuleManager manager = Manager.getModules();
        return manager != null && manager.isVisible(module);
    }

    private static boolean canOpen(Class<? extends Module> type) {
        ModuleManager manager = Manager.getModules();
        return manager != null && manager.getByType(type).map(manager::isVisible).orElse(false);
    }
}
