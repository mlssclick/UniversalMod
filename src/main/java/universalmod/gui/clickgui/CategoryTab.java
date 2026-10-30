package universalmod.gui.clickgui;

import universalmod.module.ModuleCategory;
import universalmod.service.language.LanguageManager;

record CategoryTab(String label, ModuleCategory category, boolean hwEvents) {
    String displayLabel() {
        if (hwEvents) return LanguageManager.translate("hw_events.name");
        if (category == ModuleCategory.CONFIGS) return LanguageManager.translate("configs.name");
        return LanguageManager.categoryName(label);
    }

    boolean active() {
        return ClickGuiController.SESSION.selectedCategory == category
                && ClickGuiController.SESSION.selectedHwEvents == hwEvents;
    }
}
