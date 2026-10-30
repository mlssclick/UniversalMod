package universalmod.command;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import universalmod.service.chat.ChatMessenger;

import java.util.*;
import java.util.stream.Stream;

public abstract class Command {
    private final String name, description;
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
        List<String> out = new ArrayList<>();
        out.add(name);
        out.addAll(aliases);
        return out;
    }

    public boolean matches(String input) {
        return name.equalsIgnoreCase(input) || aliases.stream().anyMatch(a -> a.equalsIgnoreCase(input));
    }

    protected void logDirect(String message) {
        ChatMessenger.sendArrow(message);
    }

    protected void logDirect(String message, Formatting formatting) {
        CommandManager m = CommandManager.getInstance();
        if (m == null) {
            ChatMessenger.sendArrow(message);
            return;
        }
        if (formatting == Formatting.RED) m.sendError(message);
        else if (formatting == Formatting.GREEN) m.sendSuccess(message);
        else m.sendMessage(message);
    }

    protected void logDirect(Text text) {
        ChatMessenger.sendArrow(text);
    }

    protected void logDirect(MutableText text) {
        ChatMessenger.sendArrow(text);
    }

    protected void logDirectRaw(Text text) {
        CommandManager m = CommandManager.getInstance();
        if (m != null) m.sendRaw(text);
    }

    protected void logDirectRaw(MutableText text) {
        CommandManager m = CommandManager.getInstance();
        if (m != null) m.sendRaw(text);
    }
}
