package universalmod.module.render;

import universalmod.module.Module;
import universalmod.module.ModuleCategory;

public final class TwoDItems extends Module {
    private static TwoDItems instance;

    public TwoDItems() {
        super("two_d_items", "2D Items", ModuleCategory.UTILS);
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
