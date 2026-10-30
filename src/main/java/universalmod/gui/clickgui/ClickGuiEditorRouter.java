package universalmod.gui.clickgui;

import universalmod.lifecycle.Manager;
import universalmod.module.Module;
import universalmod.module.ModuleManager;
import universalmod.module.misc.CustomCrosshair;
import universalmod.module.render.ItemReplacer;
import universalmod.service.language.LanguageManager;

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
