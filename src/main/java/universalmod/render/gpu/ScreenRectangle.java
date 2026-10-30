package universalmod.render.gpu;

import org.joml.Matrix3x2f;
import org.joml.Vector2f;

public record ScreenRectangle(int left, int top, int width, int height) {
    public ScreenRectangle intersection(ScreenRectangle other) {
        int x = Math.max(left, other.left), y = Math.max(top, other.top);
        int right = Math.min(left + width, other.left + other.width);
        int bottom = Math.min(top + height, other.top + other.height);
        return new ScreenRectangle(x, y, Math.max(0, right - x), Math.max(0, bottom - y));
    }

    public ScreenRectangle transformMaxBounds(Matrix3x2f pose) {
        Vector2f point = new Vector2f();
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            pose.transformPosition(left + ((i & 1) == 0 ? 0 : width),
                    top + ((i & 2) == 0 ? 0 : height), point);
            minX = Math.min(minX, point.x); minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x); maxY = Math.max(maxY, point.y);
        }
        int x = (int) Math.floor(minX), y = (int) Math.floor(minY);
        return new ScreenRectangle(x, y, (int) Math.ceil(maxX) - x, (int) Math.ceil(maxY) - y);
    }
}
