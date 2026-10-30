package universalmod.command;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import universalmod.command.Command;
import universalmod.feature.waypoint.*;

import java.util.*;
import java.util.stream.Stream;

public final class GpsCommand extends Command {
    public GpsCommand() {
        super("gps", "Adds and manages waypoints.", "waypoint", "waypoints");
    }

    public void execute(String label, String[] args) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            usage();
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "add" -> add(args);
            case "list" -> list();
            case "remove", "delete", "del" -> remove(args);
            case "clear" -> clear(args);
            default -> usage();
        }
    }

    public Stream<String> tabComplete(String label, String[] args) {
        if (args.length <= 1) {
            String p = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return Stream.of("add", "list", "remove", "clear").filter(v -> v.startsWith(p));
        }
        return Stream.empty();
    }

    private void add(String[] a) {
        if (a.length < 4) {
            logDirect("Usage: .gps add <x> <y> <z> [name]", Formatting.RED);
            return;
        }
        Double x = parse(a[1]), y = parse(a[2]), z = parse(a[3]);
        if (x == null || y == null || z == null) {
            logDirect("Coordinates must be numbers.", Formatting.RED);
            return;
        }
        String n = a.length > 4 ? String.join(" ", Arrays.copyOfRange(a, 4, a.length)) : "Waypoint";
        WaypointDefinition w = WaypointManager.getInstance().addManual(x, y, z, n, MinecraftClient.getInstance());
        logDirect(Text.literal("Waypoint added: ").formatted(Formatting.GREEN).append(WaypointManager.formatWaypoint(w)));
    }

    private void list() {
        var list = WaypointManager.getInstance().manualWaypoints();
        if (list.isEmpty()) {
            logDirect("No waypoints.", Formatting.RED);
            return;
        }
        logDirect("Waypoints:", Formatting.GREEN);
        for (WaypointDefinition w : list)
            logDirect(Text.literal("- ").formatted(Formatting.DARK_GRAY).append(WaypointManager.formatWaypoint(w)));
    }

    private void remove(String[] a) {
        if (a.length < 2) {
            logDirect("Usage: .gps remove <name|uuid>", Formatting.RED);
            return;
        }
        int n = WaypointManager.getInstance().removeManual(String.join(" ", Arrays.copyOfRange(a, 1, a.length)));
        logDirect(n == 0 ? "Waypoint not found." : "Removed " + n + " waypoint(s).", n == 0 ? Formatting.RED : Formatting.GREEN);
    }

    private void clear(String[] a) {
        boolean auto = a.length > 1 && "auto".equalsIgnoreCase(a[1]);
        int n = auto ? WaypointManager.getInstance().clearAutomatic() : WaypointManager.getInstance().clearManual();
        logDirect("Cleared " + n + (auto ? " auto waypoint(s)." : " waypoint(s)."), Formatting.GREEN);
    }

    private void usage() {
        logDirect("Usage: .gps add <x> <y> <z> [name]");
        logDirect("Also: .gps list, .gps remove <name|uuid>, .gps clear, .gps clear auto");
    }

    private static Double parse(String s) {
        try {
            double d = Double.parseDouble(s.replace(',', '.'));
            return Double.isFinite(d) ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
