package universalmod.render.smooth;

public record SmoothRoundedShape(
        float x,
        float y,
        float width,
        float height,
        float radius,
        int color
) {
    public boolean visible() {
        return width > 0.0F && height > 0.0F && ((color >>> 24) & 0xFF) != 0;
    }
}
