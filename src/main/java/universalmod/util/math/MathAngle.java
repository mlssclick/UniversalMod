package universalmod.util.math;

import net.minecraft.client.MinecraftClient;

public final class MathAngle {
    private MathAngle() {
    }

    public static Angle cameraAngle() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client == null || client.player == null ? Angle.DEFAULT : new Angle(client.player.getYaw(), client.player.getPitch());
    }
}
