package universalmod.gui.clickgui.component;

import universalmod.setting.BindSetting;


final class ModuleBindLabel {
    private ModuleBindLabel() {
    }

    static String format(boolean binding, BindSetting setting) {
        if (binding) {
            return "...";
        }
        if (setting == null || setting.getValue() == null || !setting.getValue().isBound()) {
            return "n/a";
        }
        String display = setting.getValue().getDisplayName();
        if (display == null || display.isBlank()) {
            return "n/a";
        }
        return display
                .replace("LEFT ", "L")
                .replace("RIGHT ", "R")
                .replace("MOUSE ", "M");
    }
}
