package universalmod.module.misc.serverhelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

public final class ServerInfoService {
    private static final Minecraft MC = Minecraft.getInstance();

    private ServerInfoService() {
    }

    public static boolean isReallyWorld() {
        String address = serverAddress();
        return !address.isBlank() && address.toLowerCase(Locale.ROOT).contains("reallyworld");
    }

    public static boolean isSingleplayer() {
        return MC.isLocalServer();
    }

    public static String serverAddress() {
        if (MC.getConnection() == null || MC.getConnection().getServerData() == null) {
            return "";
        }
        return safeTrim(MC.getConnection().getServerData().ip);
    }

    public static String serverBrand() {
        if (MC.getConnection() == null || MC.getConnection().getServerData() == null) {
            return "";
        }

        Object serverData = MC.getConnection().getServerData();
        Throwable lastFailure = null;
        for (String methodName : new String[]{"getGameVersion", "gameVersion", "getBrand", "brand"}) {
            try {
                Method method = serverData.getClass().getMethod(methodName);
                Object value = method.invoke(serverData);
                if (value != null) {
                    return value.toString().trim();
                }
            } catch (ReflectiveOperationException failure) {
                lastFailure = failure;
            }
        }

        for (String fieldName : new String[]{"gameVersion", "brand"}) {
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
            } catch (ReflectiveOperationException failure) {
                lastFailure = failure;
            }
        }

        if (lastFailure != null) {
        }
        return "";
    }

    public static String pingText(PlayerInfo playerInfo) {
        int ping = ping(playerInfo);
        return ping < 0 ? "?ms" : ping + "ms";
    }

    public static int ping(PlayerInfo playerInfo) {
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
