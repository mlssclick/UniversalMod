package universalmod.render.smooth;

public final class SmoothCircleRenderer {
    private SmoothCircleRenderer() {
    }

    public static void fill(float centerX, float centerY, float diameter, int color) {
        if (diameter <= 0.0F || ((color >>> 24) & 0xFF) == 0) {
            return;
        }
        float half = diameter * 0.5F;
        SmoothRoundedRenderer.getInstance().enqueue(
                new SmoothRoundedShape(centerX - half, centerY - half, diameter, diameter, half, color)
        );
    }

    public static void layered(float centerX, float centerY,
                               float outerDiameter, int outerColor,
                               float innerDiameter, int innerColor) {
        fill(centerX, centerY, outerDiameter, outerColor);
        fill(centerX, centerY, innerDiameter, innerColor);
    }
}
