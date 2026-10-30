package universalmod.module.utility;

import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.feature.savekt.SaveKtManager;

public final class SaveKtLeave extends Module {
    private static SaveKtLeave instance;

    public SaveKtLeave() {
        super("save_kt_leave", "Save Kt Leave", ModuleCategory.UTILS);
        instance = this;
    }

    public static SaveKtLeave getInstance() {
        return instance;
    }

    @Override
    protected void onDisable() {
        SaveKtManager.reset();
    }
}
