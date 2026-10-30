package universalmod.command;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import universalmod.command.*;
import universalmod.event.SubscribeEvent;
import universalmod.event.type.ChatEvent;
import universalmod.event.type.TabCompleteEvent;
import universalmod.storage.RepositoryStorage;
import universalmod.service.chat.ChatMessenger;
import universalmod.module.ModuleManager;
import universalmod.config.ConfigManager;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

public final class CommandManager {
    private static CommandManager instance;
    private final List<Command> commands = new CopyOnWriteArrayList<>();
    private String prefix = ".";
    private final ModuleManager moduleManager;
    private final ConfigManager configManager;

    public CommandManager(ModuleManager moduleManager, ConfigManager configManager) {
        instance = this;
        this.moduleManager = moduleManager;
        this.configManager = configManager;
    }

    public static CommandManager getInstance() {
        return instance;
    }

    public void init() {
        JsonObject cfg = RepositoryStorage.readObject("prefix.universalmod");
        prefix = cfg.has("prefix") ? cfg.get("prefix").getAsString() : ".";
        registerCommand(new HelpCommand());
        registerCommand(new FriendCommand());
        registerCommand(new BindCommand(moduleManager, configManager));
        registerCommand(new GpsCommand());
    }

    public void registerCommand(Command c) {
        if (c != null && getCommand(c.getName()) == null) commands.add(c);
    }

    public Command getCommand(String name) {
        return commands.stream().filter(c -> c.matches(name)).findFirst().orElse(null);
    }

    public List<Command> getCommands() {
        return new ArrayList<>(commands);
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix == null || prefix.isBlank() ? "." : prefix;
        JsonObject o = new JsonObject();
        o.addProperty("prefix", this.prefix);
        RepositoryStorage.write("prefix.universalmod", o);
    }

    @SubscribeEvent
    private void onChat(ChatEvent event) {
        String msg = event.getMessage();
        if (!msg.startsWith(prefix)) return;
        event.cancel();
        String command = msg.substring(prefix.length());
        if (command.trim().isEmpty()) {
            execute("help");
            return;
        }
        if (!execute(command)) sendError("Unknown command. Use " + prefix + "help.");
    }

    @SubscribeEvent
    private void onTabComplete(TabCompleteEvent event) {
        String p = event.getPrefix();
        if (!p.startsWith(prefix)) return;
        String msg = p.substring(prefix.length());
        Stream<String> stream = tabComplete(msg);
        String[] parts = msg.split(" ", -1);
        if (parts.length <= 1) stream = stream.map(v -> prefix + v);
        event.setCompletions(stream.toArray(String[]::new));
        event.cancel();
    }

    public boolean execute(String input) {
        if (input == null || input.trim().isEmpty()) return execute("help");
        String[] parts = input.trim().split("\\s+", 2);
        Command c = getCommand(parts[0]);
        if (c == null) return false;
        String[] args = parts.length > 1 ? parts[1].split("\\s+") : new String[0];
        try {
            c.execute(parts[0], args);
            return true;
        } catch (Exception e) {
            sendError("Command error: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));

            return true;
        }
    }

    public Stream<String> tabComplete(String input) {
        if (input == null) input = "";
        String[] args = input.split("\\s+", -1);
        if (args.length <= 1) {
            String partial = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            Set<String> s = new LinkedHashSet<>();
            for (Command c : commands) {
                if (c.getName().toLowerCase(Locale.ROOT).startsWith(partial)) s.add(c.getName());
                for (String a : c.getAliases()) if (a.toLowerCase(Locale.ROOT).startsWith(partial)) s.add(a);
            }
            return s.stream().sorted();
        }
        Command c = getCommand(args[0]);
        return c == null ? Stream.empty() : c.tabComplete(args[0], Arrays.copyOfRange(args, 1, args.length));
    }

    public void sendMessage(String message) {
        ChatMessenger.sendArrow(message);
    }

    public void sendSuccess(String message) {
        sendRaw(Text.literal("UniversalMod -> ").formatted(Formatting.DARK_GRAY).append(Text.literal(message).formatted(Formatting.GREEN)));
    }

    public void sendError(String message) {
        sendRaw(Text.literal("UniversalMod -> ").formatted(Formatting.DARK_GRAY).append(Text.literal(message).formatted(Formatting.RED)));
    }

    public void sendRaw(Text text) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.player != null) mc.player.sendMessage(text, false);
    }
}
