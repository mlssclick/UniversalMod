package universalmod.module.render.ping;


import universalmod.feature.ping.PingColors;

import java.util.Collection;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import universalmod.module.render.ping.PingNametags;

public final class PingNametagHelper {
    private PingNametagHelper() {
    }

    public static Text appendPing(PlayerEntity player, Text original) {
        if (!PingNametags.isActive() || original == null) return original;
        int ping = resolvePing(player, original, MinecraftClient.getInstance());
        if (ping < 0) return original;
        String suffix = " (" + ping + "ms)";
        if (original.getString().endsWith(suffix)) return original;
        MutableText combined = original.copy();
        combined.append(Text.literal(" "));
        combined.append(Text.literal("(" + ping + "ms)").setStyle(net.minecraft.text.Style.EMPTY.withColor(PingColors.getColor(ping))));
        return combined;
    }

    public static int resolvePing(PlayerEntity player, Text renderedName, MinecraftClient client) {
        if (player == null || client == null) return -1;
        ClientPlayNetworkHandler connection = client.getNetworkHandler();
        if (connection == null) return -1;
        PlayerListEntry info = connection.getPlayerListEntry(player.getUuid());
        if (info == null) info = findMatching(connection, player, renderedName);
        return info == null ? -1 : universalmod.feature.server.ServerEnvironment.ping(info);
    }

    private static PlayerListEntry findMatching(ClientPlayNetworkHandler connection, PlayerEntity player, Text renderedName) {
        String playerName = player.getGameProfile() == null ? "" : player.getGameProfile().getName();
        String rendered = renderedName == null ? "" : renderedName.getString();
        Collection<PlayerListEntry> entries = connection.getPlayerList();
        for (PlayerListEntry entry : entries) {
            if (entry == null || entry.getProfile() == null) continue;
            if (!playerName.isEmpty() && playerName.equals(entry.getProfile().getName())) return entry;
            Text display = entry.getDisplayName();
            if (display != null && !rendered.isBlank() && rendered.equals(display.getString())) return entry;
        }
        return null;
    }
}
