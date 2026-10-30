package universalmod.render.atmosphere;

import java.nio.ByteBuffer;
public final class WeatherUniformLayout {
    public static final int MATRIX_BYTES = 64;
    public static final int VECTOR_BYTES = 16;
    public static final int VECTOR_COUNT = 35;
    public static final int TOTAL_BYTES = MATRIX_BYTES + VECTOR_COUNT * VECTOR_BYTES;

    private WeatherUniformLayout() {
    }

    public static int offset(int index) {
        if (index < 0 || index >= VECTOR_COUNT) {
            throw new IndexOutOfBoundsException("Atmosphere uniform vector: " + index);
        }
        return MATRIX_BYTES + index * VECTOR_BYTES;
    }

    public static void scalar(ByteBuffer data, int index, float value) {
        data.putFloat(offset(index), value);
    }

    public static void vec2(ByteBuffer data, int index, float x, float y) {
        int start = offset(index);
        data.putFloat(start, x);
        data.putFloat(start + 4, y);
    }

    public static void vec3(ByteBuffer data, int index, float x, float y, float z) {
        int start = offset(index);
        data.putFloat(start, x);
        data.putFloat(start + 4, y);
        data.putFloat(start + 8, z);
    }

    public static void vec4(ByteBuffer data, int index, float x, float y, float z, float w) {
        int start = offset(index);
        data.putFloat(start, x);
        data.putFloat(start + 4, y);
        data.putFloat(start + 8, z);
        data.putFloat(start + 12, w);
    }
}
