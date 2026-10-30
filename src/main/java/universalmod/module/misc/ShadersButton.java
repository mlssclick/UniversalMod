package universalmod.module.misc;

import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;

@ModuleInformation(
        moduleName = "Shaders Button",
        moduleCategory = ModuleCategory.MISC
)
public final class ShadersButton extends Module {
    private static ShadersButton instance;

    public ShadersButton() {
        instance = this;
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }
}
