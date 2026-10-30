package universalmod.render.gpu;

import org.lwjgl.BufferUtils;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class IndexedGuiBatch<T> implements AutoCloseable {
    public static final int DEFAULT_CAPACITY = 64;
    @FunctionalInterface public interface Encoder<T> { void encode(ByteBuffer target, int offset, T value); }
    private final int capacity;
    private final int stride;
    private final String uniform;
    private final Encoder<T> encoder;
    private final List<T> prepared = new ArrayList<>();
    private final GpuUniformBuffer buffer;
    private boolean dirty;
    private final RenderPipeline pipeline;
    private java.util.function.Consumer<RenderPass> extraBindings = pass -> { };

    public IndexedGuiBatch(String shader, String uniform, int vec4Count, Encoder<T> encoder) {
        this(shader, shader, uniform, vec4Count, DEFAULT_CAPACITY, encoder);
    }
    public IndexedGuiBatch(String vertex, String fragment, String uniform, int vec4Count, Encoder<T> encoder) {
        this(vertex, fragment, uniform, vec4Count, DEFAULT_CAPACITY, encoder);
    }
    public IndexedGuiBatch(String shader, String uniform, int vec4Count, int capacity, Encoder<T> encoder) {
        this(shader, shader, uniform, vec4Count, capacity, encoder);
    }
    public IndexedGuiBatch(String vertex, String fragment, String uniform,
                           int vec4Count, int capacity, Encoder<T> encoder) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity");
        this.capacity = capacity;
        this.stride = vec4Count * 16; this.uniform = uniform; this.encoder = encoder;
        buffer = new GpuUniformBuffer(uniform, capacity * stride);
        pipeline = new RenderPipeline(vertex, fragment, this::begin, this::bind).withParamCapacity(capacity * vec4Count);
    }
    public RenderPipeline pipeline() { return pipeline; }
    public IndexedGuiBatch<T> withBindings(java.util.function.Consumer<RenderPass> bindings) {
        extraBindings = java.util.Objects.requireNonNull(bindings);
        return this;
    }
    public void begin() { prepared.clear(); dirty = false; }
    public int reserve(T value) {
        if (prepared.size() >= capacity) throw new IllegalStateException(uniform + " batch capacity exceeded");
        int index = prepared.size(); prepared.add(value); dirty = true; return index;
    }
    public void prepare() {
        if (!dirty) return;
        ByteBuffer data = BufferUtils.createByteBuffer(Math.max(1, prepared.size()) * stride);
        for (int i=0;i<prepared.size();i++) encoder.encode(data, i * stride, prepared.get(i));
        if (!buffer.write(data)) throw new IllegalStateException(uniform + " upload failed");
        dirty = false;
    }
    public void bind(RenderPass pass) { prepare(); pass.setUniform(uniform, buffer.buffer()); extraBindings.accept(pass); }
    @Override public void close() { prepared.clear(); buffer.close(); pipeline.close(); }
    public static void vec4(ByteBuffer data, int offset, float x, float y, float z, float w) {
        data.putFloat(offset,x).putFloat(offset+4,y).putFloat(offset+8,z).putFloat(offset+12,w);
    }
    public static void color(ByteBuffer data, int offset, int argb) { ColorPacker.putRgbaFloat(data,offset,argb); }
    public static float radius(float value, float width, float height) { return Math.max(0,Math.min(value,Math.min(width,height)*.5f)); }
}
