package universalmod.feature.server;

import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import universalmod.integration.minecraft.IMinecraft;

public final class Server implements IMinecraft {
    private Server() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static int getPing(PlayerEntity entity) {
        if (mc.getNetworkHandler() == null || entity == null) {
            return 0;
        }

        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(entity.getUuid());
        return entry == null ? 0 : entry.getLatency();
    }

    public static float getHealth(LivingEntity entity, boolean includeAbsorption) {
        if (entity == null) {
            return 0.0f;
        }

        return entity.getHealth() + (includeAbsorption ? entity.getAbsorptionAmount() : 0.0f);
    }
}
