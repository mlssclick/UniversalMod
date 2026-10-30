package universalmod.render.arc;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.lwjgl.system.MemoryStack;
import universalmod.mixin.accessor.GuiGraphicsExtractorAccessor;
import universalmod.render.ScissorUtil;
import universalmod.render.Render2DCoordinateSpace;
import universalmod.render.gpu.GpuUniformBuffer;
import universalmod.render.gpu.ColorPacker;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class UiArcRenderer implements AutoCloseable {
    private static final int MAX_ARCS = 64;
    private static final int PARAMS_PER_ARC = 11;
    private static final int FLOATS_PER_PARAM = 4;
    private static final int UNIFORM_BYTES = MAX_ARCS * PARAMS_PER_ARC * FLOATS_PER_PARAM * Float.BYTES;
    private static volatile UiArcRenderer instance;

    public static final RenderPipeline PIPELINE = RenderPipelines.register(RenderPipeline.builder()
            .withLocation(id("pipeline/ui_cooldown_arc"))
            .withVertexShader(id("ui/arcs/arc_quad"))
            .withFragmentShader(id("ui/arcs/arc"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_LINE_WIDTH, VertexFormat.Mode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("UiArcParams", UniformType.UNIFORM_BUFFER)
            .build());

    private final List<UiArc> prepared = new ArrayList<>(8);
    private GuiGraphics graphics;
    private final GpuUniformBuffer paramsBuffer = new GpuUniformBuffer("UNIVERSALMOD_ui_arc_params", UNIFORM_BYTES);
    private boolean dirty = true;

    private UiArcRenderer() {
    }

    public static void closeInstance() {
        UiArcRenderer local = instance;
        if (local != null) {
            local.close();
            instance = null;
        }
    }

    public static UiArcRenderer getInstance() {
        UiArcRenderer local = instance;
        if (local == null) {
            synchronized (UiArcRenderer.class) {
                local = instance;
                if (local == null) {
                    instance = local = new UiArcRenderer();
                }
            }
        }
        return local;
    }

    public void beginFrame(GuiGraphics graphics) {
        this.graphics = graphics;
    }

    public void flush() {
        graphics = null;
    }

    public void beginGuiFrame() {
        prepared.clear();
        dirty = false;
    }

    public void enqueue(UiArc arc) {
        if (graphics == null || arc == null || !arc.visible()) {
            return;
        }
        UiArc normalized = normalize(arc);
        Matrix3x2f pose = Render2DCoordinateSpace.pose(graphics);
        ((GuiGraphicsExtractorAccessor) graphics).universalmod$getGuiRenderState()
                .submitGuiElement(new UiArcState(pose, normalized, ScissorUtil.current()));
    }

    int reserve(UiArc arc) {
        int index = prepared.size();
        if (index >= MAX_ARCS) {
            return -1;
        }
        prepared.add(arc);
        dirty = true;
        return index;
    }

    public boolean isPipeline(RenderPipeline pipeline) {
        return pipeline == PIPELINE;
    }

    public void bindParams(RenderPass pass) {
        if (pass == null || prepared.isEmpty()) {
            return;
        }
        GpuBuffer current = ensureBuffer();
        if (current != null) {
            pass.setUniform("UiArcParams", current);
        }
    }

    public void prepareBuffers() {
        if (prepared.isEmpty() || !dirty) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = buildUniformData(stack);
            dirty = !paramsBuffer.write(data);
        }
    }

    private GpuBuffer ensureBuffer() {
        if (dirty) {
            prepareBuffers();
        }
        if (dirty || paramsBuffer.degraded()) {
            return null;
        }
        return paramsBuffer.buffer();
    }

    private ByteBuffer buildUniformData(MemoryStack stack) {
        int usedBytes = Math.max(1, prepared.size()) * PARAMS_PER_ARC * FLOATS_PER_PARAM * Float.BYTES;
        ByteBuffer data = stack.calloc(usedBytes);
        for (int i = 0; i < prepared.size(); i++) {
            UiArc arc = prepared.get(i);
            int offset = i * PARAMS_PER_ARC * FLOATS_PER_PARAM * Float.BYTES;
            data.putFloat(offset, arc.size());
            data.putFloat(offset + 4, arc.thickness());
            data.putFloat(offset + 8, arc.degree());
            data.putFloat(offset + 12, arc.rotation());
            data.putFloat(offset + 16, arc.blurRadius());
            int[] colors = arc.colors();
            for (int colorIndex = 0; colorIndex < 9; colorIndex++) {
                putColor(data, offset + (colorIndex + 2) * 16, colors[colorIndex]);
            }
        }
        data.position(0);
        return data;
    }

    private static UiArc normalize(UiArc arc) {
        float size = Math.max(0.0F, arc.size());
        return new UiArc(
                arc.x(),
                arc.y(),
                size,
                Math.max(0.0F, Math.min(arc.thickness(), size)),
                Math.max(0.0F, Math.min(arc.degree(), 360.0F)),
                arc.rotation(),
                Math.max(0.0F, Math.min(arc.blurRadius(), size * 0.25F)),
                arc.colors()
        );
    }

    private static void putColor(ByteBuffer data, int offset, int color) {
        ColorPacker.putRgbaFloat(data, offset, color);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("universalmod", path);
    }

    @Override
    public void close() {
        prepared.clear();
        graphics = null;
        paramsBuffer.close();
    }


}
