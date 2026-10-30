package universalmod.render.gpu;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;
import java.nio.ByteBuffer;

public final class GpuBuffer implements AutoCloseable {
    private int id;
    private final int size;

    public GpuBuffer(int size) {
        RenderSystem.assertOnRenderThread();
        this.size = size;
        int previous = GL15.glGetInteger(GL31.GL_UNIFORM_BUFFER_BINDING);
        id = GL15.glGenBuffers();
        try {
            GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, id);
            GL15.glBufferData(GL31.GL_UNIFORM_BUFFER, size, GL15.GL_DYNAMIC_DRAW);
        } finally { GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, previous); }
    }
    public int id() { return id; }
    public int size() { return size; }
    public boolean isClosed() { return id == 0; }
    public void write(ByteBuffer data) {
        RenderSystem.assertOnRenderThread();
        if (id == 0 || data.remaining() > size) throw new IllegalArgumentException("Uniform buffer write out of bounds");
        int previous = GL15.glGetInteger(GL31.GL_UNIFORM_BUFFER_BINDING);
        try {
            GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, id);
            GL15.glBufferSubData(GL31.GL_UNIFORM_BUFFER, 0, data);
        } finally { GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, previous); }
    }
    @Override public void close() {
        RenderSystem.assertOnRenderThread();
        if (id != 0) { GL15.glDeleteBuffers(id); id = 0; }
    }
}
