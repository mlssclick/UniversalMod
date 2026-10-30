package universalmod.theme;


public final class ThemeRender {
    private ThemeRender() {
    }

    public static float defaultAlpha(float alpha) {
        return clamp01(alpha);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
