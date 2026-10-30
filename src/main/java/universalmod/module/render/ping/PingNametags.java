package universalmod.module.render.ping;

import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;

@ModuleInformation(
        moduleName = "Ping Nametags",
        moduleCategory = ModuleCategory.RENDER
)
public final class PingNametags extends Module {
    private static PingNametags instance;

    public PingNametags() {
        instance = this;
        setEnabled(true);
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }
}
