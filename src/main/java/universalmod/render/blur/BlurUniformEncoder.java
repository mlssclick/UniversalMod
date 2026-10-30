package universalmod.render.blur;

import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.List;

final class BlurUniformEncoder {
    private static final int PARAMS_PER_RECT = 3;
    private static final int FLOATS_PER_PARAM = 4;

    private BlurUniformEncoder() {
    }

    static ByteBuffer rects(MemoryStack stack, List<BlurPass> batch) {
        int usedBytes = Math.max(1, batch.size()) * PARAMS_PER_RECT * FLOATS_PER_PARAM * Float.BYTES;
        ByteBuffer data = stack.calloc(usedBytes);
        for (int i = 0; i < batch.size(); i++) {
            BlurPass blur = batch.get(i);
            int offset = i * PARAMS_PER_RECT * FLOATS_PER_PARAM * Float.BYTES;
            data.putFloat(offset, blur.radiusTopLeft());
            data.putFloat(offset + 4, blur.radiusTopRight());
            data.putFloat(offset + 8, blur.radiusBottomRight());
            data.putFloat(offset + 12, blur.radiusBottomLeft());
            data.putFloat(offset + 16, blur.width());
            data.putFloat(offset + 20, blur.height());
            data.putFloat(offset + 24, blur.smoothness());
            data.putFloat(offset + 28, blur.verticalSplit());
            putColor(data, offset + 32, blur.splitColor());
        }
        data.position(0);
        return data;
    }

    static ByteBuffer region(MemoryStack stack, int x, int y, int width, int height) {
        ByteBuffer data = stack.calloc(4 * Float.BYTES);
        data.putFloat(0, x);
        data.putFloat(4, y);
        data.putFloat(8, width);
        data.putFloat(12, height);
        data.position(0);
        return data;
    }

    private static void putColor(ByteBuffer data, int offset, int color) {
        data.putFloat(offset, ((color >>> 16) & 0xFF) / 255.0f);
        data.putFloat(offset + 4, ((color >>> 8) & 0xFF) / 255.0f);
        data.putFloat(offset + 8, (color & 0xFF) / 255.0f);
        data.putFloat(offset + 12, ((color >>> 24) & 0xFF) / 255.0f);
    }
}
