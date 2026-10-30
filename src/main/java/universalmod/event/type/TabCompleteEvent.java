package universalmod.event.type;

import universalmod.event.CancellableEvent;

public final class TabCompleteEvent extends CancellableEvent {
    private final String prefix;
    private String[] completions;

    public TabCompleteEvent(String prefix) {
        this.prefix = prefix == null ? "" : prefix;
    }

    public String getPrefix() {
        return prefix;
    }

    public String[] getCompletions() {
        return completions;
    }

    public void setCompletions(String[] completions) {
        this.completions = completions == null ? new String[0] : completions;
    }
}
