package universalmod.command;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import universalmod.command.BindCommand;
import universalmod.command.FriendCommand;
import universalmod.command.GpsCommand;
import universalmod.command.HelpCommand;
import universalmod.event.SubscribeEvent;
import universalmod.event.type.ChatEvent;
import universalmod.event.type.TabCompleteEvent;
import universalmod.storage.RepositoryStorage;
import universalmod.service.ChatMessage;
import universalmod.module.ModuleManager;
import universalmod.config.ConfigManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

public final class CommandManager {
    private final List<Command> commands = new CopyOnWriteArrayList<>();
    private final ModuleManager moduleManager;
    private final ConfigManager configManager;
    private String prefix = ".";

    public CommandManager(ModuleManager moduleManager, ConfigManager configManager) {
        this.moduleManager = java.util.Objects.requireNonNull(moduleManager, "moduleManager");
        this.configManager = java.util.Objects.requireNonNull(configManager, "configManager");
    }

    public ModuleManager modules() {
        return moduleManager;
    }

    public void saveConfig() {
        configManager.saveAll();
    }

    public void init() {
        com.google.gson.JsonObject prefixConfig = RepositoryStorage.readObject("prefix.universalmod");
        prefix = prefixConfig.has("prefix") ? prefixConfig.get("prefix").getAsString() : ".";
        registerCommand(new HelpCommand(this));
        registerCommand(new FriendCommand(this));
        registerCommand(new BindCommand(this));
        registerCommand(new GpsCommand(this));
    }

    public void registerCommand(Command command) {
        commands.add(command);
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
        com.google.gson.JsonObject object = new com.google.gson.JsonObject();
        object.addProperty("prefix", this.prefix);
        RepositoryStorage.write("prefix.universalmod", object);
    }

    @SubscribeEvent
    private void onChat(ChatEvent event) {
        String msg = event.getMessage();
        if (!msg.startsWith(prefix)) {
            return;
        }
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
        if (!eventPrefix.startsWith(prefix)) {
            return;
        }
        String msg = eventPrefix.substring(prefix.length());
        Stream<String> stream = tabComplete(msg);
        String[] parts = msg.split(" ", -1);
        if (parts.length <= 1) {
            stream = stream.map(value -> prefix + value);
        }
        event.setCompletions(stream.toArray(String[]::new));
    }

    public boolean execute(String input) {
        if (input == null || input.trim().isEmpty()) {
            return execute("help");
        }
        String[] parts = input.trim().split("\\s+", 2);
        String commandName = parts[0];
        String[] args = parts.length > 1 ? parts[1].split("\\s+") : new String[0];
        Command command = getCommand(commandName);
        if (command == null) {
            return false;
        }
        try {
            command.execute(commandName, args);
            return true;
        } catch (RuntimeException exception) {
            sendError("Command error: " + exception.getMessage());

            return true;
        }
    }

    public Stream<String> tabComplete(String input) {
        if (input == null) {
            input = "";
        }
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
            if (command.getName().toLowerCase().startsWith(partial)) {
                suggestions.add(command.getName());
            }
            for (String alias : command.getAliases()) {
                if (alias.toLowerCase().startsWith(partial)) {
                    suggestions.add(alias);
                }
            }
        }
        return suggestions.stream().sorted();
    }

    public void sendMessage(String message) {
        ChatMessage.brandmessage(message);
    }

    public void sendSuccess(String message) {
        sendRaw(ChatMessage.brandmessage().copy()
                .append(Component.literal(" -> ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(message).withStyle(ChatFormatting.GREEN)));
    }

    public void sendError(String message) {
        sendRaw(ChatMessage.brandmessage().copy()
                .append(Component.literal(" -> ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(message).withStyle(ChatFormatting.RED)));
    }

    public void sendRaw(Component text) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(text, false);
        }
    }
}
