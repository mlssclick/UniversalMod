package universalmod.feature.ping;

public final class PingColors {
    private static final int GREY = 0x535353, GREEN = 0x00E676, YELLOW = 0xD6CD30, RED = 0xE53935;

    private PingColors() {
    }

    public static int getColor(int ping) {
        if (ping < 0) return GREY;
        if (ping <= 150) return blend(GREEN, YELLOW, ping / 150.0f);
        if (ping <= 300) return blend(YELLOW, RED, (ping - 150) / 150.0f);
        return RED;
    }

    private static int blend(int start, int end, float progress) {
        float p = Math.max(0f, Math.min(1f, progress));
        int sr = (start >> 16) & 255, sg = (start >> 8) & 255, sb = start & 255;
        int er = (end >> 16) & 255, eg = (end >> 8) & 255, eb = end & 255;
        int r = Math.round(sr + (er - sr) * p), g = Math.round(sg + (eg - sg) * p), b = Math.round(sb + (eb - sb) * p);
        return (r << 16) | (g << 8) | b;
    }
}
