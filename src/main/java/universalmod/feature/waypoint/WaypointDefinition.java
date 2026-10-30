package universalmod.feature.waypoint;

import net.minecraft.util.math.Vec3d;

import java.util.Objects;
import java.util.UUID;

public final class WaypointDefinition {
    public enum Source {MANUAL, DEATH, AUTO_EVENT, AUTO_TREASURE}

    private final UUID id;
    private final String name;
    private final double x, y, z;
    private final String dimension, serverKey;
    private final Source source;
    private final long createdAtMillis;

    public WaypointDefinition(UUID id, String name, double x, double y, double z, String dimension, String serverKey, Source source, long createdAtMillis) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.source = source == null ? Source.MANUAL : source;
        this.name = sanitizeName(name, this.source);
        this.x = sanitizeCoordinate(x);
        this.y = sanitizeCoordinate(y);
        this.z = sanitizeCoordinate(z);
        this.dimension = sanitizeDimension(dimension);
        this.serverKey = serverKey == null ? "" : serverKey.trim().toLowerCase();
        this.createdAtMillis = Math.max(0L, createdAtMillis);
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public String dimension() {
        return dimension;
    }

    public String serverKey() {
        return serverKey;
    }

    public Source source() {
        return source;
    }

    public long createdAtMillis() {
        return createdAtMillis;
    }

    public boolean manual() {
        return source == Source.MANUAL;
    }

    public Vec3d anchorPosition() {
        return new Vec3d(x + .5d, y + 1.15d, z + .5d);
    }

    public WaypointDefinition copy() {
        return new WaypointDefinition(id, name, x, y, z, dimension, serverKey, source, createdAtMillis);
    }

    public boolean sameLocationScope(WaypointDefinition other) {
        return other != null && Objects.equals(dimension, other.dimension) && Objects.equals(serverKey, other.serverKey) && source == other.source;
    }

    public static String sanitizeDimension(String value) {
        String s = value == null ? "" : value.trim().toLowerCase();
        return s.isEmpty() ? "minecraft:overworld" : s;
    }

    private static String sanitizeName(String value, Source source) {
        String s = value == null ? "" : value.trim();
        if (!s.isEmpty()) return s;
        return switch (source == null ? Source.MANUAL : source) {
            case AUTO_TREASURE -> "[auto] Сокровище";
            case AUTO_EVENT -> "[auto] Event";
            case DEATH -> "Death";
            case MANUAL -> "Waypoint";
        };
    }

    private static double sanitizeCoordinate(double v) {
        return Double.isFinite(v) && Math.abs(v) <= 30_000_000d ? v : 0d;
    }
}
