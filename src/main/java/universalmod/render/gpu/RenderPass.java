package universalmod.render.gpu;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RenderPass implements AutoCloseable {
    private final int program;
    private final Map<Integer, Integer> previousBindings = new LinkedHashMap<>();
    private int nextBinding = 2;

    RenderPass(int program) { this.program = program; }

    public void setUniform(String name, GpuBuffer buffer) {
        if (buffer == null || buffer.isClosed()) throw new IllegalStateException("Missing uniform buffer: " + name);
        bind(name, buffer.id(), nextBinding++);
    }

    void bind(String name, int buffer, int binding) {
        int block = GL31.glGetUniformBlockIndex(program, name);
        if (block == GL31.GL_INVALID_INDEX) return;
        previousBindings.putIfAbsent(binding, GL30.glGetIntegeri(GL31.GL_UNIFORM_BUFFER_BINDING, binding));
        GL31.glUniformBlockBinding(program, block, binding);
        GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, binding, buffer);
    }

    public void sampler(String name, int unit) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location >= 0) GL20.glUniform1i(location, unit);
    }

    @Override public void close() {
        int previous = GL11.glGetInteger(GL31.GL_UNIFORM_BUFFER_BINDING);
        previousBindings.forEach((binding, buffer) -> GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, binding, buffer));
        org.lwjgl.opengl.GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, previous);
    }
}
