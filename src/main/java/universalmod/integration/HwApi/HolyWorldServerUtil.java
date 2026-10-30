package universalmod.integration.HwApi;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Locale;


final class HolyWorldServerUtil {
    private static volatile String cachedAddressInput = "";
    private static volatile String cachedNormalizedHost = "";

    private HolyWorldServerUtil() {
    }

    static String getCurrentServerHost() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.hasSingleplayerServer()) {
            return "";
        }

        ServerData current = client.getCurrentServer();
        if (current != null && current.ip != null) {
            return stripPort(current.ip);
        }

        ClientPacketListener connection = client.getConnection();
        if (connection == null) {
            return "";
        }

        ServerData serverData = connection.getServerData();
        if (serverData != null && serverData.ip != null) {
            return stripPort(serverData.ip);
        }

        SocketAddress remote = connection.getConnection().getRemoteAddress();
        if (remote instanceof InetSocketAddress inet && inet.getHostString() != null) {
            return stripPort(inet.getHostString());
        }
        return "";
    }

    static boolean hasServerSegment(String segment) {
        String host = getCurrentServerHost();
        return !host.isEmpty() && containsHostSegment(host, segment);
    }

    private static String stripPort(String address) {
        if (address == null) {
            return "";
        }
        if (address.equals(cachedAddressInput)) {
            return cachedNormalizedHost;
        }
        String normalized = address.toLowerCase(Locale.ROOT).trim();
        int colon = normalized.indexOf(':');
        String host = colon >= 0 ? normalized.substring(0, colon) : normalized;
        cachedNormalizedHost = host;
        cachedAddressInput = address;
        return host;
    }

    private static boolean containsHostSegment(String host, String segment) {
        int start = 0;
        while (start <= host.length()) {
            int dot = host.indexOf('.', start);
            if (dot < 0) {
                dot = host.length();
            }
            int length = dot - start;
            if (length == segment.length() && host.regionMatches(start, segment, 0, length)) {
                return true;
            }
            if (dot == host.length()) {
                return false;
            }
            start = dot + 1;
        }
        return false;
    }
}
