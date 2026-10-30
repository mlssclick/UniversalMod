package universalmod.util;

import net.minecraft.util.math.MathHelper;
import universalmod.integration.minecraft.IMinecraft;

public final class Counter implements IMinecraft {
    private static int currentFPS;

    public static void updateFPS() {
        int prevFPS = mc.getCurrentFps();
        currentFPS = MathHelper.lerp(0.5F, prevFPS, currentFPS);
    }

    private Counter() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static int getCurrentFPS() {
        return currentFPS;
    }
}
