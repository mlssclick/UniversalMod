package universalmod.render.blur;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

final class BlurUniformEncoder {
    static final int MAX_RECTS = 1024;
    static final int FLOATS_PER_RECT = 12;
    static final int BYTES_PER_RECT = 48;

    private BlurUniformEncoder() { }

    static ByteBuffer rects(List<BlurPass> batch) {
        if (batch == null || batch.size() > MAX_RECTS) {
            throw new IllegalArgumentException("Blur batch must contain at most " + MAX_RECTS + " rectangles");
        }
        ByteBuffer data = ByteBuffer.allocateDirect(Math.max(1, batch.size()) * BYTES_PER_RECT)
                .order(ByteOrder.nativeOrder());
        for (int i = 0; i < batch.size(); i++) {
            BlurPass blur = batch.get(i);
            if (blur == null) throw new IllegalArgumentException("Null blur rectangle");
            int off = i * BYTES_PER_RECT;
            data.putFloat(off, blur.radiusTopLeft());
            data.putFloat(off + 4, blur.radiusTopRight());
            data.putFloat(off + 8, blur.radiusBottomRight());
            data.putFloat(off + 12, blur.radiusBottomLeft());
            data.putFloat(off + 16, blur.width());
            data.putFloat(off + 20, blur.height());
            data.putFloat(off + 24, blur.smoothness());
            data.putFloat(off + 28, blur.verticalSplit());
            putColor(data, off + 32, blur.splitColor());
        }
        return data;
    }

    static ByteBuffer region(int x, int y, int width, int height) {
        ByteBuffer data = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
        data.putFloat(0, x);
        data.putFloat(4, y);
        data.putFloat(8, width);
        data.putFloat(12, height);
        return data;
    }

    private static void putColor(ByteBuffer data, int off, int argb) {
        data.putFloat(off, ((argb >>> 16) & 0xFF) / 255f);
        data.putFloat(off + 4, ((argb >>> 8) & 0xFF) / 255f);
        data.putFloat(off + 8, (argb & 0xFF) / 255f);
        data.putFloat(off + 12, ((argb >>> 24) & 0xFF) / 255f);
    }
}
