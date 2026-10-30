package universalmod.render.gpu;

import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class GuiVertexConsumer {
    public static final int STRIDE = 68;
    private static final int MAX_VERTICES = 65536;
    private final List<byte[]> vertices = new ArrayList<>();
    private ByteBuffer current;

    public GuiVertexConsumer addVertex(org.joml.Matrix4f matrix, float x, float y, float z) {
        finishVertex();
        if (vertices.size() >= MAX_VERTICES) throw new IllegalStateException("GUI vertex limit exceeded");
        var p = matrix.transformPosition(x, y, z, new org.joml.Vector3f());
        current = BufferUtils.createByteBuffer(STRIDE);
        current.putFloat(0,p.x).putFloat(4,p.y).putFloat(8,p.z);
        return setColor(0xFFFFFFFF);
    }

    public GuiVertexConsumer addVertexWith2DPose(Matrix3x2f pose, float x, float y) {
        finishVertex();
        if (vertices.size() >= MAX_VERTICES) throw new IllegalStateException("GUI vertex limit exceeded");
        Vector2f p = pose.transformPosition(x, y, new Vector2f());
        current = BufferUtils.createByteBuffer(STRIDE);
        current.putFloat(0, p.x).putFloat(4, p.y).putFloat(8, 0);
        return setColor(0xFFFFFFFF);
    }

    public GuiVertexConsumer setColor(int argb) {
        return setColor((argb >>> 16) & 255, (argb >>> 8) & 255, argb & 255, (argb >>> 24) & 255);
    }
    public GuiVertexConsumer setColor(int r, int g, int b, int a) {
        current.putFloat(12, r / 255f).putFloat(16, g / 255f).putFloat(20, b / 255f).putFloat(24, a / 255f);
        return this;
    }
    public GuiVertexConsumer setUv(float u, float v) { current.putFloat(28, u).putFloat(32, v); return this; }
    public GuiVertexConsumer setUv1(int u, int v) { current.putInt(36, u).putInt(40, v); return this; }
    public GuiVertexConsumer setUv2(int u, int v) { current.putInt(44, u).putInt(48, v); return this; }
    public GuiVertexConsumer setNormal(float x, float y, float z) {
        current.putFloat(52, x).putFloat(56, y).putFloat(60, z); return this;
    }
    public GuiVertexConsumer setLineWidth(float width) { current.putFloat(64, width); return this; }

    private void finishVertex() {
        if (current == null) return;
        byte[] data = new byte[STRIDE];
        current.position(0); current.get(data); vertices.add(data); current = null;
    }

    public ByteBuffer quads() {
        finishVertex();
        if (vertices.size() % 4 != 0) throw new IllegalStateException("GUI state produced an incomplete quad");
        ByteBuffer result = BufferUtils.createByteBuffer(vertices.size() * STRIDE);
        for (byte[] vertex : vertices) result.put(vertex);
        return result.flip();
    }
}
