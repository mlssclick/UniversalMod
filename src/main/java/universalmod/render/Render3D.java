package universalmod.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import universalmod.event.type.WorldRenderEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Render3D {
    private static final double MAX_SAFE_COORD = 30_000_000.0;
    private static final Map<VoxelShape, List<Box>> SHAPE_BOXES = new HashMap<>();

    public static final List<Line> LINE_DEPTH = new ArrayList<>();
    public static final List<Line> LINE = new ArrayList<>();
    public static final List<Line> LINE_OVERLAY = new ArrayList<>();
    public static final List<Quad> QUAD_DEPTH = new ArrayList<>();
    public static final List<Quad> QUAD = new ArrayList<>();

    public static final Matrix4f lastProjMat = new Matrix4f();
    public static final Matrix4f lastModMat = new Matrix4f();
    public static final Matrix4f lastWorldSpaceMatrix = new Matrix4f();
    public static MatrixStack.Entry lastWorldSpaceEntry = new MatrixStack().peek();
    public static float lastTickDelta = 1.0f;
    public static Vec3d lastCameraPos = Vec3d.ZERO;
    public static Quaternionf lastCameraRotation = new Quaternionf();

    private Render3D() {
    }

    public static void setLastWorldSpaceEntry(MatrixStack.Entry entry) {
        if (entry != null) lastWorldSpaceEntry = entry;
    }

    public static void setLastTickDelta(float tickDelta) {
        lastTickDelta = Float.isFinite(tickDelta) ? tickDelta : 1.0f;
    }

    public static void setLastCameraPos(Vec3d position) {
        if (finite(position)) lastCameraPos = position;
    }

    public static void setLastCameraRotation(Quaternionf rotation) {
        if (rotation != null) lastCameraRotation = new Quaternionf(rotation);
    }

    public static void onWorldRender(WorldRenderEvent event) {
        if (event == null || event.getMatrixStack() == null) {
            clearQueues();
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null || !finite(lastCameraPos)) {
            clearQueues();
            return;
        }

        MatrixStack matrices = event.getMatrixStack();

        try {
            renderQuads(matrices, lastCameraPos);
            renderLines(matrices, lastCameraPos);
        } finally {
            clearQueues();
        }
    }

    public static void render(WorldRenderEvent event) {
        onWorldRender(event);
    }

    public static void clearQueues() {
        LINE_DEPTH.clear();
        LINE.clear();
        LINE_OVERLAY.clear();
        QUAD_DEPTH.clear();
        QUAD.clear();
    }

    public static void close() {
        clearQueues();
        SHAPE_BOXES.clear();
    }

    private static void renderLines(MatrixStack matrices, Vec3d camera) {
        if (LINE.isEmpty() && LINE_DEPTH.isEmpty() && LINE_OVERLAY.isEmpty()) return;
        GlState state = GlState.capture();
        try {
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
            drawLineBatch(matrices, camera, LINE_DEPTH, true);
            drawLineBatch(matrices, camera, LINE, false);
            drawLineBatch(matrices, camera, LINE_OVERLAY, false);
        } finally {
            state.restore();
        }
    }

    private static void drawLineBatch(MatrixStack matrices, Vec3d camera, List<Line> lines, boolean depth) {
        if (lines.isEmpty()) return;
        if (depth) RenderSystem.enableDepthTest();
        else RenderSystem.disableDepthTest();
        Set<Float> widths = new LinkedHashSet<>();
        for (Line line : lines) widths.add(sanitizeWidth(line.width()));
        for (float width : widths) {
            RenderSystem.lineWidth(width);
            BufferBuilder buffer = Tessellator.getInstance().begin(DrawMode.LINES, VertexFormats.LINES);
            for (Line line : lines) {
                if (Float.compare(sanitizeWidth(line.width()), width) == 0) {
                    drawLineVertices(matrices, buffer, line, camera);
                }
            }
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        }
    }

    private static void drawLineVertices(MatrixStack matrices, VertexConsumer buffer, Line line, Vec3d camera) {
        Vec3d a = line.start(), b = line.end();
        if (!finite(a) || !finite(b)) return;
        Matrix4f transform = line.transform() == null
                ? matrices.peek().getPositionMatrix() : line.transform();
        Vector3f normal = lineNormal(a, b);
        buffer.vertex(transform, (float) (a.x - camera.x), (float) (a.y - camera.y), (float) (a.z - camera.z))
                .color(line.colorStart()).normal(normal.x, normal.y, normal.z);
        buffer.vertex(transform, (float) (b.x - camera.x), (float) (b.y - camera.y), (float) (b.z - camera.z))
                .color(line.colorEnd()).normal(normal.x, normal.y, normal.z);
    }

    private static void renderQuads(MatrixStack matrices, Vec3d camera) {
        if (QUAD_DEPTH.isEmpty() && QUAD.isEmpty()) return;
        GlState state = GlState.capture();
        try {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
            drawQuadBatch(matrices, camera, QUAD_DEPTH, true);
            drawQuadBatch(matrices, camera, QUAD, false);
        } finally {
            state.restore();
        }
    }

    private static void drawQuadBatch(MatrixStack matrices, Vec3d camera, List<Quad> quads, boolean depth) {
        if (quads.isEmpty()) return;
        if (depth) RenderSystem.enableDepthTest();
        else RenderSystem.disableDepthTest();
        BufferBuilder buffer = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (Quad quad : quads) {
            Matrix4f transform = quad.transform() == null
                    ? matrices.peek().getPositionMatrix() : quad.transform();
            vertex(transform, buffer, quad.x(), camera, quad.color());
            vertex(transform, buffer, quad.y(), camera, quad.color());
            vertex(transform, buffer, quad.w(), camera, quad.color());
            vertex(transform, buffer, quad.z(), camera, quad.color());
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private static void vertex(Matrix4f matrix, VertexConsumer consumer, Vec3d world, Vec3d camera, int color) {
        consumer.vertex(matrix, (float) (world.x - camera.x), (float) (world.y - camera.y),
                (float) (world.z - camera.z)).color(color);
    }

    public static void drawLine(double x1, double y1, double z1, double x2, double y2, double z2,
                                int color, float width, boolean depth) {
        drawLine(new Vec3d(x1, y1, z1), new Vec3d(x2, y2, z2), color, color, width, depth);
    }

    public static void drawLine(Vec3d start, Vec3d end, int color, float width, boolean depth) {
        drawLine(start, end, color, color, width, depth);
    }

    public static void drawLine(Vec3d start, Vec3d end, int colorStart, int colorEnd, float width, boolean depth) {
        drawLine((MatrixStack.Entry) null, start, end, colorStart, colorEnd, width, depth);
    }

    public static void drawLineGradient(Vec3d start, Vec3d end, int startColor, int endColor,
                                        float width, boolean depth) {
        drawLine(start, end, startColor, endColor, width, depth);
    }

    public static void drawLine(MatrixStack.Entry entry, double x1, double y1, double z1,
                                double x2, double y2, double z2, int color, float width, boolean depth) {
        drawLine(entry, new Vec3d(x1, y1, z1), new Vec3d(x2, y2, z2), color, color, width, depth);
    }

    public static void drawLine(MatrixStack.Entry entry, Vec3d start, Vec3d end,
                                int startColor, int endColor, float width, boolean depth) {
        if (!finite(start) || !finite(end)) return;
        Line line = new Line(snapshot(entry), start, end, startColor, endColor, sanitizeWidth(width));
        (depth ? LINE_DEPTH : LINE).add(line);
    }

    public static void drawLineOverlay(Vec3d start, Vec3d end, int color, float width) {
        drawLineOverlay(null, start, end, color, color, width);
    }

    public static void drawLineOverlay(MatrixStack.Entry entry, Vec3d start, Vec3d end,
                                       int colorStart, int colorEnd, float width) {
        if (!finite(start) || !finite(end)) return;
        LINE_OVERLAY.add(new Line(snapshot(entry), start, end, colorStart, colorEnd, sanitizeWidth(width)));
    }

    public static void drawQuad(Vec3d x, Vec3d y, Vec3d w, Vec3d z, int color, boolean depth) {
        drawQuad(null, x, y, w, z, color, depth);
    }

    public static void drawQuad(MatrixStack.Entry entry, Vec3d x, Vec3d y, Vec3d w, Vec3d z,
                                int color, boolean depth) {
        if (!finite(x) || !finite(y) || !finite(w) || !finite(z)) return;
        (depth ? QUAD_DEPTH : QUAD).add(new Quad(snapshot(entry), x, y, w, z, color));
    }

    public static void drawShape(BlockPos pos, VoxelShape shape, int color, float width) {
        drawShape(pos, shape, color, width, true, false);
    }

    public static void drawShape(BlockPos pos, VoxelShape shape, int color, float width,
                                 boolean fill, boolean depth) {
        if (pos == null || shape == null) return;
        for (Box local : SHAPE_BOXES.computeIfAbsent(shape, VoxelShape::getBoundingBoxes)) {
            drawBox(local.offset(pos), color, width, true, fill, depth);
        }
    }

    public static void drawBox(Box box, int color, float width) {
        drawBox(box, color, width, true, true, false);
    }

    public static void drawBox(Box box, int color, float width, boolean line, boolean fill, boolean depth) {
        drawBox(null, box, color, width, line, fill, depth);
    }

    public static void drawBox(MatrixStack.Entry entry, Box box, int color, float width,
                               boolean line, boolean fill, boolean depth) {
        if (!finite(box)) return;
        double x1 = box.minX, y1 = box.minY, z1 = box.minZ;
        double x2 = box.maxX, y2 = box.maxY, z2 = box.maxZ;
        if (fill) {

            int fillColor = multiplyAlpha(color, 0.3f);
            drawQuad(entry, v(x1,y1,z1), v(x2,y1,z1), v(x2,y1,z2), v(x1,y1,z2), fillColor, depth);
            drawQuad(entry, v(x1,y1,z1), v(x1,y2,z1), v(x2,y2,z1), v(x2,y1,z1), fillColor, depth);
            drawQuad(entry, v(x2,y1,z1), v(x2,y2,z1), v(x2,y2,z2), v(x2,y1,z2), fillColor, depth);
            drawQuad(entry, v(x1,y1,z2), v(x2,y1,z2), v(x2,y2,z2), v(x1,y2,z2), fillColor, depth);
            drawQuad(entry, v(x1,y1,z1), v(x1,y1,z2), v(x1,y2,z2), v(x1,y2,z1), fillColor, depth);
            drawQuad(entry, v(x1,y2,z1), v(x1,y2,z2), v(x2,y2,z2), v(x2,y2,z1), fillColor, depth);
        }
        if (line) boxOutline(entry, box, color, width, depth);
    }

    public static void filledBox(MatrixStack matrices, Box worldBox, int color, boolean depth) {
        if (worldBox == null || !finite(worldBox)) return;

        queueBoxFaces(matrices == null ? null : matrices.peek(), worldBox, color, depth);
    }

    public static void filledRelativeBox(MatrixStack matrices, Box relative, int color, boolean depth) {
        if (!finite(relative)) return;
        Vec3d camera = lastCameraPos;
        queueBoxFaces(matrices == null ? null : matrices.peek(), relative.offset(camera), color, depth);
    }

    private static void queueBoxFaces(MatrixStack.Entry entry, Box box, int color, boolean depth) {
        double x1=box.minX, y1=box.minY, z1=box.minZ, x2=box.maxX, y2=box.maxY, z2=box.maxZ;
        drawQuad(entry, v(x1,y1,z2), v(x2,y1,z2), v(x2,y2,z2), v(x1,y2,z2), color, depth);
        drawQuad(entry, v(x2,y1,z1), v(x1,y1,z1), v(x1,y2,z1), v(x2,y2,z1), color, depth);
        drawQuad(entry, v(x1,y1,z1), v(x1,y1,z2), v(x1,y2,z2), v(x1,y2,z1), color, depth);
        drawQuad(entry, v(x2,y1,z2), v(x2,y1,z1), v(x2,y2,z1), v(x2,y2,z2), color, depth);
        drawQuad(entry, v(x1,y2,z2), v(x2,y2,z2), v(x2,y2,z1), v(x1,y2,z1), color, depth);
        drawQuad(entry, v(x1,y1,z1), v(x2,y1,z1), v(x2,y1,z2), v(x1,y1,z2), color, depth);
    }

    public static void boxOutline(Box box, int color, float width, boolean depth) {
        boxOutline(null, box, color, width, depth);
    }

    private static void boxOutline(MatrixStack.Entry entry, Box box, int color, float width, boolean depth) {
        if (!finite(box)) return;
        double x1=box.minX, y1=box.minY, z1=box.minZ, x2=box.maxX, y2=box.maxY, z2=box.maxZ;
        drawLine(entry, x1,y1,z1, x2,y1,z1, color,width,depth);
        drawLine(entry, x2,y1,z1, x2,y1,z2, color,width,depth);
        drawLine(entry, x2,y1,z2, x1,y1,z2, color,width,depth);
        drawLine(entry, x1,y1,z2, x1,y1,z1, color,width,depth);
        drawLine(entry, x1,y1,z2, x1,y2,z2, color,width,depth);
        drawLine(entry, x1,y1,z1, x1,y2,z1, color,width,depth);
        drawLine(entry, x2,y1,z2, x2,y2,z2, color,width,depth);
        drawLine(entry, x2,y1,z1, x2,y2,z1, color,width,depth);
        drawLine(entry, x1,y2,z1, x2,y2,z1, color,width,depth);
        drawLine(entry, x2,y2,z1, x2,y2,z2, color,width,depth);
        drawLine(entry, x2,y2,z2, x1,y2,z2, color,width,depth);
        drawLine(entry, x1,y2,z2, x1,y2,z1, color,width,depth);
    }

    public static void boxVertices(Matrix4f matrix, BufferBuilder buffer, Box box, int color) {
        if (!finite(box)) return;
        double x1=box.minX,y1=box.minY,z1=box.minZ,x2=box.maxX,y2=box.maxY,z2=box.maxZ;
        relativeVertex(matrix,buffer,x1,y1,z2,color); relativeVertex(matrix,buffer,x2,y1,z2,color);
        relativeVertex(matrix,buffer,x2,y2,z2,color); relativeVertex(matrix,buffer,x1,y2,z2,color);
        relativeVertex(matrix,buffer,x2,y1,z1,color); relativeVertex(matrix,buffer,x1,y1,z1,color);
        relativeVertex(matrix,buffer,x1,y2,z1,color); relativeVertex(matrix,buffer,x2,y2,z1,color);
        relativeVertex(matrix,buffer,x1,y1,z1,color); relativeVertex(matrix,buffer,x1,y1,z2,color);
        relativeVertex(matrix,buffer,x1,y2,z2,color); relativeVertex(matrix,buffer,x1,y2,z1,color);
        relativeVertex(matrix,buffer,x2,y1,z2,color); relativeVertex(matrix,buffer,x2,y1,z1,color);
        relativeVertex(matrix,buffer,x2,y2,z1,color); relativeVertex(matrix,buffer,x2,y2,z2,color);
        relativeVertex(matrix,buffer,x1,y2,z2,color); relativeVertex(matrix,buffer,x2,y2,z2,color);
        relativeVertex(matrix,buffer,x2,y2,z1,color); relativeVertex(matrix,buffer,x1,y2,z1,color);
        relativeVertex(matrix,buffer,x1,y1,z1,color); relativeVertex(matrix,buffer,x2,y1,z1,color);
        relativeVertex(matrix,buffer,x2,y1,z2,color); relativeVertex(matrix,buffer,x1,y1,z2,color);
    }

    private static void relativeVertex(Matrix4f matrix, BufferBuilder buffer, double x, double y, double z, int color) {
        buffer.vertex(matrix, (float)x, (float)y, (float)z).color(color);
    }

    private static Matrix4f snapshot(MatrixStack.Entry entry) {
        return entry == null ? null : new Matrix4f(entry.getPositionMatrix());
    }

    private static Vec3d v(double x,double y,double z) { return new Vec3d(x,y,z); }

    private static Vector3f lineNormal(Vec3d start, Vec3d end) {
        float x=(float)(start.x-end.x),y=(float)(start.y-end.y),z=(float)(start.z-end.z);
        float length=MathHelper.sqrt(x*x+y*y+z*z);
        return !Float.isFinite(length) || length < 0.0001f
                ? new Vector3f(0,1,0) : new Vector3f(x/length,y/length,z/length);
    }

    private static boolean finite(Vec3d value) {
        return value != null && finite(value.x) && finite(value.y) && finite(value.z);
    }

    private static boolean finite(Box value) {
        return value != null && finite(value.minX) && finite(value.minY) && finite(value.minZ)
                && finite(value.maxX) && finite(value.maxY) && finite(value.maxZ);
    }

    private static boolean finite(double value) {
        return Double.isFinite(value) && Math.abs(value) <= MAX_SAFE_COORD;
    }

    private static int multiplyAlpha(int rgba, float fraction) {
        int alpha = Math.round(((rgba >>> 24) & 255) * fraction);
        return ((alpha & 255) << 24) | (rgba & 0xffffff);
    }

    private static float sanitizeWidth(float width) {
        if (!Float.isFinite(width)) return 1f;
        return MathHelper.clamp(width, 0.1f, 16f);
    }

    public record Line(Matrix4f transform, Vec3d start, Vec3d end, int colorStart, int colorEnd, float width) {}
    public record Quad(Matrix4f transform, Vec3d x, Vec3d y, Vec3d w, Vec3d z, int color) {}

    private record GlState(boolean blend, boolean cull, boolean depth, boolean smooth,
                           boolean writeDepth, float lineWidth, int srcRgb, int dstRgb,
                           int srcAlpha, int dstAlpha, ShaderProgram shader) {
        private static GlState capture() {
            return new GlState(GL11.glIsEnabled(GL11.GL_BLEND), GL11.glIsEnabled(GL11.GL_CULL_FACE),
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST), GL11.glIsEnabled(GL11.GL_LINE_SMOOTH),
                    GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK), GL11.glGetFloat(GL11.GL_LINE_WIDTH),
                    GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB),
                    GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB),
                    GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA),
                    GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA), RenderSystem.getShader());
        }

        private void restore() {
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (smooth) GL11.glEnable(GL11.GL_LINE_SMOOTH); else GL11.glDisable(GL11.GL_LINE_SMOOTH);
            RenderSystem.depthMask(writeDepth);
            RenderSystem.lineWidth(lineWidth);
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (shader == null) RenderSystem.clearShader();
            else RenderSystem.setShader(shader);
        }
    }
}
