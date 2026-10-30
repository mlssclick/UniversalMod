package universalmod.command;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import universalmod.command.Command;
import universalmod.command.CommandManager;
import universalmod.command.Paginator;
import universalmod.command.TabCompleteHelper;
import universalmod.feature.friend.Friend;
import universalmod.feature.friend.FriendRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class FriendCommand extends Command {
    public FriendCommand() {
        super("friend", "Manage friends", "f", "friends");
    }

    @Override
    public void execute(String label, String[] args) {
        CommandManager manager = CommandManager.getInstance();
        String action = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "list";
        switch (action) {
            case "add" -> {
                if (args.length < 2) {
                    logDirect("Usage: friend add <name>", Formatting.RED);
                    return;
                }
                if (FriendRepository.isFriend(args[1])) {
                    logDirect(args[1] + " is already friend.", Formatting.RED);
                    return;
                }
                FriendRepository.addFriend(args[1]);
                FriendRepository.save();
                logDirect(args[1] + " added to friends.", Formatting.GREEN);
            }
            case "remove", "del", "delete" -> {
                if (args.length < 2) {
                    logDirect("Usage: friend remove <name>", Formatting.RED);
                    return;
                }
                if (!FriendRepository.isFriend(args[1])) {
                    logDirect("Friend not found: " + args[1], Formatting.RED);
                    return;
                }
                FriendRepository.removeFriend(args[1]);
                FriendRepository.save();
                logDirect(args[1] + " removed from friends.", Formatting.GREEN);
            }
            case "clear" -> {
                int count = FriendRepository.getFriends().size();
                FriendRepository.clear();
                FriendRepository.save();
                logDirect("Friend list cleared. Removed: " + count, Formatting.GREEN);
            }
            case "list" -> {
                List<String> friends = FriendRepository.getFriends().stream().map(Friend::name).toList();
                if (friends.isEmpty()) {
                    logDirect("Friend list is empty.", Formatting.RED);
                    return;
                }
                int page = args.length > 1 ? parsePage(args[1]) : 1;
                Paginator<String> paginator = new Paginator<>(friends);
                paginator.setPage(page);
                paginator.display(
                        () -> {
                            logDirectRaw(Text.literal(HelpCommand.getLine()));
                            logDirect("Friends (" + friends.size() + ")");
                        },
                        friend -> Text.literal("  §a● §f" + friend).styled(style -> style
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to remove " + friend)))
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, manager.getPrefix() + "friend remove " + friend))),
                        manager.getPrefix() + label + " list"
                );
            }
            default -> logDirect("Usage: friend add/remove/list/clear");
        }
    }

    @Override
    public Stream<String> tabComplete(String label, String[] args) {
        if (args.length == 1) {
            return new TabCompleteHelper().append("add", "remove", "list", "clear").sortAlphabetically().filterPrefix(args[0]).stream();
        }
        if (args.length == 2) {
            String action = args[0].toLowerCase(Locale.ROOT);
            if (action.equals("add")) {
                return new TabCompleteHelper().append(getOnlinePlayers().toArray(new String[0])).filterPrefix(args[1]).stream();
            }
            if (action.equals("remove") || action.equals("del") || action.equals("delete")) {
                return new TabCompleteHelper()
                        .append(FriendRepository.getFriends().stream().map(Friend::name).toArray(String[]::new))
                        .filterPrefix(args[1])
                        .stream();
            }
        }
        return Stream.empty();
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList("Manages friend list.", "Usage:", "> friend add <name>", "> friend remove <name>", "> friend list", "> friend clear");
    }

    private int parsePage(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private List<String> getOnlinePlayers() {
        List<String> players = new ArrayList<>();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() != null) {
            for (PlayerListEntry info : mc.getNetworkHandler().getPlayerList()) {
                String name = info.getProfile().getName();
                if (!FriendRepository.isFriend(name)) players.add(name);
            }
        }
        return players;
    }
}
