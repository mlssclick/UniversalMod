package universalmod.render.optimize;

import net.minecraft.client.gui.DrawContext;








public final class UiFrameContext {
    private static long sequence;
    private static long frameId;
    private static long nowNanos;
    private static long nowMillis;
    private static long previousRootNanos;
    private static float deltaSeconds = 1.0f / 60.0f;
    private static int depth;
    private static DrawContext graphics;

    private UiFrameContext() {
    }

    public static void begin(DrawContext nextGraphics) {
        if (depth == 0) {
            long sampledNanos = System.nanoTime();
            long sampledMillis = System.currentTimeMillis();
            frameId = ++sequence;
            nowNanos = sampledNanos;
            nowMillis = sampledMillis;
            if (previousRootNanos != 0L) {
                deltaSeconds = clamp((sampledNanos - previousRootNanos) / 1_000_000_000.0f, 0.0f, 0.1f);
            }
            previousRootNanos = sampledNanos;
            graphics = nextGraphics;
        } else if (graphics == null && nextGraphics != null) {
            graphics = nextGraphics;
        }
        depth++;
    }

    public static void end() {
        if (depth <= 0) {
            depth = 0;
            return;
        }
        depth--;
        if (depth == 0) {
            graphics = null;
        }
    }

    public static boolean active() {
        return depth > 0;
    }

    public static long frameId() {
        return frameId;
    }

    public static long nowNanos() {
        return active() ? nowNanos : System.nanoTime();
    }

    public static long nowMillis() {
        return active() ? nowMillis : System.currentTimeMillis();
    }

    public static float deltaSeconds() {
        return active() ? deltaSeconds : 1.0f / 60.0f;
    }

    public static DrawContext graphics() {
        return graphics;
    }

    public static boolean intersects(float x, float y, float width, float height,
                                     float clipX, float clipY, float clipWidth, float clipHeight) {
        return width > 0.0f
                && height > 0.0f
                && clipWidth > 0.0f
                && clipHeight > 0.0f
                && x + width > clipX
                && x < clipX + clipWidth
                && y + height > clipY
                && y < clipY + clipHeight;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
