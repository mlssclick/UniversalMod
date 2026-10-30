package universalmod.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import universalmod.render.color.ColorUtil;

import java.util.ArrayList;
import java.util.List;

public final class Render3D {
    private static final List<GradientLine> LINES = new ArrayList<>(1024);
    private static final List<Quad> QUADS = new ArrayList<>(512);
    public static Vec3d lastCameraPos = Vec3d.ZERO;
    public static final Quaternionf lastCameraRotation = new Quaternionf();

    private Render3D() {
    }

    public static void drawLine(Vec3d start, Vec3d end, int color, float width, boolean depth) {
        drawLineGradient(start, end, color, color, width, depth);
    }

    public static void drawLineGradient(Vec3d start, Vec3d end, int startColor, int endColor, float width, boolean depth) {
        if (start == null || end == null || !finite(start) || !finite(end)) return;
        LINES.add(new GradientLine(start, end, startColor, endColor, Math.max(0.5F, width), depth));
    }

    public static void drawQuad(Vec3d a, Vec3d b, Vec3d c, Vec3d d, int color, boolean depth) {
        if (a == null || b == null || c == null || d == null || !finite(a) || !finite(b) || !finite(c) || !finite(d))
            return;
        QUADS.add(new Quad(a, b, c, d, color, depth));
    }

    public static void drawFilledBox(Box box, int color, boolean depth) {
        if (box == null) return;
        double x1 = box.minX, y1 = box.minY, z1 = box.minZ, x2 = box.maxX, y2 = box.maxY, z2 = box.maxZ;
        drawQuad(new Vec3d(x1, y1, z2), new Vec3d(x2, y1, z2), new Vec3d(x2, y2, z2), new Vec3d(x1, y2, z2), color, depth);
        drawQuad(new Vec3d(x2, y1, z1), new Vec3d(x1, y1, z1), new Vec3d(x1, y2, z1), new Vec3d(x2, y2, z1), color, depth);
        drawQuad(new Vec3d(x1, y1, z1), new Vec3d(x1, y1, z2), new Vec3d(x1, y2, z2), new Vec3d(x1, y2, z1), color, depth);
        drawQuad(new Vec3d(x2, y1, z2), new Vec3d(x2, y1, z1), new Vec3d(x2, y2, z1), new Vec3d(x2, y2, z2), color, depth);
        drawQuad(new Vec3d(x1, y2, z2), new Vec3d(x2, y2, z2), new Vec3d(x2, y2, z1), new Vec3d(x1, y2, z1), color, depth);
        drawQuad(new Vec3d(x1, y1, z1), new Vec3d(x2, y1, z1), new Vec3d(x2, y1, z2), new Vec3d(x1, y1, z2), color, depth);
    }

    public static void drawBoxOutline(Box box, int color, float width, boolean depth) {
        if (box == null) return;
        double x1 = box.minX, y1 = box.minY, z1 = box.minZ, x2 = box.maxX, y2 = box.maxY, z2 = box.maxZ;
        drawLine(new Vec3d(x1, y1, z1), new Vec3d(x2, y1, z1), color, width, depth);
        drawLine(new Vec3d(x2, y1, z1), new Vec3d(x2, y1, z2), color, width, depth);
        drawLine(new Vec3d(x2, y1, z2), new Vec3d(x1, y1, z2), color, width, depth);
        drawLine(new Vec3d(x1, y1, z2), new Vec3d(x1, y1, z1), color, width, depth);
        drawLine(new Vec3d(x1, y1, z1), new Vec3d(x1, y2, z1), color, width, depth);
        drawLine(new Vec3d(x2, y1, z1), new Vec3d(x2, y2, z1), color, width, depth);
        drawLine(new Vec3d(x2, y1, z2), new Vec3d(x2, y2, z2), color, width, depth);
        drawLine(new Vec3d(x1, y1, z2), new Vec3d(x1, y2, z2), color, width, depth);
        drawLine(new Vec3d(x1, y2, z1), new Vec3d(x2, y2, z1), color, width, depth);
        drawLine(new Vec3d(x2, y2, z1), new Vec3d(x2, y2, z2), color, width, depth);
        drawLine(new Vec3d(x2, y2, z2), new Vec3d(x1, y2, z2), color, width, depth);
        drawLine(new Vec3d(x1, y2, z2), new Vec3d(x1, y2, z1), color, width, depth);
    }

    public static void clear() {
        LINES.clear();
        QUADS.clear();
    }

    public static void render(WorldRenderContext context) {
        if ((LINES.isEmpty() && QUADS.isEmpty()) || context == null || context.matrixStack() == null || context.camera() == null)
            return;
        GlUiState savedState = GlUiState.capture();
        MatrixStack matrices = context.matrixStack();
        Vec3d camera = context.camera().getPos();
        lastCameraPos = camera;
        lastCameraRotation.set(context.camera().getRotation());
        List<Quad> quadSnapshot = List.copyOf(QUADS);
        List<GradientLine> lineSnapshot = List.copyOf(LINES);
        QUADS.clear();
        LINES.clear();
        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        try {
            renderQuads(matrix, quadSnapshot, true);
            renderQuads(matrix, quadSnapshot, false);
            renderLines(matrix, lineSnapshot);
        } finally {
            matrices.pop();
            savedState.close();
        }
    }

    private static void renderQuads(Matrix4f matrix, List<Quad> quads, boolean depth) {
        boolean any = false;
        for (Quad q : quads)
            if (q.depth == depth) {
                any = true;
                break;
            }
        if (!any) return;
        if (depth) RenderSystem.enableDepthTest();
        else RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (Quad q : quads) {
            if (q.depth != depth) continue;
            vertex(b, matrix, q.a, q.color);
            vertex(b, matrix, q.b, q.color);
            vertex(b, matrix, q.c, q.color);
            vertex(b, matrix, q.d, q.color);
        }
        BufferRenderer.drawWithGlobalProgram(b.end());
        RenderSystem.depthMask(true);
    }

    private static void renderLines(Matrix4f matrix, List<GradientLine> lines) {
        boolean depthState = true;
        float currentWidth = -1.0F;
        BufferBuilder buffer = null;
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        for (GradientLine line : lines) {
            if (buffer == null || depthState != line.depth || Math.abs(currentWidth - line.width) > 0.001F) {
                if (buffer != null) BufferRenderer.drawWithGlobalProgram(buffer.end());
                depthState = line.depth;
                currentWidth = line.width;
                if (depthState) RenderSystem.enableDepthTest();
                else RenderSystem.disableDepthTest();
                RenderSystem.lineWidth(currentWidth);
                buffer = Tessellator.getInstance().getBuffer();
                buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            }
            vertex(buffer, matrix, line.start, line.startColor);
            vertex(buffer, matrix, line.end, line.endColor);
        }
        if (buffer != null) BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, Vec3d point, int color) {
        buffer.vertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .color(ColorUtil.getRed(color), ColorUtil.getGreen(color), ColorUtil.getBlue(color), ColorUtil.getAlpha(color)).next();
    }

    private static boolean finite(Vec3d value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private record GradientLine(Vec3d start, Vec3d end, int startColor, int endColor, float width, boolean depth) {
    }

    private record Quad(Vec3d a, Vec3d b, Vec3d c, Vec3d d, int color, boolean depth) {
    }
}
