package universalmod.module.utility;

import universalmod.module.Module;
import universalmod.module.ModuleCategory;

public final class HotbarCooldowns extends Module {
    private static HotbarCooldowns instance;

    public HotbarCooldowns() {
        super("cooldowns", "Cooldowns In Hotbar", ModuleCategory.UTILS);
        instance = this;
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }
}
