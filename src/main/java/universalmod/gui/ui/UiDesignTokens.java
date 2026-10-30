package universalmod.gui.ui;

public final class UiDesignTokens {
    public static final class Spacing {
        public static final float HAIRLINE = 0.45F;
        public static final float XS = 2.0F;
        public static final float SM = 4.0F;
        public static final float MD = 7.0F;
        public static final float LG = 12.0F;
        public static final float XL = 16.0F;

        public static final float PANEL_INSET = UiMetrics.MAIN_PADDING;
        public static final float SETTINGS_INSET = UiMetrics.SETTINGS_PADDING;
        public static final float ROW_GAP = UiMetrics.MODULE_ROW_GAP;

        private Spacing() {}
    }

    public static final class Radius {
        public static final float PANEL = UiMetrics.PANEL_RADIUS;
        public static final float CARD = UiMetrics.MODULE_CARD_RADIUS;
        public static final float CONTROL = UiMetrics.SEARCH_RADIUS;
        public static final float PILL = 999.0F;

        private Radius() {}
    }

    public static final class Motion {
        public static final double POPUP_SECONDS = 0.22;
        public static final double HOVER_SECONDS = 0.16;
        public static final double CONTROL_SECONDS = 0.20;
        public static final double SCROLL_SECONDS = 0.18;
        public static final float POPUP_START_SCALE = 0.92F;
        public static final float POPUP_START_Y_OFFSET = -6.0F;

        private Motion() {}
    }

    public static final class Typography {
        public static final float CAPTION = 8.0F;
        public static final float BODY = 10.0F;
        public static final float BODY_STRONG = 10.0F;
        public static final float SECTION_TITLE = 11.0F;
        public static final float PANEL_TITLE = 12.0F;

        private Typography() {}
    }

    public static final class Opacity {
        public static final float DISABLED = 0.45F;
        public static final float MUTED = 0.72F;
        public static final float SUBTLE = 0.24F;

        private Opacity() {}
    }

    private UiDesignTokens() {
    }
}
