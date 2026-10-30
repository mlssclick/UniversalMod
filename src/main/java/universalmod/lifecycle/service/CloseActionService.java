package universalmod.lifecycle.service;

import java.util.Objects;


public final class CloseActionService implements ClientService {
    private final String name;
    private final Runnable closeAction;

    public CloseActionService(String name, Runnable closeAction) {
        this.name = Objects.requireNonNullElse(name, "close-action");
        this.closeAction = Objects.requireNonNull(closeAction, "closeAction");
    }

    @Override
    public void close() {
        closeAction.run();
    }

    @Override
    public String toString() {
        return name;
    }
}
