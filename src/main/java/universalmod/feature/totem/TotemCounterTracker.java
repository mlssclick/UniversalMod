package universalmod.feature.totem;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

public final class TotemCounterTracker {
    private final Map<UUID, Integer> pops = new HashMap<>();

    public void recordPop(PlayerEntity player) {
        if (player != null) {
            pops.merge(player.getUuid(), 1, Integer::sum);
        }
    }

    public int getPops(PlayerEntity player) {
        return player == null ? 0 : pops.getOrDefault(player.getUuid(), 0);
    }

    public boolean hasPops(PlayerEntity player) {
        return player != null && pops.containsKey(player.getUuid());
    }

    public void remove(PlayerEntity player) {
        if (player != null) {
            pops.remove(player.getUuid());
        }
    }

    public void clearInvalid(MinecraftClient client) {
        if (client == null || client.world == null) {
            pops.clear();
            return;
        }

        Set<UUID> alivePlayers = new HashSet<>();
        for (PlayerEntity player : client.world.getPlayers()) {
            if (player != null && player.isAlive() && !player.isRemoved()) {
                alivePlayers.add(player.getUuid());
            }
        }

        Iterator<UUID> iterator = pops.keySet().iterator();
        while (iterator.hasNext()) {
            if (!alivePlayers.contains(iterator.next())) {
                iterator.remove();
            }
        }
    }

    public void clear() {
        pops.clear();
    }
}
