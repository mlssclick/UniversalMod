package universalmod.gui.ui.control;

import java.util.ArrayList;
import java.util.List;


public final class SegmentGridLayout {
    private SegmentGridLayout() {
    }

    public static float controlHeight(int itemCount, int maxColumns, float top, float segmentHeight, float gap) {
        int rows = rows(itemCount, maxColumns);
        return top + rows * segmentHeight + Math.max(0, rows - 1) * gap + 2.0F;
    }

    public static List<Bounds> cells(float x, float startY, float width, float segmentHeight,
                                     float gap, int itemCount, int maxColumns) {
        if (itemCount <= 0) {
            return List.of();
        }
        int columns = Math.max(1, maxColumns);
        float segmentWidth = Math.max(0.0F, (width - gap * (columns - 1)) / columns);
        List<Bounds> cells = new ArrayList<>(itemCount);
        for (int i = 0; i < itemCount; i++) {
            int row = i / columns;
            int column = i % columns;
            cells.add(new Bounds(
                    x + column * (segmentWidth + gap),
                    startY + row * (segmentHeight + gap),
                    segmentWidth,
                    segmentHeight
            ));
        }
        return List.copyOf(cells);
    }

    private static int rows(int itemCount, int maxColumns) {
        int columns = Math.max(1, maxColumns);
        return Math.max(1, (Math.max(0, itemCount) + columns - 1) / columns);
    }
}
