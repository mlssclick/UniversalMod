package universalmod.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.Deque;






public final class Render2DCoordinateSpace {
    private static final float DESIGN_GUI_SCALE = 2.0f;
    private static final Matrix4f FRAME_MATRIX = new Matrix4f();
    private static final Deque<Matrix4f> OVERRIDES = new ArrayDeque<>();

    private Render2DCoordinateSpace() {
    }

    public static void beginFrame(DrawContext graphics) {
        OVERRIDES.clear();
        if (graphics == null) {
            FRAME_MATRIX.identity();
            return;
        }
        FRAME_MATRIX.set(graphics.getMatrices().peek().getPositionMatrix());
        float scale = guiIndependentScale();
        if (Math.abs(scale - 1.0f) > 0.0001f) {
            FRAME_MATRIX.scale(scale, scale, 1.0f);
        }
    }

    public static void endFrame() {
        OVERRIDES.clear();
    }

    public static Matrix4f matrix() {
        Matrix4f override = OVERRIDES.peekLast();
        return override == null ? FRAME_MATRIX : override;
    }

    public static org.joml.Matrix3x2f pose(DrawContext graphics) {
        Matrix4f matrix = matrix();
        return new org.joml.Matrix3x2f(matrix.m00(), matrix.m01(), matrix.m10(), matrix.m11(), matrix.m30(), matrix.m31());
    }

    public static void push(Matrix4f matrix) {
        OVERRIDES.addLast(matrix == null ? new Matrix4f(FRAME_MATRIX) : new Matrix4f(matrix));
    }

    public static void pop() {
        if (!OVERRIDES.isEmpty()) {
            OVERRIDES.removeLast();
        }
    }

    public static void applyGuiScaleIndependence(net.minecraft.client.util.math.MatrixStack matrices) {
        float scale = guiIndependentScale();
        if (matrices != null && Math.abs(scale - 1.0f) > 0.0001f) {
            matrices.scale(scale, scale, 1.0f);
        }
    }

    public static float toGui(float value) {
        return value * guiIndependentScale();
    }

    public static int toGuiInt(float value) {
        return Math.round(toGui(value));
    }

    public static float guiIndependentScale() {
        return DESIGN_GUI_SCALE / actualGuiScale();
    }

    public static float designGuiScale() {
        return DESIGN_GUI_SCALE;
    }

    public static int guiScale() {
        return Math.max(1, Math.round(actualGuiScale()));
    }

    public static float actualGuiScale() {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) {
            return 1.0f;
        }
        return Math.max(1.0f, (float) minecraft.getWindow().getScaleFactor());
    }
}
