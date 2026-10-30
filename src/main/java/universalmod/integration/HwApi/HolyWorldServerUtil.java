package universalmod.integration.HwApi;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ServerInfo;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Locale;


final class HolyWorldServerUtil {
    private static volatile String cachedAddressInput = "";
    private static volatile String cachedNormalizedHost = "";

    private HolyWorldServerUtil() {
    }

    static String getCurrentServerHost() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.isInSingleplayer()) {
            return "";
        }

        ServerInfo current = client.getCurrentServerEntry();
        if (current != null && current.address != null) {
            return stripPort(current.address);
        }

        ClientPlayNetworkHandler connection = client.getNetworkHandler();
        if (connection == null) {
            return "";
        }

        ServerInfo serverData = connection.getServerInfo();
        if (serverData != null && serverData.address != null) {
            return stripPort(serverData.address);
        }

        SocketAddress remote = connection.getConnection().getAddress();
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
