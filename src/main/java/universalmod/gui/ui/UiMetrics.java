package universalmod.gui.ui;






public final class UiMetrics {
    public static final float GUI_WIDTH = 575.0f;
    public static final float GUI_HEIGHT = 372.0f;
    public static final float SIDE_WIDTH = 135.0f;
    public static final float HEADER_HEIGHT = 36.0f;
    public static final float BODY_HEIGHT = GUI_HEIGHT - HEADER_HEIGHT;
    public static final float MAIN_WIDTH = GUI_WIDTH - SIDE_WIDTH;
    public static final float DESIGN_SCALE = 0.546f;


    public static final float MAIN_PADDING = 5.5f;
    public static final float MODULE_LIST_WIDTH = 160.0f;
    public static final float PANE_GAP = 4.0f;
    public static final float SETTINGS_PANEL_WIDTH = MAIN_WIDTH - MAIN_PADDING * 2.0f - MODULE_LIST_WIDTH - PANE_GAP;
    public static final float MODULE_ROW_HEIGHT = 34.5f;
    public static final float MODULE_ROW_GAP = 3.5f;
    public static final float SETTINGS_HEADER_HEIGHT = 34.0f;
    public static final float SETTINGS_PADDING = 7.0f;

    public static final float SIDEBAR_SEARCH_X = 8.0f;
    public static final float SIDEBAR_SEARCH_Y = 8.0f;
    public static final float SIDEBAR_SEARCH_WIDTH = SIDE_WIDTH - 16.0f;
    public static final float SIDEBAR_SEARCH_HEIGHT = 22.0f;
    public static final float SIDEBAR_CATEGORY_TOP = 37.0f;
    public static final float SIDEBAR_CATEGORY_HEIGHT = 26.5f;
    public static final float SIDEBAR_CATEGORY_GAP = 3.0f;
    public static final float SIDEBAR_PROFILE_HEIGHT = 46.0f;


    public static final int MODULE_COLUMNS = 3;
    public static final float MODULE_PANEL_PADDING_X = 16.0f;
    public static final float MODULE_PANEL_PADDING_Y = 13.0f;
    public static final float MODULE_GAP_X = 14.0f;
    public static final float MODULE_GAP_Y = 8.5f;
    public static final float MODULE_WIDTH = (MAIN_WIDTH - MODULE_PANEL_PADDING_X * 2.0f - MODULE_GAP_X * (MODULE_COLUMNS - 1)) / MODULE_COLUMNS;
    public static final float MODULE_HEIGHT = 27.0f;
    public static final float MODULE_CARD_RADIUS = 6.0f;
    public static final float OPTION_GAP = 2.1f;
    public static final float SETTINGS_PADDING_X = 5.6f;
    public static final float SETTINGS_PADDING_Y = 4.9f;
    public static final float OPTION_SCALE = 0.70f;

    public static final float SEARCH_WIDTH = SIDEBAR_SEARCH_WIDTH;
    public static final float SEARCH_HEIGHT = SIDEBAR_SEARCH_HEIGHT;
    public static final float SEARCH_RESULTS_WIDTH = SEARCH_WIDTH;
    public static final float SEARCH_RESULTS_ROW_HEIGHT = 25.0f;
    public static final float SEARCH_RESULTS_GAP = 4.0f;
    public static final int SEARCH_RESULTS_LIMIT = 6;
    public static final long SEARCH_HIGHLIGHT_MS = 1000L;

    public static final float CONFIGS_TOOLBAR_HEIGHT = 23.0f;
    public static final float CONFIGS_TOOLBAR_TOP = 1.0f;
    public static final int CONFIG_CARD_COLUMNS = MODULE_COLUMNS;
    public static final float CONFIG_CARD_WIDTH = MODULE_WIDTH;
    public static final float CONFIG_CARD_HEIGHT = MODULE_HEIGHT * 1.3f;
    public static final float CONFIG_CARD_GAP_X = MODULE_GAP_X;
    public static final float CONFIG_CARD_GAP_Y = MODULE_GAP_Y;
    public static final float CONFIG_CARD_RADIUS = 6.0f;
    public static final float CONFIG_ACTION_SIZE = 14.0f;
    public static final float CONFIG_ACTION_GAP = 3.0f;
    public static final float CONFIG_RENAME_SIZE = 9.0f;
    public static final float CONFIG_RENAME_GAP = 2.0f;
    public static final float CONFIG_CARD_SIDE_PADDING = 7.0f;
    public static final float SCROLL_STEP = 27.0f;

    public static final float PANEL_RADIUS = 8.0f;
    public static final float PANEL_BLUR_RADIUS = 9.0f;
    public static final float PANEL_OUTLINE = 0.45f;
    public static final float SEARCH_RADIUS = 5.0f;
    public static final float SEARCH_BLUR_RADIUS = 3.8f;
    public static final float SEARCH_RESULTS_BLUR_RADIUS = 5.2f;
    public static final String SIDEBAR_AVATAR = "clickgui/avatar.png";

    private UiMetrics() {
    }
}
