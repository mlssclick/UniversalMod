package universalmod.render.glass;

import net.minecraft.client.gui.DrawContext;

public record BuiltGlass(float x, float y, float width, float height, float radiusTopLeft, float radiusTopRight,
                         float radiusBottomRight, float radiusBottomLeft, int color, float globalAlpha,
                         float fresnelPower, int fresnelColor, float baseAlpha, boolean fresnelInvert, float fresnelMix,
                         float distortStrength, float squirt, float z) {
    public BuiltGlass(float x, float y, float w, float h, float[] r, int color, float ga, float fp, int fc, float ba, boolean fi, float fm, float ds, float squirt, float z) {
        this(x, y, w, h, rv(r, 0), rv(r, 1), rv(r, 2), rv(r, 3), color, ga, fp, fc, ba, fi, fm, ds, squirt, z);
    }

    public void render(DrawContext g) {
        GlassRenderer.getInstance().draw(g, this);
    }

    public boolean visible() {
        return width > 0 && height > 0 && globalAlpha > 0 && (baseAlpha > 0 || (fresnelColor >>> 24) != 0);
    }

    private static float rv(float[] r, int i) {
        return r == null || i < 0 || i >= r.length ? 0 : r[i];
    }
}
