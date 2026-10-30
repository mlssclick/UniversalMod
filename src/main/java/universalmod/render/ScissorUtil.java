package universalmod.render;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.gui.navigation.ScreenRectangle;

public final class ScissorUtil {
    private static final ThreadLocal<Deque<Entry>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private ScissorUtil() {
    }

    public static void push(int left, int top, int right, int bottom) {
        int x0 = Math.min(left, right);
        int y0 = Math.min(top, bottom);
        int x1 = Math.max(left, right);
        int y1 = Math.max(top, bottom);

        Entry current = STACK.get().peek();
        if (current != null) {
            x0 = Math.max(x0, current.left);
            y0 = Math.max(y0, current.top);
            x1 = Math.min(x1, current.right);
            y1 = Math.min(y1, current.bottom);
            if (x1 < x0) {
                x1 = x0;
            }
            if (y1 < y0) {
                y1 = y0;
            }
        }
        STACK.get().push(new Entry(x0, y0, x1, y1));
    }

    public static void pop() {
        Deque<Entry> stack = STACK.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
        if (stack.isEmpty()) {
            STACK.remove();
        }
    }

    public static ScreenRectangle current() {
        Entry entry = STACK.get().peek();
        return entry == null ? null : entry.rectangle;
    }

    public static ScissorBox currentBox() {
        Entry entry = STACK.get().peek();
        return entry == null ? null : new ScissorBox(entry.left, entry.top, entry.right, entry.bottom);
    }

    public static int depth() {
        return STACK.get().size();
    }

    public static void clear() {
        STACK.remove();
    }

    public record ScissorBox(int left, int top, int right, int bottom) {
        public int width() {
            return Math.max(0, right - left);
        }

        public int height() {
            return Math.max(0, bottom - top);
        }
    }

    private static final class Entry {
        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final ScreenRectangle rectangle;

        private Entry(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.rectangle = new ScreenRectangle(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
        }
    }
}
