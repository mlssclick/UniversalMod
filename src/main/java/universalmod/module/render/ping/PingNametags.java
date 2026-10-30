package universalmod.module.render.ping;

import universalmod.module.Module;
import universalmod.module.ModuleCategory;

public final class PingNametags extends Module {
    private static PingNametags instance;

    public PingNametags() {
        super("ping_nametags", "Ping Nametags", ModuleCategory.RENDER);
        instance = this;
        setEnabled(true);
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }
}
