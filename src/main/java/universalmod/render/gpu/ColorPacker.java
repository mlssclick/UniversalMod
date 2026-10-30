package universalmod.render.gpu;

import java.nio.ByteBuffer;
import java.util.Objects;


public final class ColorPacker {
    private ColorPacker() {
    }

    public static void putRgbaFloat(ByteBuffer data, int offset, int argb) {
        Objects.requireNonNull(data, "data");
        data.putFloat(offset, ((argb >>> 16) & 0xFF) / 255.0F);
        data.putFloat(offset + 4, ((argb >>> 8) & 0xFF) / 255.0F);
        data.putFloat(offset + 8, (argb & 0xFF) / 255.0F);
        data.putFloat(offset + 12, ((argb >>> 24) & 0xFF) / 255.0F);
    }
}
