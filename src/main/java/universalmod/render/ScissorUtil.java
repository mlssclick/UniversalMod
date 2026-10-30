package universalmod.render;

import java.util.ArrayDeque;
import java.util.Deque;

 
public final class ScissorUtil {
    private static final ThreadLocal<Deque<ScissorBox>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private ScissorUtil() {}

    public static void push(int left, int top, int right, int bottom) {
        int x0 = Math.min(left, right);
        int y0 = Math.min(top, bottom);
        int x1 = Math.max(left, right);
        int y1 = Math.max(top, bottom);
        ScissorBox current = STACK.get().peek();
        if (current != null) {
            x0 = Math.max(x0, current.left());
            y0 = Math.max(y0, current.top());
            x1 = Math.min(x1, current.right());
            y1 = Math.min(y1, current.bottom());
            if (x1 < x0) x1 = x0;
            if (y1 < y0) y1 = y0;
        }
        STACK.get().push(new ScissorBox(x0, y0, x1, y1));
    }

    public static void pop() {
        Deque<ScissorBox> stack = STACK.get();
        if (!stack.isEmpty()) stack.pop();
        if (stack.isEmpty()) STACK.remove();
    }

    public static ScissorBox currentBox() {
        return STACK.get().peek();
    }

    public static int depth() {
        return STACK.get().size();
    }

    public static void clear() {
        STACK.remove();
    }

    public record ScissorBox(int left, int top, int right, int bottom) {
        public int width() { return Math.max(0, right - left); }
        public int height() { return Math.max(0, bottom - top); }
    }
}
