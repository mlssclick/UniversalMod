package universalmod.module.utility;

import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;

@ModuleInformation(
        moduleName = "Cooldowns In Hotbar",
        moduleCategory = ModuleCategory.UTILS
)
public final class HotbarCooldowns extends Module {
    private static HotbarCooldowns instance;

    public HotbarCooldowns() {
        instance = this;
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }
}
