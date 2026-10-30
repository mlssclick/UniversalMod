package universalmod.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;

public final class Render2DCoordinateSpace {
    private static final float DESIGN_GUI_SCALE = 2.0f;

    private Render2DCoordinateSpace() {
    }

    public static float designGuiScale() {
        return DESIGN_GUI_SCALE;
    }

    public static int guiScale() {
        return Math.max(1, Math.round(actualGuiScale()));
    }

    public static float actualGuiScale() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc == null || mc.getWindow() == null ? 1.0F : Math.max(1.0F, (float) mc.getWindow().getScaleFactor());
    }

    public static float guiIndependentScale() {
        return DESIGN_GUI_SCALE / actualGuiScale();
    }

    public static void applyGuiScaleIndependence(MatrixStack matrices) {
        float s = guiIndependentScale();
        if (matrices != null && Math.abs(s - 1.0f) > 0.0001f) matrices.scale(s, s, 1.0f);
    }

    public static float toGui(float value) {
        return value * guiIndependentScale();
    }

    public static int toGuiInt(float value) {
        return Math.round(toGui(value));
    }
}
