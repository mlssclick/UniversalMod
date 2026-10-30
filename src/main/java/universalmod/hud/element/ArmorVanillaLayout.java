package universalmod.hud.element;


public final class ArmorVanillaLayout {
    public static final int HEIGHT = 22;
    public static final int SLOT_PITCH = 20;
    public static final int FIRST_WIDTH = 22;
    public static final int HOTBAR_HALF_WIDTH = 91;
    public static final int SIDE_GAP = 7;

    private ArmorVanillaLayout() {
    }

    public static int width(int itemCount) {
        int count = Math.max(0, Math.min(4, itemCount));
        return count == 0 ? 0 : FIRST_WIDTH + (count - 1) * SLOT_PITCH;
    }

    public static int x(int screenWidth, int itemCount, Side side) {
        int stripWidth = width(itemCount);
        int center = screenWidth / 2;
        Side resolved = side == null ? Side.RIGHT : side;
        return resolved == Side.LEFT
                ? center - HOTBAR_HALF_WIDTH - SIDE_GAP - stripWidth
                : center + HOTBAR_HALF_WIDTH + SIDE_GAP;
    }

    public static int y(int screenHeight) {
        return screenHeight - HEIGHT;
    }

    public static int itemX(int stripX, int visibleSlotIndex) {
        return stripX + 3 + Math.max(0, visibleSlotIndex) * SLOT_PITCH;
    }

    public enum Side {
        LEFT,
        RIGHT
    }
}
