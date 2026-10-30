package universalmod.module.utility;

import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;
import universalmod.feature.savekt.SaveKtManager;

@ModuleInformation(
        moduleName = "Save Kt Leave",
        moduleCategory = ModuleCategory.UTILS
)
public final class SaveKtLeave extends Module {
    private static SaveKtLeave instance;

    public SaveKtLeave() {
        instance = this;
    }

    public static SaveKtLeave getInstance() {
        return instance;
    }

    @Override
    public void onDisable() {
        SaveKtManager.reset();
        super.onDisable();
    }
}
