package universalmod.render.gpu;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;

import java.nio.ByteBuffer;
import java.util.Objects;

public final class GpuUniformBuffer implements AutoCloseable {
    private final String debugName;
    private final int capacityBytes;
    private GpuBuffer buffer;
    private boolean degraded;

    public GpuUniformBuffer(String debugName, int capacityBytes) {
        this.debugName = Objects.requireNonNull(debugName, "debugName");
        if (capacityBytes <= 0) {
            throw new IllegalArgumentException("capacityBytes must be positive");
        }
        this.capacityBytes = capacityBytes;
    }

    public GpuBuffer buffer() {
        return ensureBuffer();
    }

    public boolean write(ByteBuffer data) {
        Objects.requireNonNull(data, "data");
        if (data.remaining() > capacityBytes) {
            degraded = true;
            return false;
        }

        GpuBuffer target = ensureBuffer();
        if (target == null) {
            return false;
        }
        try {
            RenderSystem.getDevice()
                    .createCommandEncoder()
                    .writeToBuffer(target.slice(0, data.remaining()), data);
            degraded = false;
            return true;
        } catch (RuntimeException failure) {
            degraded = true;
            return false;
        }
    }

    public boolean degraded() {
        return degraded;
    }

    public int capacityBytes() {
        return capacityBytes;
    }

    private GpuBuffer ensureBuffer() {
        if (buffer != null && !buffer.isClosed() && buffer.size() >= capacityBytes) {
            return buffer;
        }
        closeBuffer();
        try {
            buffer = RenderSystem.getDevice().createBuffer(
                    () -> debugName,
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    capacityBytes
            );
            degraded = false;
            return buffer;
        } catch (RuntimeException failure) {
            degraded = true;
            return null;
        }
    }

    @Override
    public void close() {
        closeBuffer();
    }

    private void closeBuffer() {
        if (buffer == null) {
            return;
        }
        try {
            buffer.close();
        } catch (RuntimeException failure) {
            degraded = true;
        } finally {
            buffer = null;
        }
    }
}
