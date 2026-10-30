package universalmod.render.surface;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.lwjgl.system.MemoryStack;
import universalmod.mixin.accessor.GuiGraphicsExtractorAccessor;
import universalmod.render.Render2DCoordinateSpace;
import universalmod.render.ScissorUtil;
import universalmod.render.gpu.GpuUniformBuffer;
import universalmod.render.color.ColorUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;


public final class GuiSurfaceRenderer implements AutoCloseable {
    private static final int MAX = 768;
    private static final int PARAMS_PER_SURFACE = 4;
    private static final int UNIFORM_BYTES = MAX * PARAMS_PER_SURFACE * 4 * Float.BYTES;
    private static volatile GuiSurfaceRenderer instance;

    public static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(id("pipeline/gui_surface"))
            .withVertexShader(id("ui/gui_surface/gui_surface"))
            .withFragmentShader(id("ui/gui_surface/gui_surface"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_LINE_WIDTH, VertexFormat.Mode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withUniform("DynamicTransforms", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("GuiSurfaceParams", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .build();

    private final List<GuiSurface> prepared = new ArrayList<>(128);
    private final GpuUniformBuffer paramsBuffer = new GpuUniformBuffer("UNIVERSALMOD_gui_surface_params", UNIFORM_BYTES);
    private boolean paramsDirty = true;

    private GuiSurfaceRenderer() {}

    public static GuiSurfaceRenderer getInstance() {
        GuiSurfaceRenderer local = instance;
        if (local == null) {
            synchronized (GuiSurfaceRenderer.class) {
                local = instance;
                if (local == null) instance = local = new GuiSurfaceRenderer();
            }
        }
        return local;
    }

    public static void closeInstance() {
        GuiSurfaceRenderer local = instance;
        if (local != null) {
            local.close();
            instance = null;
        }
    }

    public void beginGuiFrame() {
        prepared.clear();
        paramsDirty = false;
    }

    public boolean isPipeline(RenderPipeline pipeline) {
        return pipeline == PIPELINE;
    }

    public void draw(GuiGraphics graphics, GuiSurface surface) {
        if (graphics == null || surface == null || !surface.visible()) return;
        float maxRadius = Math.max(0.0f, Math.min(surface.width(), surface.height()) * 0.5f);
        GuiSurface normalized = new GuiSurface(
                surface.x(), surface.y(), surface.width(), surface.height(),
                clamp(surface.radiusTopLeft(), 0.0f, maxRadius),
                clamp(surface.radiusTopRight(), 0.0f, maxRadius),
                clamp(surface.radiusBottomRight(), 0.0f, maxRadius),
                clamp(surface.radiusBottomLeft(), 0.0f, maxRadius),
                clamp(surface.alpha(), 0.0f, 1.0f),
                Math.max(0.45f, surface.smoothness()),
                clamp(surface.accentStrength(), 0.0f, 1.0f),
                surface.baseColor(), surface.accentColor()
        );
        Matrix3x2f pose = Render2DCoordinateSpace.pose(graphics);
        ((GuiGraphicsExtractorAccessor) graphics).universalmod$getGuiRenderState()
                .submitGuiElement(new GuiSurfaceState(pose, normalized, ScissorUtil.current()));
    }

    int reserve(GuiSurface surface) {
        if (prepared.size() >= MAX) return -1;
        int index = prepared.size();
        prepared.add(surface);
        paramsDirty = true;
        return index;
    }

    public void prepareBuffers() {
        if (prepared.isEmpty() || !paramsDirty) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = buildData(stack);
            paramsDirty = !paramsBuffer.write(data);
        }
    }

    public void bindParams(RenderPass pass) {
        if (pass == null || prepared.isEmpty()) {
            return;
        }
        GpuBuffer buffer = ensureBuffer();
        if (buffer != null) {
            pass.setUniform("GuiSurfaceParams", buffer);
        }
    }

    private GpuBuffer ensureBuffer() {
        if (paramsDirty) {
            prepareBuffers();
        }
        if (paramsDirty || paramsBuffer.degraded()) {
            return null;
        }
        return paramsBuffer.buffer();
    }

    private ByteBuffer buildData(MemoryStack stack) {
        ByteBuffer data = stack.calloc(Math.max(1, prepared.size()) * PARAMS_PER_SURFACE * 4 * Float.BYTES);
        for (int i = 0; i < prepared.size(); i++) {
            GuiSurface s = prepared.get(i);
            int o = i * PARAMS_PER_SURFACE * 4 * Float.BYTES;
            data.putFloat(o, s.radiusTopLeft());
            data.putFloat(o + 4, s.radiusTopRight());
            data.putFloat(o + 8, s.radiusBottomRight());
            data.putFloat(o + 12, s.radiusBottomLeft());
            data.putFloat(o + 16, s.width());
            data.putFloat(o + 20, s.height());
            data.putFloat(o + 24, s.alpha());
            data.putFloat(o + 28, s.smoothness());
            putColor(data, o + 32, s.baseColor());
            data.putFloat(o + 44, s.accentStrength());
            putColor(data, o + 48, s.accentColor());
        }
        data.position(0);
        return data;
    }

    private static void putColor(ByteBuffer data, int offset, int color) {
        data.putFloat(offset, ColorUtil.getRed(color) / 255.0f);
        data.putFloat(offset + 4, ColorUtil.getGreen(color) / 255.0f);
        data.putFloat(offset + 8, ColorUtil.getBlue(color) / 255.0f);
        data.putFloat(offset + 12, ColorUtil.getAlpha(color) / 255.0f);
    }


    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("universalmod", path);
    }

    @Override
    public void close() {
        prepared.clear();
        paramsBuffer.close();
    }
}
