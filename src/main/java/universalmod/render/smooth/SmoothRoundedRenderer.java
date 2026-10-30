package universalmod.render.smooth;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import universalmod.mixin.accessor.GuiGraphicsExtractorAccessor;
import universalmod.render.Render2DCoordinateSpace;
import universalmod.render.ScissorUtil;

public final class SmoothRoundedRenderer implements AutoCloseable {
    private static final VertexFormat SMOOTH_VERTEX_FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("Color", VertexFormatElement.COLOR)
            .add("UV0", VertexFormatElement.UV0)
            .add("UV1", VertexFormatElement.UV1)
            .add("UV2", VertexFormatElement.UV2)
            .add("Normal", VertexFormatElement.NORMAL)
            .add("LineWidth", VertexFormatElement.LINE_WIDTH)
            .padding(1)
            .build();

    public static final RenderPipeline PIPELINE = RenderPipelines.register(RenderPipeline.builder()
            .withLocation(id("pipeline/smooth_rounded"))
            .withVertexShader(id("ui/smooth_rounded/rounded"))
            .withFragmentShader(id("ui/smooth_rounded/rounded"))
            .withUniform("DynamicTransforms", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(SMOOTH_VERTEX_FORMAT, VertexFormat.Mode.QUADS)
            .build());

    private static volatile SmoothRoundedRenderer instance;
    private GuiGraphics activeGraphics;

    private SmoothRoundedRenderer() {
    }

    public static SmoothRoundedRenderer getInstance() {
        SmoothRoundedRenderer local = instance;
        if (local == null) {
            synchronized (SmoothRoundedRenderer.class) {
                local = instance;
                if (local == null) {
                    local = new SmoothRoundedRenderer();
                    instance = local;
                }
            }
        }
        return local;
    }

    public static void closeInstance() {
        SmoothRoundedRenderer local = instance;
        if (local != null) {
            local.close();
            instance = null;
        }
    }

    public void beginFrame(GuiGraphics graphics) {
        activeGraphics = graphics;
    }

    public void flush() {
        activeGraphics = null;
    }

    public void enqueue(SmoothRoundedShape shape) {
        submit(activeGraphics, shape);
    }

    private void submit(GuiGraphics graphics, SmoothRoundedShape shape) {
        if (graphics == null || shape == null || !shape.visible()) {
            return;
        }
        try {
            Matrix3x2f pose = Render2DCoordinateSpace.pose(graphics);
            ((GuiGraphicsExtractorAccessor) graphics)
                    .universalmod$getGuiRenderState()
                    .submitGuiElement(new SmoothRoundedState(pose, shape, ScissorUtil.current()));
        } catch (RuntimeException failure) {
            
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("universalmod", path);
    }

    @Override
    public void close() {
        activeGraphics = null;
    }
}
