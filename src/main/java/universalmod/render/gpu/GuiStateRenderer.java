package universalmod.render.gpu;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public final class GuiStateRenderer implements AutoCloseable {
    private static final GuiStateRenderer INSTANCE = new GuiStateRenderer();
    private int vao, vertices, indices;
    private GpuBuffer dynamic, projection;
    private final java.util.ArrayDeque<GuiElementRenderState> pending = new java.util.ArrayDeque<>();

    private GuiStateRenderer() { }
    public static GuiStateRenderer getInstance() { return INSTANCE; }

    public void submitGuiElement(GuiElementRenderState state) {
        flush();
        draw(state);
    }

    public void queueGuiElement(GuiElementRenderState state) {
        RenderSystem.assertOnRenderThread();
        if (state == null) return;
        if (pending.size() >= 4096) flush();
        pending.addLast(state);
    }

    public void flush() {
        RenderSystem.assertOnRenderThread();
        while (!pending.isEmpty()) draw(pending.removeFirst());
    }

    public void nextStratum() { flush(); }
    public void up() { flush(); }
    public void reset() { flush(); }

    private void draw(GuiElementRenderState state) {
        RenderSystem.assertOnRenderThread();
        if (state == null || state.bounds() == null || state.bounds().width() <= 0 || state.bounds().height() <= 0) return;
        RenderPipeline pipeline = state.pipeline();
        pipeline.begin();
        GuiVertexConsumer consumer = new GuiVertexConsumer();
        state.buildVertices(consumer);
        ByteBuffer data = consumer.quads();
        if (!data.hasRemaining()) return;
        int oldProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int oldVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int oldArray = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int oldUniform = GL11.glGetInteger(GL31.GL_UNIFORM_BUFFER_BINDING);
        int oldActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        IntBuffer clip = BufferUtils.createIntBuffer(4); GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, clip);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int oldTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int texture = 0, minFilter = 0, magFilter = 0;
        try {
            ensureObjects();
            int program = pipeline.program();
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(vao); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertices);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STREAM_DRAW);
            int vertexCount = data.remaining() / GuiVertexConsumer.STRIDE;
            IntBuffer elementData = BufferUtils.createIntBuffer(vertexCount / 4 * 6);
            for (int base=0;base<vertexCount;base+=4) {
                elementData.put(base).put(base+1).put(base+2).put(base).put(base+2).put(base+3);
            }
            elementData.flip();
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, indices);
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, elementData, GL15.GL_STREAM_DRAW);
            restore(GL11.GL_BLEND, pipeline.blend()); GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_CULL_FACE);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            ScreenRectangle rect = state.scissorArea();
            if (rect != null) {
                var window = MinecraftClient.getInstance().getWindow(); double scale = window.getScaleFactor();
                int left = (int) Math.floor(rect.left() * scale), bottom = window.getHeight() - (int) Math.ceil((rect.top() + rect.height()) * scale);
                int right = (int) Math.ceil((rect.left() + rect.width()) * scale), top = window.getHeight() - (int) Math.floor(rect.top() * scale);
                if (scissor) { left = Math.max(left, clip.get(0)); bottom = Math.max(bottom, clip.get(1));
                    right = Math.min(right, clip.get(0) + clip.get(2)); top = Math.min(top, clip.get(1) + clip.get(3)); }
                GL11.glEnable(GL11.GL_SCISSOR_TEST); GL11.glScissor(left, bottom, Math.max(0, right-left), Math.max(0, top-bottom));
            }
            TextureSetup setup = state.textureSetup();
            texture = setup == null ? 0 : setup.texture().getAsInt();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            if (texture != 0) {
                minFilter = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
                magFilter = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER);
                int filter = setup.nearest() ? GL11.GL_NEAREST : GL11.GL_LINEAR;
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
            }
            updateTransforms();
            try (RenderPass pass = new RenderPass(program)) {
                pass.bind("DynamicTransforms", dynamic.id(), 0); pass.bind("Projection", projection.id(), 1);
                pass.sampler("Sampler0", 0); pipeline.prepare(pass);
                GL11.glDrawElements(GL11.GL_TRIANGLES, elementData.remaining(), GL11.GL_UNSIGNED_INT, 0L);
            }
        } finally {
            if (texture != 0) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, minFilter);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, magFilter);
            }
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, oldTexture); GL13.glActiveTexture(oldActive);
            GL20.glUseProgram(oldProgram); GL30.glBindVertexArray(oldVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, oldArray); GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, oldUniform);
            GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            restore(GL11.GL_BLEND, blend); restore(GL11.GL_DEPTH_TEST, depth); restore(GL11.GL_CULL_FACE, cull);
            GL11.glScissor(clip.get(0), clip.get(1), clip.get(2), clip.get(3)); restore(GL11.GL_SCISSOR_TEST, scissor);
        }
    }

    private void ensureObjects() {
        if (vao != 0) return;
        vao = GL30.glGenVertexArrays(); vertices = GL15.glGenBuffers(); indices = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertices);
        int[] counts = {3,4,2,2,2,3,1}, offsets = {0,12,28,36,44,52,64};
        for (int i=0;i<counts.length;i++) {
            GL20.glEnableVertexAttribArray(i);
            if (i==3 || i==4) GL30.glVertexAttribIPointer(i, counts[i], GL11.GL_INT, GuiVertexConsumer.STRIDE, offsets[i]);
            else GL20.glVertexAttribPointer(i, counts[i], GL11.GL_FLOAT, false, GuiVertexConsumer.STRIDE, offsets[i]);
        }
        dynamic = new GpuBuffer(160); projection = new GpuBuffer(64);
    }

    private void updateTransforms() {
        ByteBuffer transforms = BufferUtils.createByteBuffer(160);
        RenderSystem.getModelViewMatrix().get(0, transforms);
        for (int i=0;i<4;i++) transforms.putFloat(64+i*4, 1);
        new Matrix4f().get(96, transforms);
        dynamic.write(transforms);
        ByteBuffer proj = BufferUtils.createByteBuffer(64); RenderSystem.getProjectionMatrix().get(0, proj); projection.write(proj);
    }
    private static void restore(int flag, boolean enabled) { if (enabled) GL11.glEnable(flag); else GL11.glDisable(flag); }
    @Override public void close() {
        RenderSystem.assertOnRenderThread();
        pending.clear();
        if (dynamic != null) { dynamic.close(); dynamic = null; }
        if (projection != null) { projection.close(); projection = null; }
        if (vertices != 0) { GL15.glDeleteBuffers(vertices); vertices = 0; }
        if (indices != 0) { GL15.glDeleteBuffers(indices); indices = 0; }
        if (vao != 0) { GL30.glDeleteVertexArrays(vao); vao = 0; }
    }
}
