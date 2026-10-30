package universalmod.render.blur;


final class BlurQualityPolicy {
    private static final float MAX_RENDER_SCALE = 0.5f;
    private static final float MEDIUM_RENDER_SCALE = 0.44f;
    private static final float MIN_RENDER_SCALE = 0.4f;

    private BlurQualityPolicy() {
    }

    static int iterations(float blurRadius) {
        if (blurRadius >= 52.0f) {
            return 4;
        }
        if (blurRadius >= 36.0f) {
            return 3;
        }
        return 2;
    }

    static float renderScale(float blurRadius, int width, int height) {
        long area = (long) Math.max(width, 1) * Math.max(height, 1);
        if (blurRadius >= 30.0f || area >= 220_000L) {
            return MIN_RENDER_SCALE;
        }
        if (blurRadius >= 18.0f || area >= 120_000L) {
            return MEDIUM_RENDER_SCALE;
        }
        return MAX_RENDER_SCALE;
    }
}
