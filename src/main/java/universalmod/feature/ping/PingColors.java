package universalmod.feature.ping;

public final class PingColors {
    private static final int GREY = 0x535353, GREEN = 0x00E676, YELLOW = 0xD6CD30, RED = 0xE53935;

    private PingColors() {
    }

    public static int getColor(int ping) {
        if (ping < 0) return GREY;
        if (ping <= 150) return blend(GREEN, YELLOW, ping / 150f);
        if (ping <= 300) return blend(YELLOW, RED, (ping - 150) / 150f);
        return RED;
    }

    private static int blend(int a, int b, float p) {
        p = Math.max(0, Math.min(1, p));
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return Math.round(ar + (br - ar) * p) << 16 | Math.round(ag + (bg - ag) * p) << 8 | Math.round(ab + (bb - ab) * p);
    }
}
