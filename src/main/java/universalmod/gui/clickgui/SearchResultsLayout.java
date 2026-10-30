package universalmod.gui.clickgui;

import universalmod.gui.ui.control.Bounds;


final class SearchResultsLayout {
    private final Bounds panel;
    private final float rowHeight;
    private final float gap;
    private final int count;

    private SearchResultsLayout(Bounds panel, float rowHeight, float gap, int count) {
        this.panel = panel;
        this.rowHeight = rowHeight;
        this.gap = gap;
        this.count = count;
    }

    static SearchResultsLayout create(float searchX, float searchY, float searchWidth, float searchHeight,
                                      float scale, int count) {
        int safeCount = Math.max(0, count);
        float safeScale = Math.max(0.0001F, scale);
        float rowHeight = ClickGuiController.SEARCH_RESULTS_ROW_HEIGHT * safeScale;
        float gap = ClickGuiController.SEARCH_RESULTS_GAP * safeScale;
        float panelX = searchX;
        float panelY = searchY + searchHeight + gap;
        float panelHeight = safeCount == 0 ? 0.0F : rowHeight * safeCount + gap * (safeCount - 1);
        return new SearchResultsLayout(new Bounds(panelX, panelY, searchWidth, panelHeight), rowHeight, gap, safeCount);
    }

    Bounds panel() {
        return panel;
    }

    Bounds row(int index) {
        if (index < 0 || index >= count) {
            throw new IndexOutOfBoundsException(index);
        }
        return new Bounds(panel.x(), panel.y() + index * (rowHeight + gap), panel.width(), rowHeight);
    }

    int hit(double x, double y) {
        if (count == 0 || !panel.expanded(0.0F, gap * 0.5F).contains(x, y)) {
            return -1;
        }
        for (int i = 0; i < count; i++) {
            if (row(i).contains(x, y)) {
                return i;
            }
        }
        return -1;
    }
}
