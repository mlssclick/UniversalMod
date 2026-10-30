package universalmod.lifecycle;

import universalmod.config.ConfigManager;
import universalmod.module.ModuleManager;






public final class Manager {
    private static final Manager INSTANCE = new Manager();

    private Manager() {}

    public static Manager getInstance() {
        return ClientRuntime.getInstance() == null ? null : INSTANCE;
    }

    public static ModuleManager getModules() {
        ClientRuntime runtime = ClientRuntime.getInstance();
        return runtime == null ? null : runtime.getModuleManager();
    }

    public ModuleManager getModuleManager() {
        return getModules();
    }

    public ConfigManager getConfigManager() {
        ClientRuntime runtime = ClientRuntime.getInstance();
        return runtime == null ? null : runtime.getConfigManager();
    }
}
