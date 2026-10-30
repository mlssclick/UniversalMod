package universalmod.render.glass;

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
import universalmod.render.blur.BlurFramebuffer;
import universalmod.render.blur.BlurPass;
import universalmod.render.ScissorUtil;
import universalmod.render.color.ColorUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class GlassRenderer implements AutoCloseable {
    private static final int MAX_GLASSES = 512;
    private static final int PARAMS_PER_GLASS = 5;
    private static final int FLOATS_PER_PARAM = 4;
    private static final int UNIFORM_BYTES = MAX_GLASSES * PARAMS_PER_GLASS * FLOATS_PER_PARAM * Float.BYTES;

    private static volatile GlassRenderer instance;

    public static final RenderPipeline GLASS_PIPELINE = RenderPipeline.builder()
            .withLocation(id("pipeline/glass"))
            .withVertexShader(id("ui/glass/glass"))
            .withFragmentShader(id("ui/glass/glass"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_LINE_WIDTH, VertexFormat.Mode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withSampler("Sampler0")
            .withUniform("DynamicTransforms", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("GlassParamsArray", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("BlurRegion", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .build();

    private final List<GlassQuad> preparedGlasses = new ArrayList<>(64);
    private GuiGraphics activeGraphics;
    private final GpuUniformBuffer paramsBuffer = new GpuUniformBuffer("UNIVERSALMOD_glass_params", UNIFORM_BYTES);
    private boolean paramsDirty = true;

    private GlassRenderer() {
    }

    public static GlassRenderer getInstance() {
        GlassRenderer local = instance;
        if (local == null) {
            synchronized (GlassRenderer.class) {
                local = instance;
                if (local == null) {
                    local = new GlassRenderer();
                    instance = local;
                }
            }
        }
        return local;
    }

    public static void closeInstance() {
        GlassRenderer local = instance;
        if (local != null) {
            local.close();
            instance = null;
        }
    }

    public void beginFrame(GuiGraphics graphics) {
        activeGraphics = graphics;
    }

    public void draw(GuiGraphics graphics, GlassQuad glass) {
        beginFrame(graphics);
        enqueue(glass);
        flush();
    }

    public void enqueue(GlassQuad glass) {
        submit(activeGraphics, glass);
    }

    public void flush() {
        activeGraphics = null;
    }

    public void beginGuiFrame() {
        preparedGlasses.clear();
        paramsDirty = false;
    }

    public boolean isGlassPipeline(RenderPipeline pipeline) {
        return pipeline == GLASS_PIPELINE;
    }

    public void bindParams(RenderPass renderPass) {
        if (renderPass == null || preparedGlasses.isEmpty()) {
            return;
        }

        prepareBuffers();
        var buffer = paramsBuffer.buffer();
        if (buffer != null) {
            renderPass.setUniform("GlassParamsArray", buffer);
        }
        BlurFramebuffer.getInstance().bindBlurRegion(renderPass);
    }

    public void prepareBuffers() {
        if (preparedGlasses.isEmpty() || !paramsDirty) {
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer uniformData = buildUniformData(stack, preparedGlasses);
            paramsDirty = !paramsBuffer.write(uniformData);
        } catch (RuntimeException failure) {
            paramsDirty = true;
        }
    }

    int reserve(GlassQuad glass) {
        int index = preparedGlasses.size();
        if (index == MAX_GLASSES) {
            return -1;
        }

        preparedGlasses.add(glass);
        paramsDirty = true;
        return index;
    }

    private void submit(GuiGraphics graphics, GlassQuad glass) {
        if (graphics == null || glass == null || !glass.visible()) {
            return;
        }

        try {
            GlassQuad normalized = normalize(glass);
            Matrix3x2f pose = Render2DCoordinateSpace.pose(graphics);
            BlurFramebuffer.getInstance().requestCapture(
                    graphics,
                    new BlurPass(
                            normalized.x(),
                            normalized.y(),
                            normalized.width(),
                            normalized.height(),
                            normalized.radiusTopLeft(),
                            normalized.radiusTopRight(),
                            normalized.radiusBottomRight(),
                            normalized.radiusBottomLeft(),
                            1.0f,
                            30.0f,
                            ColorUtil.WHITE
                    )
            );
            ((GuiGraphicsExtractorAccessor) graphics)
                    .universalmod$getGuiRenderState()
                    .submitGuiElement(new GlassState(pose, normalized, ScissorUtil.current()));
        } catch (RuntimeException failure) {
            
        }
    }

    private ByteBuffer buildUniformData(MemoryStack stack, List<GlassQuad> batch) {
        int usedBytes = Math.max(1, batch.size()) * PARAMS_PER_GLASS * FLOATS_PER_PARAM * Float.BYTES;
        ByteBuffer data = stack.calloc(usedBytes);

        for (int i = 0; i < batch.size(); i++) {
            GlassQuad glass = batch.get(i);
            int offset = i * PARAMS_PER_GLASS * FLOATS_PER_PARAM * Float.BYTES;

            data.putFloat(offset, glass.radiusTopLeft());
            data.putFloat(offset + 4, glass.radiusTopRight());
            data.putFloat(offset + 8, glass.radiusBottomRight());
            data.putFloat(offset + 12, glass.radiusBottomLeft());

            data.putFloat(offset + 16, glass.width());
            data.putFloat(offset + 20, glass.height());
            data.putFloat(offset + 24, 0.5f);
            data.putFloat(offset + 28, Math.max(glass.squirt(), 0.001f));

            data.putFloat(offset + 32, glass.globalAlpha());
            data.putFloat(offset + 36, glass.fresnelPower());
            data.putFloat(offset + 40, glass.baseAlpha());
            data.putFloat(offset + 44, glass.fresnelMix());

            ColorPacker.putRgbaFloat(data, offset + 48, glass.fresnelColor());

            data.putFloat(offset + 64, glass.fresnelInvert() ? 1.0f : 0.0f);
            data.putFloat(offset + 68, glass.distortStrength());
            data.putFloat(offset + 72, glass.z());
            data.putFloat(offset + 76, 0.0f);
        }

        data.position(0);
        return data;
    }

    private GlassQuad normalize(GlassQuad glass) {
        float maxRadius = Math.max(0.0f, Math.min(glass.width(), glass.height()) * 0.5f);
        float radiusTopLeft = clamp(glass.radiusTopLeft(), 0.0f, maxRadius);
        float radiusTopRight = clamp(glass.radiusTopRight(), 0.0f, maxRadius);
        float radiusBottomRight = clamp(glass.radiusBottomRight(), 0.0f, maxRadius);
        float radiusBottomLeft = clamp(glass.radiusBottomLeft(), 0.0f, maxRadius);
        float globalAlpha = clamp(glass.globalAlpha(), 0.0f, 1.0f);
        float fresnelPower = Math.max(glass.fresnelPower(), 0.001f);
        float baseAlpha = clamp(glass.baseAlpha(), 0.0f, 1.0f);
        float fresnelMix = clamp(glass.fresnelMix(), 0.0f, 1.0f);
        float squirt = Math.max(glass.squirt(), 0.001f);

        if (radiusTopLeft == glass.radiusTopLeft()
                && radiusTopRight == glass.radiusTopRight()
                && radiusBottomRight == glass.radiusBottomRight()
                && radiusBottomLeft == glass.radiusBottomLeft()
                && globalAlpha == glass.globalAlpha()
                && fresnelPower == glass.fresnelPower()
                && baseAlpha == glass.baseAlpha()
                && fresnelMix == glass.fresnelMix()
                && squirt == glass.squirt()) {
            return glass;
        }

        return new GlassQuad(
                glass.x(),
                glass.y(),
                glass.width(),
                glass.height(),
                radiusTopLeft,
                radiusTopRight,
                radiusBottomRight,
                radiusBottomLeft,
                glass.color(),
                globalAlpha,
                fresnelPower,
                glass.fresnelColor(),
                baseAlpha,
                glass.fresnelInvert(),
                fresnelMix,
                glass.distortStrength(),
                squirt,
                glass.z()
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
        preparedGlasses.clear();
        activeGraphics = null;
        paramsBuffer.close();
    }
}
