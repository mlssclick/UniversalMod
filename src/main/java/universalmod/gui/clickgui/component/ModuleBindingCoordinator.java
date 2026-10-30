package universalmod.gui.clickgui.component;


public final class ModuleBindingCoordinator {
    private ModuleOption active;

    public ModuleOption active() {
        return active != null && active.isBinding() ? active : null;
    }

    void activate(ModuleOption option) {
        if (active == option) {
            return;
        }
        ModuleOption previous = active;
        active = option;
        if (previous != null) {
            previous.cancelBindingFromCoordinator();
        }
    }

    void release(ModuleOption option) {
        if (active == option) {
            active = null;
        }
    }

    public void clear() {
        ModuleOption previous = active;
        active = null;
        if (previous != null) {
            previous.cancelBindingFromCoordinator();
        }
    }
}
