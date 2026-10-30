package universalmod.module.render;

import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;

@ModuleInformation(
        moduleName = "2D Items",
        moduleCategory = ModuleCategory.UTILS
)
public final class TwoDItems extends Module {
    private static TwoDItems instance;

    public TwoDItems() {
        instance = this;
    }

    public static boolean isFeatureEnabled() {
        return instance != null && instance.isEnabled();
    }

    public static boolean shouldCastShadows() {
        return isFeatureEnabled();
    }

    public static boolean shouldRenderSidesOfItems() {
        return isFeatureEnabled();
    }

    public static boolean shouldAffect3DModels() {
        return isFeatureEnabled();
    }
}
