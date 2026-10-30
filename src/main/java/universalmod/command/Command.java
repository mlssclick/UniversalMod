package universalmod.command;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;




public abstract class Command {
    private final String name;
    private final String description;
    private final List<String> aliases;

    protected Command(String name, String description, String... aliases) {
        this.name = name;
        this.description = description == null ? "" : description;
        this.aliases = Arrays.asList(aliases == null ? new String[0] : aliases);
    }

    public abstract void execute(String label, String[] args);

    public Stream<String> tabComplete(String label, String[] args) {
        return Stream.empty();
    }

    public String getShortDesc() {
        return description;
    }

    public List<String> getLongDesc() {
        return Arrays.asList(description, "", "Usage:", "> " + name + " - " + description);
    }

    public boolean hiddenFromHelp() {
        return false;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public List<String> getAllNames() {
        List<String> names = new ArrayList<>();
        names.add(name);
        names.addAll(aliases);
        return names;
    }

    public boolean matches(String input) {
        return name.equalsIgnoreCase(input) || aliases.stream().anyMatch(alias -> alias.equalsIgnoreCase(input));
    }

    protected void logDirect(String message) {
        CommandManager manager = CommandManager.getInstance();
        if (manager != null) manager.sendMessage(message);
    }

    protected void logDirect(String message, Formatting formatting) {
        CommandManager manager = CommandManager.getInstance();
        if (manager == null) return;
        if (formatting == Formatting.RED) {
            manager.sendError(message);
        } else if (formatting == Formatting.GREEN) {
            manager.sendSuccess(message);
        } else {
            manager.sendPrefixed(Text.literal(message == null ? "" : message).formatted(formatting == null ? Formatting.GRAY : formatting));
        }
    }

    protected void logDirect(Text text) {
        CommandManager manager = CommandManager.getInstance();
        if (manager != null) manager.sendPrefixed(text);
    }

    protected void logDirectRaw(Text text) {
        CommandManager manager = CommandManager.getInstance();
        if (manager != null) manager.sendRaw(text);
    }
}
