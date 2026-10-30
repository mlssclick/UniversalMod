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

public final class OutlineRenderer implements AutoCloseable {
    private static final int MAX_OUTLINES = 512;
    private static final int PARAMS_PER_OUTLINE = 6;
    private static final int FLOATS_PER_PARAM = 4;
    private static final int UNIFORM_BYTES = MAX_OUTLINES * PARAMS_PER_OUTLINE * FLOATS_PER_PARAM * Float.BYTES;

    private static volatile OutlineRenderer instance;

    public static final RenderPipeline OUTLINE_PIPELINE = RenderPipeline.builder()
            .withLocation(id("pipeline/outline_default"))
            .withVertexShader(id("ui/shared/outline_quad"))
            .withFragmentShader(id("ui/outline_default/outline_default"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_LINE_WIDTH, VertexFormat.Mode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withUniform("DynamicTransforms", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("OutlineParamsArray", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .build();

    private final List<OutlineShape> preparedOutlines = new ArrayList<>(128);
    private GuiGraphics activeGraphics;
    private final GpuUniformBuffer paramsBuffer = new GpuUniformBuffer("UNIVERSALMOD_outline_default_params", UNIFORM_BYTES);
    private boolean paramsDirty = true;

    private OutlineRenderer() {
    }

    public static OutlineRenderer getInstance() {
        OutlineRenderer local = instance;
        if (local == null) {
            synchronized (OutlineRenderer.class) {
                local = instance;
                if (local == null) {
                    local = new OutlineRenderer();
                    instance = local;
                }
            }
        }
        return local;
    }

    public static void closeInstance() {
        OutlineRenderer local = instance;
        if (local != null) {
            local.close();
            instance = null;
        }
    }

    public void beginFrame(GuiGraphics graphics) {
        activeGraphics = graphics;
    }

    public void draw(GuiGraphics graphics, OutlineShape outline) {
        beginFrame(graphics);
        enqueue(outline);
        flush();
    }

    public void enqueue(OutlineShape outline) {
        submit(activeGraphics, outline);
    }

    public void flush() {
        activeGraphics = null;
    }

    public void beginGuiFrame() {
        preparedOutlines.clear();
        paramsDirty = false;
    }

    public boolean isOutlinePipeline(RenderPipeline pipeline) {
        return pipeline == OUTLINE_PIPELINE;
    }

    public void bindParams(RenderPass renderPass) {
        if (renderPass == null || preparedOutlines.isEmpty()) {
            return;
        }

        prepareBuffers();
        var buffer = paramsBuffer.buffer();
        if (buffer != null) {
            renderPass.setUniform("OutlineParamsArray", buffer);
        }
    }

    public void prepareBuffers() {
        if (preparedOutlines.isEmpty() || !paramsDirty) {
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer uniformData = buildUniformData(stack, preparedOutlines);
            paramsDirty = !paramsBuffer.write(uniformData);
        } catch (RuntimeException failure) {
            paramsDirty = true;
        }
    }

    int reserve(OutlineShape outline) {
        int index = preparedOutlines.size();
        if (index == MAX_OUTLINES) {
            return -1;
        }

        preparedOutlines.add(outline);
        paramsDirty = true;
        return index;
    }

    private void submit(GuiGraphics graphics, OutlineShape outline) {
        if (graphics == null || outline == null || !outline.visible()) {
            return;
        }

        try {
            OutlineShape normalized = normalize(outline);
            Matrix3x2f pose = Render2DCoordinateSpace.pose(graphics);
            ((GuiGraphicsExtractorAccessor) graphics)
                    .universalmod$getGuiRenderState()
                    .submitGuiElement(new OutlineState(pose, normalized, ScissorUtil.current()));
        } catch (RuntimeException failure) {
            
        }
    }

    private ByteBuffer buildUniformData(MemoryStack stack, List<OutlineShape> batch) {
        int usedBytes = Math.max(1, batch.size()) * PARAMS_PER_OUTLINE * FLOATS_PER_PARAM * Float.BYTES;
        ByteBuffer data = stack.calloc(usedBytes);

        for (int i = 0; i < batch.size(); i++) {
            OutlineShape outline = batch.get(i);
            int offset = i * PARAMS_PER_OUTLINE * FLOATS_PER_PARAM * Float.BYTES;

            data.putFloat(offset, outline.radiusTopLeft());
            data.putFloat(offset + 4, outline.radiusTopRight());
            data.putFloat(offset + 8, outline.radiusBottomRight());
            data.putFloat(offset + 12, outline.radiusBottomLeft());

            data.putFloat(offset + 16, outline.width());
            data.putFloat(offset + 20, outline.height());
            data.putFloat(offset + 24, outline.thickness());
            data.putFloat(offset + 28, outline.smoothness());

            ColorPacker.putRgbaFloat(data, offset + 32, outline.colorTopLeft());
            ColorPacker.putRgbaFloat(data, offset + 48, outline.colorTopRight());
            ColorPacker.putRgbaFloat(data, offset + 64, outline.colorBottomRight());
            ColorPacker.putRgbaFloat(data, offset + 80, outline.colorBottomLeft());
        }

        data.position(0);
        return data;
    }

    private OutlineShape normalize(OutlineShape outline) {
        float maxRadius = Math.max(0.0f, Math.min(outline.width(), outline.height()) * 0.5f);
        float maxThickness = Math.max(0.0f, Math.min(outline.width(), outline.height()) * 0.5f);
        float radiusTopLeft = clamp(outline.radiusTopLeft(), 0.0f, maxRadius);
        float radiusTopRight = clamp(outline.radiusTopRight(), 0.0f, maxRadius);
        float radiusBottomRight = clamp(outline.radiusBottomRight(), 0.0f, maxRadius);
        float radiusBottomLeft = clamp(outline.radiusBottomLeft(), 0.0f, maxRadius);
        float thickness = clamp(outline.thickness(), 0.0f, maxThickness);
        float smoothness = Math.max(outline.smoothness(), 0.5f);

        if (radiusTopLeft == outline.radiusTopLeft()
                && radiusTopRight == outline.radiusTopRight()
                && radiusBottomRight == outline.radiusBottomRight()
                && radiusBottomLeft == outline.radiusBottomLeft()
                && thickness == outline.thickness()
                && smoothness == outline.smoothness()) {
            return outline;
        }

        return new OutlineShape(
                outline.x(),
                outline.y(),
                outline.width(),
                outline.height(),
                radiusTopLeft,
                radiusTopRight,
                radiusBottomRight,
                radiusBottomLeft,
                thickness,
                outline.colorTopLeft(),
                outline.colorTopRight(),
                outline.colorBottomRight(),
                outline.colorBottomLeft(),
                smoothness
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
        preparedOutlines.clear();
        activeGraphics = null;
        paramsBuffer.close();
    }
}
