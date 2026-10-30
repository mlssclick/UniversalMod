package universalmod.hud.interaction;

import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;

import java.nio.ByteBuffer;

public final class HudResizeCursor {
    private static final int CURSOR_NWSE = 0x00036007;
    private static final int CURSOR_NESW = 0x00036008;
    private static final int CURSOR_SIZE = 24;

    private static long nwse;
    private static long nesw;
    private static long horizontal;
    private static long vertical;
    private static long activeWindow;
    private static int activeShape;

    private HudResizeCursor() { }

    public static void update(long window, HudResizeSession.Handle handle) {
        if (window == 0L || handle == null || !handle.active()) {
            reset();
            return;
        }
        int shape = handle.horizontalOnly() ? GLFW.GLFW_HRESIZE_CURSOR
                : handle.verticalOnly() ? GLFW.GLFW_VRESIZE_CURSOR
                : handle.northwestSoutheast() ? CURSOR_NWSE : CURSOR_NESW;
        if (activeWindow == window && activeShape == shape) return;
        long cursor = shape == CURSOR_NWSE ? nwse : shape == CURSOR_NESW ? nesw
                : shape == GLFW.GLFW_HRESIZE_CURSOR ? horizontal : vertical;
        if (cursor == 0L) {
            cursor = GLFW.glfwCreateStandardCursor(shape);
            if (cursor == 0L) cursor = fallback(shape == CURSOR_NWSE || shape == GLFW.GLFW_HRESIZE_CURSOR);
            if (shape == CURSOR_NWSE) nwse = cursor;
            else if (shape == CURSOR_NESW) nesw = cursor;
            else if (shape == GLFW.GLFW_HRESIZE_CURSOR) horizontal = cursor;
            else vertical = cursor;
        }
        if (cursor == 0L) {
            reset();
            return;
        }
        if (activeWindow != 0L && activeWindow != window) reset();
        GLFW.glfwSetCursor(window, cursor);
        activeWindow = window;
        activeShape = shape;
    }

    public static void reset() {
        if (activeWindow != 0L) {
            GLFW.glfwSetCursor(activeWindow, 0L);
            activeWindow = 0L;
            activeShape = 0;
        }
    }

    private static long fallback(boolean nwseDiagonal) {
        ByteBuffer pixels = BufferUtils.createByteBuffer(CURSOR_SIZE * CURSOR_SIZE * 4);
        for (int py = 0; py < CURSOR_SIZE; py++) {
            for (int px = 0; px < CURSOR_SIZE; px++) {
                int x = px - 12;
                int y = py - 12;
                if (!nwseDiagonal) x = -x;
                int distance = Math.abs(x - y);
                int radial = Math.max(Math.abs(x), Math.abs(y));
                boolean stem = radial <= 7 && distance <= 1;
                boolean arrow = radial >= 6 && radial <= 9
                        && (Math.abs(x - y) <= 1 || (Math.abs(x) >= 6 && Math.abs(y) <= 2)
                        || (Math.abs(y) >= 6 && Math.abs(x) <= 2));
                boolean visible = stem || arrow;
                pixels.put((byte) 255).put((byte) 255).put((byte) 255)
                        .put((byte) (visible ? 255 : 0));
            }
        }
        pixels.flip();
        try (GLFWImage image = GLFWImage.malloc()) {
            image.set(CURSOR_SIZE, CURSOR_SIZE, pixels);
            return GLFW.glfwCreateCursor(image, 12, 12);
        }
    }
}
