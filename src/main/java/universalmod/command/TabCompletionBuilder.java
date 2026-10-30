package universalmod.command;

import universalmod.command.Command;
import universalmod.command.CommandManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class TabCompletionBuilder {
    private final List<String> values = new ArrayList<>();

    public TabCompletionBuilder append(String... entries) {
        if (entries != null) {
            for (String entry : entries) {
                if (entry != null) {
                    values.add(entry);
                }
            }
        }
        return this;
    }

    public TabCompletionBuilder addCommands(CommandManager manager) {
        if (manager != null) {
            for (Command command : manager.getCommands()) {
                if (command.hiddenFromHelp()) {
                    continue;
                }
                values.add(command.getName());
                values.addAll(command.getAliases());
            }
        }
        return this;
    }

    public TabCompletionBuilder sortAlphabetically() {
        values.sort(Comparator.comparing(String::toLowerCase));
        return this;
    }

    public TabCompletionBuilder filterPrefix(String prefix) {
        String lowered = prefix == null ? "" : prefix.toLowerCase();
        values.removeIf(value -> !value.toLowerCase().startsWith(lowered));
        return this;
    }

    public Stream<String> stream() {
        return values.stream().distinct();
    }
}
