package universalmod.command;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import universalmod.command.Command;
import universalmod.feature.waypoint.WaypointDefinition;
import universalmod.feature.waypoint.WaypointManager;

import java.util.Arrays;
import java.util.stream.Stream;

public final class GpsCommand extends Command {
    public GpsCommand() {
        super("gps", "Adds and manages waypoints.", "waypoint", "waypoints");
    }

    @Override
    public void execute(String label, String[] args) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            usage();
            return;
        }

        switch (args[0].toLowerCase()) {
            case "add" -> add(args);
            case "list" -> list();
            case "remove", "delete", "del" -> remove(args);
            case "clear" -> clear(args);
            default -> usage();
        }
    }

    @Override
    public Stream<String> tabComplete(String label, String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase();
            return Stream.of("add", "list", "remove", "clear").filter(value -> value.startsWith(prefix));
        }
        return Stream.empty();
    }

    private void add(String[] args) {
        if (args.length < 4) {
            logDirect("Usage: .gps add <x> <y> <z> [name]", Formatting.RED);
            return;
        }
        Double x = parseCoordinate(args[1]);
        Double y = parseCoordinate(args[2]);
        Double z = parseCoordinate(args[3]);
        if (x == null || y == null || z == null) {
            logDirect("Coordinates must be numbers.", Formatting.RED);
            return;
        }
        String name = args.length > 4 ? String.join(" ", Arrays.copyOfRange(args, 4, args.length)) : "Waypoint";
        WaypointDefinition waypoint = WaypointManager.getInstance().addManual(x, y, z, name, MinecraftClient.getInstance());
        logDirect(Text.literal("Waypoint added: ").formatted(Formatting.GREEN).append(WaypointManager.formatWaypoint(waypoint)));
    }

    private void list() {
        var waypoints = WaypointManager.getInstance().manualWaypoints();
        if (waypoints.isEmpty()) {
            logDirect("No waypoints.", Formatting.RED);
            return;
        }
        logDirect("Waypoints:", Formatting.GREEN);
        for (WaypointDefinition waypoint : waypoints) {
            logDirect(Text.literal("- ").formatted(Formatting.DARK_GRAY).append(WaypointManager.formatWaypoint(waypoint)));
        }
    }

    private void remove(String[] args) {
        if (args.length < 2) {
            logDirect("Usage: .gps remove <name|uuid>", Formatting.RED);
            return;
        }
        int removed = WaypointManager.getInstance().removeManual(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        if (removed == 0) logDirect("Waypoint not found.", Formatting.RED);
        else logDirect("Removed " + removed + " waypoint(s).", Formatting.GREEN);
    }

    private void clear(String[] args) {
        if (args.length > 1 && "auto".equalsIgnoreCase(args[1])) {
            int removed = WaypointManager.getInstance().clearAutomatic();
            logDirect("Cleared " + removed + " auto waypoint(s).", Formatting.GREEN);
            return;
        }
        int removed = WaypointManager.getInstance().clearManual();
        logDirect("Cleared " + removed + " waypoint(s).", Formatting.GREEN);
    }

    private void usage() {
        logDirect("Usage: .gps add <x> <y> <z> [name]");
        logDirect("Also: .gps list, .gps remove <name|uuid>, .gps clear, .gps clear auto");
    }

    private static Double parseCoordinate(String value) {
        try {
            double parsed = Double.parseDouble(value.replace(',', '.'));
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
