package universalmod.render.gpu;

import java.nio.ByteBuffer;
import java.util.Objects;
import org.slf4j.LoggerFactory;

public final class GpuUniformBuffer implements AutoCloseable {
    private final String debugName;
    private final int capacityBytes;
    private GpuBuffer buffer;
    private boolean degraded;

    public GpuUniformBuffer(String debugName, int capacityBytes) {
        this.debugName=Objects.requireNonNull(debugName,"debugName");
        if(capacityBytes<=0) throw new IllegalArgumentException("capacityBytes must be positive");
        this.capacityBytes=capacityBytes;
    }
    public GpuBuffer buffer() {
        if(buffer==null || buffer.isClosed()) buffer=new GpuBuffer(capacityBytes);
        return buffer;
    }
    public boolean write(ByteBuffer data) {
        Objects.requireNonNull(data,"data");
        if(data.remaining()>capacityBytes) { degraded=true; return false; }
        try { buffer().write(data); degraded=false; return true; }
        catch(RuntimeException failure) {
            degraded=true;
            LoggerFactory.getLogger("UniversalMod/GpuUniformBuffer").error("Uniform upload failed: {}",debugName,failure);
            return false;
        }
    }
    public boolean degraded() { return degraded; }
    public int capacityBytes() { return capacityBytes; }
    @Override public void close() { if(buffer!=null) { buffer.close(); buffer=null; } }
}
