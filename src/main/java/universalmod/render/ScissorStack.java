package universalmod.render;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;

public final class ScissorStack {
    private static final Deque<Rect> STACK = new ArrayDeque<>();
    private static int suspendDepth;
    
    
    private static Rect inheritedClip;
    private static boolean inheritedEnabled;
    private static Rect suspendedClip;
    private static boolean suspendedEnabled;

    private ScissorStack() {
    }

     
    public record FramebufferBounds(int x, int y, int width, int height) { }

    public static FramebufferBounds currentFramebufferBounds() {
        Rect active = STACK.peek();
        return active == null ? null : new FramebufferBounds(active.x, active.y, active.width, active.height);
    }

    public static void push(float x, float y, float width, float height) {
        Window window = MinecraftClient.getInstance().getWindow();
        if (STACK.isEmpty()) {
            inheritedEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            inheritedClip = inheritedEnabled ? readGlScissor() : null;
        }
        Rect requested = toFramebuffer(window, x, y, width, height);
        Rect screen = new Rect(0, 0, Math.max(0, window.getWidth()), Math.max(0, window.getHeight()));
        Rect clipped = requested.intersect(screen);
        if (!STACK.isEmpty()) clipped = clipped.intersect(STACK.peek());
        else if (inheritedEnabled && inheritedClip != null) clipped = clipped.intersect(inheritedClip);
        STACK.push(clipped);
        apply();
    }

    public static void pop() {
        if (STACK.isEmpty()) return;
        STACK.pop();
        apply();
        if (STACK.isEmpty()) {
            inheritedClip = null;
            inheritedEnabled = false;
        }
    }

    public static void suspend() {
        suspendDepth++;
        if (suspendDepth == 1) {
            suspendedEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            suspendedClip = suspendedEnabled ? readGlScissor() : null;
            RenderSystem.disableScissor();
        }
    }

    public static void resume() {
        if (suspendDepth <= 0) return;
        suspendDepth--;
        if (suspendDepth == 0) {
            if (suspendedEnabled && suspendedClip != null) {
                Rect r = suspendedClip;
                RenderSystem.enableScissor(r.x, r.y, r.width, r.height);
            } else RenderSystem.disableScissor();
            suspendedClip = null;
            suspendedEnabled = false;
        }
    }

    private static void apply() {
        if (suspendDepth > 0) return;
        if (STACK.isEmpty()) {
            if (inheritedEnabled && inheritedClip != null) {
                Rect r = inheritedClip;
                RenderSystem.enableScissor(r.x, r.y, r.width, r.height);
            } else RenderSystem.disableScissor();
            return;
        }
        Rect r = STACK.peek();
        RenderSystem.enableScissor(r.x, r.y, r.width, r.height);
    }

    private static Rect readGlScissor() {
        int[] box = new int[4];
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, box);
        return new Rect(box[0], box[1], box[2], box[3]);
    }

    private static Rect toFramebuffer(Window window, float x, float y, float width, float height) {
        double scale = window.getScaleFactor();
        if (!Double.isFinite(scale) || scale <= 0.0) scale = 1.0;
        double x0 = Math.min(x, x + width);
        double x1 = Math.max(x, x + width);
        double y0 = Math.min(y, y + height);
        double y1 = Math.max(y, y + height);
        int left = floorToInt(x0 * scale);
        int right = ceilToInt(x1 * scale);
        int top = floorToInt(y0 * scale);
        int bottom = ceilToInt(y1 * scale);
        int framebufferHeight = Math.max(0, window.getHeight());
        int glY = framebufferHeight - bottom;
        return new Rect(left, glY, Math.max(0, right - left), Math.max(0, bottom - top));
    }

    private static int floorToInt(double value) {
        if (value <= Integer.MIN_VALUE) return Integer.MIN_VALUE;
        if (value >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) Math.floor(value);
    }

    private static int ceilToInt(double value) {
        if (value <= Integer.MIN_VALUE) return Integer.MIN_VALUE;
        if (value >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) Math.ceil(value);
    }

    private record Rect(int x, int y, int width, int height) {
        Rect intersect(Rect other) {
            long ax1 = (long) x + width;
            long ay1 = (long) y + height;
            long bx1 = (long) other.x + other.width;
            long by1 = (long) other.y + other.height;
            int nx = Math.max(x, other.x);
            int ny = Math.max(y, other.y);
            long nr = Math.min(ax1, bx1);
            long nb = Math.min(ay1, by1);
            int nw = nr <= nx ? 0 : (int) Math.min(Integer.MAX_VALUE, nr - nx);
            int nh = nb <= ny ? 0 : (int) Math.min(Integer.MAX_VALUE, nb - ny);
            return new Rect(nx, ny, nw, nh);
        }
    }
}
