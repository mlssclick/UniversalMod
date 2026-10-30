package universalmod.render.hud;

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
import universalmod.render.ScissorUtil;
import universalmod.render.gpu.GpuUniformBuffer;
import universalmod.render.Render2DCoordinateSpace;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;


public final class HudSurfaceRenderer implements AutoCloseable {
    private static final int MAX = 512;
    private static final int PARAMS_PER_PANEL = 3;
    private static final int UNIFORM_BYTES = MAX * PARAMS_PER_PANEL * 4 * Float.BYTES;
    private static volatile HudSurfaceRenderer instance;

    public static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(id("pipeline/hud_surface"))
            .withVertexShader(id("ui/hud_surface/hud_surface"))
            .withFragmentShader(id("ui/hud_surface/hud_surface"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_LINE_WIDTH, VertexFormat.Mode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withUniform("DynamicTransforms", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("HudSurfaceParams", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .build();

    private final List<HudSurface> prepared = new ArrayList<>(96);
    private GuiGraphics activeGraphics;
    private final GpuUniformBuffer paramsBuffer = new GpuUniformBuffer("UNIVERSALMOD_hud_surface_params", UNIFORM_BYTES);
    private boolean paramsDirty = true;

    private HudSurfaceRenderer() {
    }

    public static HudSurfaceRenderer getInstance() {
        HudSurfaceRenderer local = instance;
        if (local == null) {
            synchronized (HudSurfaceRenderer.class) {
                local = instance;
                if (local == null) {
                    instance = local = new HudSurfaceRenderer();
                }
            }
        }
        return local;
    }

    public static void closeInstance() {
        HudSurfaceRenderer local = instance;
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

    public void beginGuiFrame() {
        prepared.clear();
        paramsDirty = false;
    }

    public void draw(GuiGraphics graphics, HudSurface panel) {
        beginFrame(graphics);
        enqueue(panel);
        flush();
    }

    public void enqueue(HudSurface panel) {
        if (activeGraphics == null || panel == null || !panel.visible()) {
            return;
        }
        float maxRadius = Math.max(0.0f, Math.min(panel.width(), panel.height()) * 0.5f);
        float rtl = clamp(panel.radiusTopLeft(), 0.0f, maxRadius);
        float rtr = clamp(panel.radiusTopRight(), 0.0f, maxRadius);
        float rbr = clamp(panel.radiusBottomRight(), 0.0f, maxRadius);
        float rbl = clamp(panel.radiusBottomLeft(), 0.0f, maxRadius);
        float alpha = clamp(panel.alpha(), 0.0f, 1.0f);
        float smoothness = Math.max(0.45f, panel.smoothness());
        float darkness = clamp(panel.darkness(), 0.0f, 1.0f);
        HudSurface normalized = rtl == panel.radiusTopLeft()
                && rtr == panel.radiusTopRight()
                && rbr == panel.radiusBottomRight()
                && rbl == panel.radiusBottomLeft()
                && alpha == panel.alpha()
                && smoothness == panel.smoothness()
                && darkness == panel.darkness()
                ? panel
                : new HudSurface(
                        panel.x(), panel.y(), panel.width(), panel.height(),
                        rtl, rtr, rbr, rbl, alpha, smoothness, darkness
                );
        Matrix3x2f pose = Render2DCoordinateSpace.pose(activeGraphics);
        ((GuiGraphicsExtractorAccessor) activeGraphics).universalmod$getGuiRenderState()
                .submitGuiElement(new HudSurfaceState(pose, normalized, ScissorUtil.current()));
    }

    int reserve(HudSurface panel) {
        if (prepared.size() >= MAX) {
            return -1;
        }
        int index = prepared.size();
        prepared.add(panel);
        paramsDirty = true;
        return index;
    }

    public boolean isPipeline(RenderPipeline pipeline) {
        return pipeline == PIPELINE;
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
            pass.setUniform("HudSurfaceParams", buffer);
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
        ByteBuffer data = stack.calloc(Math.max(1, prepared.size()) * PARAMS_PER_PANEL * 4 * Float.BYTES);
        for (int i = 0; i < prepared.size(); i++) {
            HudSurface panel = prepared.get(i);
            int offset = i * PARAMS_PER_PANEL * 4 * Float.BYTES;
            data.putFloat(offset, panel.radiusTopLeft());
            data.putFloat(offset + 4, panel.radiusTopRight());
            data.putFloat(offset + 8, panel.radiusBottomRight());
            data.putFloat(offset + 12, panel.radiusBottomLeft());
            data.putFloat(offset + 16, panel.width());
            data.putFloat(offset + 20, panel.height());
            data.putFloat(offset + 24, panel.alpha());
            data.putFloat(offset + 28, panel.smoothness());
            data.putFloat(offset + 32, panel.darkness());
        }
        data.position(0);
        return data;
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
        activeGraphics = null;
        paramsBuffer.close();
    }
}
