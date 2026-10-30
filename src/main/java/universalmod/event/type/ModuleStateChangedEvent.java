package universalmod.event.type;

import universalmod.event.Event;
import universalmod.module.Module;

import java.util.Objects;

public final class ModuleStateChangedEvent implements Event {
    private final Module module;
    private final boolean enabled;

    public ModuleStateChangedEvent(Module module, boolean enabled) {
        this.module = Objects.requireNonNull(module, "module");
        this.enabled = enabled;
    }

    public Module module() {
        return module;
    }

    public boolean enabled() {
        return enabled;
    }
}
