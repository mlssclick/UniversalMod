package universalmod.module.misc;

import universalmod.module.Module;
import universalmod.module.ModuleCategory;

public final class ShadersButton extends Module {
    private static ShadersButton instance;

    public ShadersButton() {
        super("shaders_button", "Shaders Button", ModuleCategory.MISC);
        instance = this;
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }
}
