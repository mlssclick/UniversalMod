package universalmod.render.glass;

import net.minecraft.client.gui.DrawContext;

public record BuiltGlassOutline(float x, float y, float width, float height, float radiusTopLeft, float radiusTopRight,
                                float radiusBottomRight, float radiusBottomLeft, float thickness, int color,
                                float globalAlpha, float fresnelPower, int fresnelColor, float baseAlpha,
                                boolean fresnelInvert, float fresnelMix, float distortStrength, float squirt,
                                float smoothness, float z, float shinePhase) {
    public static final float DEFAULT_SMOOTHNESS = 1f;

    public BuiltGlassOutline(float x, float y, float w, float h, float[] r, float thickness, int color, float ga, float fp, int fc, float ba, boolean fi, float fm, float ds, float squirt, float z) {
        this(x, y, w, h, rv(r, 0), rv(r, 1), rv(r, 2), rv(r, 3), thickness, color, ga, fp, fc, ba, fi, fm, ds, squirt, DEFAULT_SMOOTHNESS, z, squirt);
    }

    public BuiltGlassOutline(float x, float y, float width, float height,
                             float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
                             float thickness, int color, float globalAlpha, float fresnelPower,
                             int fresnelColor, float baseAlpha, boolean fresnelInvert, float fresnelMix,
                             float distortStrength, float squirt, float smoothness, float z) {
        this(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
             thickness, color, globalAlpha, fresnelPower, fresnelColor, baseAlpha, fresnelInvert,
             fresnelMix, distortStrength, squirt, smoothness, z, squirt);
    }

    public void render(DrawContext g) {
        GlassOutlineRenderer.getInstance().draw(g, this);
    }

    public boolean visible() {
        return width > 0 && height > 0 && thickness > 0 && globalAlpha > 0 && ((color >>> 24) != 0 || baseAlpha > 0 || (fresnelColor >>> 24) != 0);
    }

    private static float rv(float[] r, int i) {
        return r == null || i < 0 || i >= r.length ? 0 : r[i];
    }
}
