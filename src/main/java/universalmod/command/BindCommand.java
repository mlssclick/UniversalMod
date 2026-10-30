package universalmod.command;

import org.lwjgl.glfw.GLFW;
import universalmod.command.Command;
import universalmod.command.TabCompletionBuilder;
import universalmod.config.ConfigManager;
import universalmod.module.Module;
import universalmod.module.ModuleManager;
import universalmod.setting.bind.KeyBind;
import universalmod.util.input.KeyCodeNames;
import net.minecraft.util.Formatting;

import java.util.*;
import java.util.stream.Stream;

public final class BindCommand extends Command {
    private final ModuleManager modules;
    private final ConfigManager configManager;

    public BindCommand(ModuleManager modules, ConfigManager configManager) {
        super("bind", "Manage module binds", "b");
        this.modules = modules;
        this.configManager = configManager;
    }

    public void execute(String label, String[] args) {
        if (modules == null) {
            logDirect("Module manager is unavailable.", Formatting.RED);
            return;
        }
        String action = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "list";
        switch (action) {
            case "set", "add" -> setBind(modules, args);
            case "remove", "del", "delete" -> clearBind(modules, args);
            case "clear" -> clearAll(modules);
            case "list" -> listBinds(modules);
            default -> logDirect("Usage: bind set <module> <key> | bind remove <module> | bind list | bind clear");
        }
    }

    public Stream<String> tabComplete(String label, String[] args) {
        if (args.length == 1)
            return new TabCompletionBuilder().append("set", "remove", "list", "clear").sortAlphabetically().filterPrefix(args[0]).stream();
        if (modules == null) return Stream.empty();
        if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("remove")))
            return new TabCompletionBuilder().append(modules.getModules().stream().map(Module::getName).toArray(String[]::new)).filterPrefix(args[1]).stream();
        if (args.length == 3 && args[0].equalsIgnoreCase("set"))
            return new TabCompletionBuilder().append(KeyCodeNames.getAllKeyNames()).filterPrefix(args[2]).stream();
        return Stream.empty();
    }

    public List<String> getLongDesc() {
        return Arrays.asList("Manages module key binds.", "Usage:", "> bind set <module> <key>", "> bind remove <module>", "> bind list", "> bind clear");
    }

    private void setBind(ModuleManager m, String[] a) {
        if (a.length < 3) {
            logDirect("Usage: bind set <module> <key>", Formatting.RED);
            return;
        }
        Module module = find(m, a[1]);
        if (module == null) {
            logDirect("Module not found: " + a[1], Formatting.RED);
            return;
        }
        int code = KeyCodeNames.getKeyCode(a[2]);
        if (code == GLFW.GLFW_KEY_UNKNOWN || code < 0) {
            logDirect("Unknown key: " + a[2], Formatting.RED);
            return;
        }
        module.setBind(isMouseName(a[2]) ? KeyBind.mouse(code) : KeyBind.keyboard(code));
        save();
        logDirect(module.getName() + " bound to " + module.getBind().getDisplayName(), Formatting.GREEN);
    }

    private void clearBind(ModuleManager m, String[] a) {
        if (a.length < 2) {
            logDirect("Usage: bind remove <module>", Formatting.RED);
            return;
        }
        Module module = find(m, a[1]);
        if (module == null) {
            logDirect("Module not found: " + a[1], Formatting.RED);
            return;
        }
        module.setBind(KeyBind.NONE);
        save();
        logDirect(module.getName() + " bind removed.", Formatting.GREEN);
    }

    private void clearAll(ModuleManager m) {
        for (Module module : m.getModules()) module.setBind(KeyBind.NONE);
        save();
        logDirect("All binds removed.", Formatting.GREEN);
    }

    private void listBinds(ModuleManager m) {
        List<Module> bound = m.getModules().stream().filter(x -> x.getBind().isBound()).sorted(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER)).toList();
        if (bound.isEmpty()) {
            logDirect("No module binds.", Formatting.RED);
            return;
        }
        for (Module module : bound) logDirect(module.getName() + " -> " + module.getBind().getDisplayName());
    }

    private Module find(ModuleManager m, String n) {
        return m.getByName(n).orElse(null);
    }

    private boolean isMouseName(String n) {
        String v = n == null ? "" : n.trim().toUpperCase(Locale.ROOT);
        return v.startsWith("MOUSE") || v.matches("M\\d+") || v.equals("LMB") || v.equals("RMB") || v.equals("MMB");
    }

    private void save() {
        if (configManager != null) configManager.saveAll();
    }
}
