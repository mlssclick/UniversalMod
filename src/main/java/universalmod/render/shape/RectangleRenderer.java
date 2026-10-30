package universalmod.render.shape;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import universalmod.mixin.accessor.GuiGraphicsExtractorAccessor;
import universalmod.render.Render2DCoordinateSpace;
import universalmod.render.gpu.GpuUniformBuffer;
import universalmod.render.gpu.ColorPacker;
import universalmod.render.ScissorUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class RectangleRenderer implements AutoCloseable {
    private static final int MAX_RECTANGLES = 512;
    private static final int PARAMS_PER_RECTANGLE = 7;
    private static final int FLOATS_PER_PARAM = 4;
    private static final int UNIFORM_BYTES = MAX_RECTANGLES * PARAMS_PER_RECTANGLE * FLOATS_PER_PARAM * Float.BYTES;

    private static volatile RectangleRenderer instance;

    public static final RenderPipeline RECTANGLE_PIPELINE = RenderPipeline.builder()
            .withLocation(id("pipeline/rect_default"))
            .withVertexShader(id("ui/shared/rect_quad"))
            .withFragmentShader(id("ui/rect_default/rect_default"))
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withUniform("DynamicTransforms", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("RectangleParamsArray", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .build();

    private final List<RectangleShape> preparedRectangles = new ArrayList<>(128);
    private GuiGraphics activeGraphics;
    private final GpuUniformBuffer paramsBuffer = new GpuUniformBuffer("UNIVERSALMOD_rect_default_params", UNIFORM_BYTES);
    private boolean paramsDirty = true;

    private RectangleRenderer() {
    }

    public static RectangleRenderer getInstance() {
        RectangleRenderer local = instance;
        if (local == null) {
            synchronized (RectangleRenderer.class) {
                local = instance;
                if (local == null) {
                    local = new RectangleRenderer();
                    instance = local;
                }
            }
        }
        return local;
    }

    public static void closeInstance() {
        RectangleRenderer local = instance;
        if (local != null) {
            local.close();
            instance = null;
        }
    }

    public void beginFrame(GuiGraphics graphics) {
        activeGraphics = graphics;
    }

    public void draw(GuiGraphics graphics, RectangleShape rectangle) {
        beginFrame(graphics);
        enqueue(rectangle);
        flush();
    }

    public void enqueue(RectangleShape rectangle) {
        submit(activeGraphics, rectangle);
    }

    public void flush() {
        activeGraphics = null;
    }

    public void beginGuiFrame() {
        preparedRectangles.clear();
        paramsDirty = false;
    }

    public boolean isRectanglePipeline(RenderPipeline pipeline) {
        return pipeline == RECTANGLE_PIPELINE;
    }

    public void bindParams(RenderPass renderPass) {
        if (renderPass == null || preparedRectangles.isEmpty()) {
            return;
        }

        prepareBuffers();
        var buffer = paramsBuffer.buffer();
        if (buffer != null) {
            renderPass.setUniform("RectangleParamsArray", buffer);
        }
    }

    public void prepareBuffers() {
        if (preparedRectangles.isEmpty() || !paramsDirty) {
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer uniformData = buildUniformData(stack, preparedRectangles);
            paramsDirty = !paramsBuffer.write(uniformData);
        } catch (RuntimeException failure) {
            paramsDirty = true;
        }
    }

    boolean reserve(RectangleShape rectangle) {
        if (preparedRectangles.size() == MAX_RECTANGLES) {
            return false;
        }

        preparedRectangles.add(rectangle);
        paramsDirty = true;
        return true;
    }

    private void submit(GuiGraphics graphics, RectangleShape rectangle) {
        if (graphics == null || rectangle == null || !rectangle.visible()) {
            return;
        }

        try {
            RectangleShape normalized = normalize(rectangle);
            Matrix3x2f pose = Render2DCoordinateSpace.pose(graphics);
            ((GuiGraphicsExtractorAccessor) graphics)
                    .universalmod$getGuiRenderState()
                    .submitGuiElement(new RectangleState(pose, normalized, ScissorUtil.current()));
        } catch (RuntimeException failure) {
            
        }
    }

    private ByteBuffer buildUniformData(MemoryStack stack, List<RectangleShape> batch) {
        int usedBytes = Math.max(1, batch.size()) * PARAMS_PER_RECTANGLE * FLOATS_PER_PARAM * Float.BYTES;
        ByteBuffer data = stack.calloc(usedBytes);

        for (int i = 0; i < batch.size(); i++) {
            RectangleShape rectangle = batch.get(i);
            int offset = i * PARAMS_PER_RECTANGLE * FLOATS_PER_PARAM * Float.BYTES;

            data.putFloat(offset, rectangle.radiusTopLeft());
            data.putFloat(offset + 4, rectangle.radiusTopRight());
            data.putFloat(offset + 8, rectangle.radiusBottomRight());
            data.putFloat(offset + 12, rectangle.radiusBottomLeft());

            data.putFloat(offset + 16, rectangle.width());
            data.putFloat(offset + 20, rectangle.height());
            data.putFloat(offset + 24, rectangle.smoothness());
            data.putFloat(offset + 28, rectangle.verticalSplit());

            ColorPacker.putRgbaFloat(data, offset + 32, rectangle.colorTopLeft());
            ColorPacker.putRgbaFloat(data, offset + 48, rectangle.colorTopRight());
            ColorPacker.putRgbaFloat(data, offset + 64, rectangle.colorBottomRight());
            ColorPacker.putRgbaFloat(data, offset + 80, rectangle.colorBottomLeft());
            ColorPacker.putRgbaFloat(data, offset + 96, rectangle.splitColor());
        }

        data.position(0);
        return data;
    }

    private RectangleShape normalize(RectangleShape rectangle) {
        float maxRadius = Math.max(0.0f, Math.min(rectangle.width(), rectangle.height()) * 0.5f);
        float radiusTopLeft = clamp(rectangle.radiusTopLeft(), 0.0f, maxRadius);
        float radiusTopRight = clamp(rectangle.radiusTopRight(), 0.0f, maxRadius);
        float radiusBottomRight = clamp(rectangle.radiusBottomRight(), 0.0f, maxRadius);
        float radiusBottomLeft = clamp(rectangle.radiusBottomLeft(), 0.0f, maxRadius);
        float smoothness = Math.max(rectangle.smoothness(), 0.5f);
        float verticalSplit = rectangle.verticalSplit();
        if (!Float.isFinite(verticalSplit) || verticalSplit < 0.0f) {
            verticalSplit = RectangleShape.NO_VERTICAL_SPLIT;
        } else {
            verticalSplit = clamp(verticalSplit, 0.0f, Math.max(rectangle.height(), 0.0f));
        }

        if (radiusTopLeft == rectangle.radiusTopLeft()
                && radiusTopRight == rectangle.radiusTopRight()
                && radiusBottomRight == rectangle.radiusBottomRight()
                && radiusBottomLeft == rectangle.radiusBottomLeft()
                && smoothness == rectangle.smoothness()
                && verticalSplit == rectangle.verticalSplit()) {
            return rectangle;
        }

        return new RectangleShape(
                rectangle.x(),
                rectangle.y(),
                rectangle.width(),
                rectangle.height(),
                radiusTopLeft,
                radiusTopRight,
                radiusBottomRight,
                radiusBottomLeft,
                rectangle.colorTopLeft(),
                rectangle.colorTopRight(),
                rectangle.colorBottomRight(),
                rectangle.colorBottomLeft(),
                smoothness,
                verticalSplit,
                rectangle.splitColor()
        );
    }


    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }


    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("universalmod", path);
    }

    @Override
    public void close() {
        preparedRectangles.clear();
        activeGraphics = null;
        paramsBuffer.close();
    }
}
