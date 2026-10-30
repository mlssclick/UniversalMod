package universalmod.render.animation;

public final class Easings {
    public static final Easing LINEAR = Easing.LINEAR;
    public static final Easing QUAD_OUT = Easing.QUAD_OUT;
    public static final Easing CUBIC_OUT = Easing.CUBIC_OUT;
    public static final Easing CUBIC_IN = Easing.CUBIC_IN;
    public static final Easing CUBIC_IN_OUT = Easing.CUBIC_IN_OUT;
    public static final Easing QUART_OUT = Easing.QUARTIC_OUT;
    public static final Easing QUINTIC_OUT = Easing.QUINTIC_OUT;
    public static final Easing EXPO_IN = Easing.EXPO_IN;
    public static final Easing EXPO_OUT = Easing.EXPO_OUT;
    public static final Easing EXPO_IN_OUT = Easing.EXPO_IN_OUT;
    public static final Easing CIRC_IN = Easing.CIRC_IN;
    public static final Easing SINE_OUT = Easing.SINE_OUT;
    public static final Easing BACK_OUT = Easing.BACK_OUT;
    public static final Easing FIGMA_EASE_IN_OUT = Easing.CUBIC_IN_OUT;
    public static final Easing BAKEK = Easing.BACK_OUT;
    public static final Easing POW_17 = (time, start, change, duration) -> {
        float progress = duration <= 0 ? 1 : Math.max(0, Math.min(1, time / duration));
        return start + change * (float) (1 - Math.pow(1 - progress, 1.7));
    };
    private static final double DECAY_RATE = 6.0;
    public static final Easing DECAY_OUT = (time, start, change, duration) -> {
        float progress = duration <= 0 ? 1 : Math.max(0, Math.min(1, time / duration));
        return start + change * (float) (-Math.expm1(-DECAY_RATE * progress) / -Math.expm1(-DECAY_RATE));
    };

    private Easings() {
    }
}
