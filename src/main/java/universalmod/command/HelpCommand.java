package universalmod.command;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import universalmod.command.Command;
import universalmod.command.CommandManager;
import universalmod.command.Paginator;
import universalmod.command.TabCompleteHelper;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class HelpCommand extends Command {
    public HelpCommand() {
        super("help", "Shows available commands");
    }

    @Override
    public void execute(String label, String[] args) {
        CommandManager manager = CommandManager.getInstance();
        if (args.length == 0 || isInteger(args[0])) {
            int page = args.length > 0 && isInteger(args[0]) ? Integer.parseInt(args[0]) : 1;
            List<Command> commands = manager.getCommands().stream().filter(command -> !command.hiddenFromHelp()).collect(Collectors.toList());
            Paginator<Command> paginator = new Paginator<>(commands);
            paginator.setPage(page);
            paginator.display(
                    () -> {
                        logDirectRaw(Text.literal(getLine()));
                        logDirect("Available commands");
                        logDirectRaw(Text.literal(getLine()));
                    },
                    command -> {
                        String fullName = manager.getPrefix() + command.getName();
                        MutableText hover = Text.literal(fullName).formatted(Formatting.WHITE)
                                .append(Text.literal("\n" + command.getShortDesc()).formatted(Formatting.GRAY));
                        return Text.literal(fullName).formatted(Formatting.WHITE)
                                .append(Text.literal(" - " + command.getShortDesc()).formatted(Formatting.GRAY))
                                .styled(style -> style
                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))
                                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, manager.getPrefix() + label + " " + command.getName())));
                    },
                    manager.getPrefix() + label
            );
            return;
        }

        Command command = manager.getCommand(args[0]);
        if (command == null) {
            logDirect("Command '" + args[0] + "' not found.", Formatting.RED);
            return;
        }
        logDirectRaw(Text.literal(getLine()));
        logDirect(command.getName().toUpperCase());
        logDirectRaw(Text.literal(getLine()));
        for (String line : command.getLongDesc()) {
            if (!line.isEmpty()) logDirect(line);
        }
        logDirectRaw(Text.literal(getLine()));
    }

    @Override
    public Stream<String> tabComplete(String label, String[] args) {
        if (args.length == 1) {
            return new TabCompleteHelper().filterPrefix(args[0]).addCommands(CommandManager.getInstance()).stream();
        }
        return Stream.empty();
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList("Shows command help.", "Usage:", "> help", "> help <command>");
    }

    @Override
    public boolean hiddenFromHelp() {
        return true;
    }

    private boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    public static String getLine() {
        MinecraftClient mc = MinecraftClient.getInstance();
        int dashCount = mc != null && mc.textRenderer != null
                ? Math.max(18, Math.min(80, mc.getWindow().getScaledWidth() / Math.max(4, mc.textRenderer.getWidth("-")) / 3))
                : 24;
        return "§8§m" + "-".repeat(dashCount);
    }
}
