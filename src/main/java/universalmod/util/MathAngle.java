package universalmod.util;

import net.minecraft.client.MinecraftClient;

public final class MathAngle {
    private MathAngle() {
    }

    public static Angle cameraAngle() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return Angle.DEFAULT;
        }
        return new Angle(client.player.getYaw(), client.player.getPitch());
    }
}
