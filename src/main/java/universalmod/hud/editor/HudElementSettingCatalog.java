package universalmod.hud.editor;

import universalmod.hud.registry.HudElementId;
import universalmod.module.render.Hud;
import universalmod.setting.Setting;

import java.util.ArrayList;
import java.util.List;


final class HudElementSettingCatalog {
    private HudElementSettingCatalog() {
    }

    static List<Setting<?>> resolve(Hud hud, HudElementId elementId) {
        if (hud == null || elementId == null) {
            return List.of();
        }
        ArrayList<Setting<?>> result = new ArrayList<>();
        boolean keystrokes = elementId == HudElementId.KEYSTROKES;
        boolean bossbar = elementId == HudElementId.BOSSBAR;
        boolean armor = elementId == HudElementId.ARMOR;
        for (Setting<?> setting : hud.getSettings()) {
            String name = setting.getName();
            if (keystrokes && isKeystrokesSetting(name)) {
                result.add(setting);
            } else if (bossbar && isBossbarSetting(name)) {
                result.add(setting);
            } else if (armor && isArmorSetting(name)) {
                result.add(setting);
            }
        }
        return List.copyOf(result);
    }

    private static boolean isKeystrokesSetting(String name) {
        return name.equals("Keys") || name.equals("Mouse Buttons") || name.equals("Space") || name.startsWith("Keystrokes ");
    }

    private static boolean isBossbarSetting(String name) {
        return name.equals("Use Custom Bossbar Settings") || name.equals("Bossbar Color") || name.equals("Bossbar Text Color");
    }

    private static boolean isArmorSetting(String name) {
        return name.equals("Armor Mode") || name.equals("Armor Side");
    }
}
