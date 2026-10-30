package universalmod.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;

import java.util.ArrayDeque;
import java.util.Deque;

public final class ScissorUtil {
    private static final Deque<Rect> STACK = new ArrayDeque<>();
    private static int suspendDepth;

    private ScissorUtil() {
    }

    public static void push(int left, int top, int right, int bottom) {
        Window window = MinecraftClient.getInstance().getWindow();
        Rect requested = toFramebuffer(window, left, top, right, bottom);
        Rect screen = new Rect(0, 0, Math.max(0, window.getWidth()), Math.max(0, window.getHeight()));
        Rect clipped = requested.intersect(screen);
        if (!STACK.isEmpty()) {
            clipped = clipped.intersect(STACK.peek());
        }
        STACK.push(clipped);
        apply();
    }

    public static void pop() {
        if (!STACK.isEmpty()) {
            STACK.pop();
        }
        apply();
    }

    public static void clear() {
        STACK.clear();
        suspendDepth = 0;
        RenderSystem.disableScissor();
    }

    public static void suspend() {
        suspendDepth++;
        if (suspendDepth == 1) {
            RenderSystem.disableScissor();
        }
    }

    public static void resume() {
        if (suspendDepth > 0) {
            suspendDepth--;
        }
        if (suspendDepth == 0) {
            apply();
        }
    }

    public static boolean active() {
        return !STACK.isEmpty();
    }

    public static int[] currentFramebufferBounds() {
        Rect r = STACK.peek();
        return r == null ? null : new int[]{r.x, r.y, r.width, r.height};
    }

    public static universalmod.render.gpu.ScreenRectangle current() {
        Rect rect = STACK.peek();
        if (rect == null || suspendDepth > 0) return null;
        Window window = MinecraftClient.getInstance().getWindow();
        double scale = window.getScaleFactor();
        int left = floor(rect.x / scale), top = floor((window.getHeight() - rect.y - rect.height) / scale);
        int right = ceil((rect.x + rect.width) / scale), bottom = ceil((window.getHeight() - rect.y) / scale);
        return new universalmod.render.gpu.ScreenRectangle(left, top, Math.max(0, right-left), Math.max(0,bottom-top));
    }

    private static void apply() {
        if (suspendDepth > 0) {
            return;
        }
        Rect rect = STACK.peek();
        if (rect == null) {
            RenderSystem.disableScissor();
            return;
        }
        RenderSystem.enableScissor(rect.x, rect.y, rect.width, rect.height);
    }

    private static Rect toFramebuffer(Window window, int left, int top, int right, int bottom) {
        double scale = window.getScaleFactor();
        if (!Double.isFinite(scale) || scale <= 0.0) {
            scale = 1.0;
        }
        int x0 = floor(left * scale);
        int x1 = ceil(right * scale);
        int y0 = floor(top * scale);
        int y1 = ceil(bottom * scale);
        int glY = window.getHeight() - y1;
        return new Rect(x0, glY, Math.max(0, x1 - x0), Math.max(0, y1 - y0));
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static int ceil(double value) {
        return (int) Math.ceil(value);
    }

    private record Rect(int x, int y, int width, int height) {
        private Rect intersect(Rect other) {
            int left = Math.max(x, other.x);
            int bottom = Math.max(y, other.y);
            int right = Math.min(x + width, other.x + other.width);
            int top = Math.min(y + height, other.y + other.height);
            return new Rect(left, bottom, Math.max(0, right - left), Math.max(0, top - bottom));
        }
    }
}
