package universalmod.feature.server;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;

public final class ServerEnvironment {
    private static final MinecraftClient MC = MinecraftClient.getInstance();

    private ServerEnvironment() {
    }

    public static boolean isReallyWorld() {
        return safeLower(serverAddress()).contains("reallyworld");
    }

    public static boolean isSingleplayer() {
        return MC != null && MC.isInSingleplayer();
    }

    public static String serverAddress() {
        if (MC == null || MC.getNetworkHandler() == null || MC.getCurrentServerEntry() == null) {
            return "";
        }
        return safeTrim(MC.getCurrentServerEntry().address);
    }

    public static String serverBrand() {
        if (MC == null || MC.getNetworkHandler() == null || MC.getCurrentServerEntry() == null) {
            return "";
        }
        Object serverData = MC.getCurrentServerEntry();
        for (String methodName : new String[]{
                "getGameVersion", "gameVersion", "getBrand", "brand", "getVersion", "version"
        }
        ) {
            try {
                Method method = serverData.getClass().getMethod(methodName);
                Object value = method.invoke(serverData);
                if (value != null) {
                    return value.toString().trim();
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        for (String fieldName : new String[]{
                "gameVersion", "brand", "version"
        }
        ) {
            try {
                Field field = serverData.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                Object value = field.get(serverData);
                if (value != null) {
                    return value.toString().trim();
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return "";
    }

    public static String pingText(PlayerListEntry playerInfo) {
        int ping = ping(playerInfo);
        return ping < 0 ? "?ms" : ping + "ms";
    }

    public static int ping(PlayerListEntry playerInfo) {
        if (playerInfo == null) {
            return -1;
        }
        try {
            return Math.max(-1, playerInfo.getLatency());
        } catch (Throwable ignored) {
        }
        for (String methodName : new String[]{
                "getLatency", "latency", "getPing", "ping"
        }
        ) {
            try {
                Method method = playerInfo.getClass().getMethod(methodName);
                Object value = method.invoke(playerInfo);
                if (value instanceof Number number) {
                    return Math.max(-1, number.intValue());
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        for (String fieldName : new String[]{
                "latency", "ping"
        }
        ) {
            try {
                Field field = playerInfo.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(playerInfo);
                if (value instanceof Number number) {
                    return Math.max(-1, number.intValue());
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return -1;
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
