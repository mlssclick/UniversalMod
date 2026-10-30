package universalmod.command;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import universalmod.command.BindCommand;
import universalmod.command.FriendCommand;
import universalmod.command.GpsCommand;
import universalmod.command.HelpCommand;
import universalmod.event.SubscribeEvent;
import universalmod.event.type.ChatEvent;
import universalmod.event.type.TabCompleteEvent;
import universalmod.util.QuickLogger;
import universalmod.storage.RepositoryStorage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;




public final class CommandManager {
    private static CommandManager instance;
    private final List<Command> commands = new CopyOnWriteArrayList<>();
    private String prefix = ".";

    public CommandManager() {
        instance = this;
    }

    public static CommandManager getInstance() {
        return instance;
    }

    public void init() {
        JsonObject prefixConfig = RepositoryStorage.readObject("prefix.universalmod");
        prefix = prefixConfig.has("prefix") ? prefixConfig.get("prefix").getAsString() : ".";
        registerCommand(new HelpCommand());
        registerCommand(new FriendCommand());
        registerCommand(new BindCommand());
        registerCommand(new GpsCommand());
    }

    public void registerCommand(Command command) {
        if (command != null) commands.add(command);
    }

    public Command getCommand(String name) {
        return commands.stream().filter(command -> command.matches(name)).findFirst().orElse(null);
    }

    public List<Command> getCommands() {
        return new ArrayList<>(commands);
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix == null || prefix.isBlank() ? "." : prefix;
        JsonObject object = new JsonObject();
        object.addProperty("prefix", this.prefix);
        RepositoryStorage.write("prefix.universalmod", object);
    }

    @SubscribeEvent
    private void onChat(ChatEvent event) {
        String msg = event.getMessage();
        if (!msg.startsWith(prefix)) return;

        event.cancel();
        String commandStr = msg.substring(prefix.length());
        if (commandStr.trim().isEmpty()) {
            execute("help");
            return;
        }
        if (!execute(commandStr)) {
            sendError("Unknown command. Use " + prefix + "help.");
        }
    }

    @SubscribeEvent
    private void onTabComplete(TabCompleteEvent event) {
        String eventPrefix = event.getPrefix();
        if (!eventPrefix.startsWith(prefix)) return;

        String msg = eventPrefix.substring(prefix.length());
        Stream<String> stream = tabComplete(msg);
        String[] parts = msg.split(" ", -1);
        if (parts.length <= 1) stream = stream.map(value -> prefix + value);
        event.setCompletions(stream.toArray(String[]::new));
    }

    public boolean execute(String input) {
        if (input == null || input.trim().isEmpty()) return execute("help");

        String[] parts = input.trim().split("\\s+", 2);
        String commandName = parts[0];
        String[] args = parts.length > 1 ? parts[1].split("\\s+") : new String[0];
        Command command = getCommand(commandName);
        if (command == null) return false;

        try {
            command.execute(commandName, args);
            return true;
        } catch (Exception exception) {
            String message = exception.getMessage();
            sendError("Command error: " + (message == null || message.isBlank() ? exception.getClass().getSimpleName() : message));

            return true;
        }
    }

    public Stream<String> tabComplete(String input) {
        if (input == null) input = "";
        String[] args = input.split("\\s+", -1);
        if (args.length <= 1) {
            String partial = args.length == 0 ? "" : args[0].toLowerCase();
            return getCommandSuggestions(partial);
        }
        Command command = getCommand(args[0]);
        if (command != null) {
            return command.tabComplete(args[0], Arrays.copyOfRange(args, 1, args.length));
        }
        return Stream.empty();
    }

    private Stream<String> getCommandSuggestions(String partial) {
        Set<String> suggestions = new LinkedHashSet<>();
        for (Command command : commands) {
            if (command.getName().toLowerCase().startsWith(partial)) suggestions.add(command.getName());
            for (String alias : command.getAliases()) {
                if (alias.toLowerCase().startsWith(partial)) suggestions.add(alias);
            }
        }
        return suggestions.stream().sorted();
    }

    public void sendMessage(String message) {
        sendPrefixed(Text.literal(message == null ? "" : message).formatted(Formatting.GRAY));
    }

    public void sendSuccess(String message) {
        sendPrefixed(Text.literal(message == null ? "" : message).formatted(Formatting.GREEN));
    }

    public void sendError(String message) {
        sendPrefixed(Text.literal(message == null ? "" : message).formatted(Formatting.RED));
    }

    public void sendPrefixed(Text text) {
        MutableText component = Text.empty();
        component.append(QuickLogger.getPrefix());
        component.append(text == null ? Text.empty() : text);
        sendRaw(component);
    }

    public void sendRaw(Text text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.inGameHud != null && text != null) {
            client.inGameHud.getChatHud().addMessage(text);
        }
    }
}
