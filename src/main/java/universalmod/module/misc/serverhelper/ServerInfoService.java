package universalmod.module.misc.serverhelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

public final class ServerInfoService {
    private static final MinecraftClient MC = MinecraftClient.getInstance();

    private ServerInfoService() {
    }

    public static boolean isReallyWorld() {
        String address = serverAddress();
        return !address.isBlank() && address.toLowerCase(Locale.ROOT).contains("reallyworld");
    }

    public static boolean isSingleplayer() {
        return MC.isInSingleplayer();
    }

    public static String serverAddress() {
        if (MC.getNetworkHandler() == null || MC.getNetworkHandler().getServerInfo() == null) {
            return "";
        }
        return safeTrim(MC.getNetworkHandler().getServerInfo().address);
    }

    public static String serverBrand() {
        return MC.getNetworkHandler() == null ? "" : safeTrim(MC.getNetworkHandler().getBrand());
    }

    public static String pingText(PlayerListEntry playerInfo) {
        int ping = ping(playerInfo);
        return ping < 0 ? "?ms" : ping + "ms";
    }

    public static int ping(PlayerListEntry playerInfo) {
        if (playerInfo == null) {
            return -1;
        }

        Throwable lastFailure = null;
        try {
            return Math.max(-1, playerInfo.getLatency());
        } catch (RuntimeException | LinkageError failure) {
            
            lastFailure = failure;
        }

        for (String methodName : new String[]{"getLatency", "latency", "getPing", "ping"}) {
            try {
                Method method = playerInfo.getClass().getMethod(methodName);
                Object value = method.invoke(playerInfo);
                if (value instanceof Number number) {
                    return Math.max(-1, number.intValue());
                }
            } catch (ReflectiveOperationException failure) {
                lastFailure = failure;
            }
        }

        for (String fieldName : new String[]{"latency", "ping"}) {
            try {
                Field field = playerInfo.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(playerInfo);
                if (value instanceof Number number) {
                    return Math.max(-1, number.intValue());
                }
            } catch (ReflectiveOperationException failure) {
                lastFailure = failure;
            }
        }

        if (lastFailure != null) {
        }
        return -1;
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
