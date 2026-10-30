package universalmod.render.blur;


import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import universalmod.mixin.accessor.GuiGraphicsExtractorAccessor;
import universalmod.render.color.ColorUtil;
import universalmod.render.Render2DCoordinateSpace;
import universalmod.render.ScissorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import org.joml.Matrix3x2f;
import org.lwjgl.system.MemoryStack;
import universalmod.gui.clickgui.ClickGui;


import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class BlurFramebuffer implements AutoCloseable {
    private static final int MAX_BLUR_RECTS = 1024;
    private static final int MAX_BLUR_ITERATIONS = 4;
    private static final int PARAMS_PER_RECT = 3;
    private static final int FLOATS_PER_PARAM = 4;
    private static final int UNIFORM_BYTES = MAX_BLUR_RECTS * PARAMS_PER_RECT * FLOATS_PER_PARAM * Float.BYTES;
    private static final int REGION_UNIFORM_BYTES = 4 * Float.BYTES;
    private static final int KAWASE_UNIFORM_BYTES = 12 * Float.BYTES;
    private static final float MAX_BLUR_RENDER_SCALE = 0.5f;
    private static final float MEDIUM_BLUR_RENDER_SCALE = 0.44f;
    private static final float MIN_BLUR_RENDER_SCALE = 0.4f;

    private static volatile BlurFramebuffer instance;
    private static float skyFallbackRed = 0.55f;
    private static float skyFallbackGreen = 0.65f;
    private static float skyFallbackBlue = 0.78f;

    public static final RenderPipeline BATCHED_BLUR_PIPELINE = BlurPipelines.BATCHED;
    public static final RenderPipeline KAWASE_DOWN_PIPELINE = BlurPipelines.KAWASE_DOWN;
    public static final RenderPipeline KAWASE_UP_PIPELINE = BlurPipelines.KAWASE_UP;
    public static final RenderPipeline SCOREBOARD_CROP_PIPELINE = BlurPipelines.SCOREBOARD_CROP;
    public static final RenderPipeline SCOREBOARD_SEPARABLE_PIPELINE = BlurPipelines.SCOREBOARD_SEPARABLE;

    private final List<BlurCaptureRequest> pendingBlurs = new ArrayList<>(32);
    private final List<BlurPass> preparedBlurs = new ArrayList<>(32);
    private final ScoreboardBlurChain scoreboardBlur = new ScoreboardBlurChain();
    private GuiGraphics activeGraphics;
    private final BlurTargetPool targets = new BlurTargetPool(MAX_BLUR_ITERATIONS);
    private GpuBuffer fullscreenQuadBuffer;
    private GpuBuffer paramsBuffer;
    private GpuBuffer regionBuffer;
    private GpuBuffer kawaseParamsBuffer;
    private TextureSetup blurTextureSetup = TextureSetup.noTexture();
    private boolean captureRequested;
    private boolean kawaseCaptureRequested;
    private boolean scoreboardCaptureRequested;
    private boolean paramsDirty = true;
    private boolean regionDirty = true;
    private int regionX;
    private int regionY;
    private int regionWidth;
    private int regionHeight;
    private int preparedIterations;
    private float maxBlurRadius;
    private float maxKawaseBlurRadius;
    private BlurFramebuffer() {
    }

    public static BlurFramebuffer getInstance() {
        BlurFramebuffer local = instance;
        if (local == null) {
            synchronized (BlurFramebuffer.class) {
                local = instance;
                if (local == null) {
                    local = new BlurFramebuffer();
                    instance = local;
                }
            }
        }
        return local;
    }

    public static void closeInstance() {
        BlurFramebuffer local = instance;
        if (local != null) {
            local.close();
            instance = null;
        }
    }

    public static void setSkyFallbackColor(float red, float green, float blue) {
        if (!Float.isFinite(red) || !Float.isFinite(green) || !Float.isFinite(blue)) {
            return;
        }

        skyFallbackRed = clamp(red, 0.0f, 1.0f);
        skyFallbackGreen = clamp(green, 0.0f, 1.0f);
        skyFallbackBlue = clamp(blue, 0.0f, 1.0f);
    }

    public void beginFrame(GuiGraphics graphics) {
        activeGraphics = graphics;
    }

    public void draw(GuiGraphics graphics, BlurPass blur) {
        beginFrame(graphics);
        enqueue(blur);
        flush();
    }

    public void enqueue(BlurPass blur) {
        submit(activeGraphics, blur);
    }

    public void flush() {
        activeGraphics = null;
    }

    public void beginGuiFrame() {
        preparedBlurs.clear();
        captureRequested = false;
        kawaseCaptureRequested = false;
        scoreboardCaptureRequested = false;
        paramsDirty = false;
        regionDirty = false;
        regionWidth = 0;
        regionHeight = 0;
        preparedIterations = 0;
        maxBlurRadius = 0.0f;
        maxKawaseBlurRadius = 0.0f;
        blurTextureSetup = TextureSetup.noTexture();
        scoreboardBlur.beginFrame();
    }

    public void preparePending() {
        if (pendingBlurs.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            pendingBlurs.clear();
            return;
        }

        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        if (mainTarget == null || mainTarget.getColorTexture() == null || !computeBlurRegion(minecraft, mainTarget)) {
            pendingBlurs.clear();
            return;
        }

        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        if (kawaseCaptureRequested) {
            preparedIterations = BlurGeometry.iterations(maxKawaseBlurRadius);
            targets.ensure(
                    regionWidth,
                    regionHeight,
                    BlurGeometry.renderScale(maxKawaseBlurRadius, regionWidth, regionHeight),
                    preparedIterations
            );
            TextureTarget blurTarget = targets.result();
            if (blurTarget != null && blurTarget.getColorTextureView() != null) {
                blurTextureSetup = TextureSetup.singleTexture(blurTarget.getColorTextureView(), sampler);
            } else {
                kawaseCaptureRequested = false;
            }
        }

        if (scoreboardCaptureRequested && !scoreboardBlur.prepare(regionWidth, regionHeight)) {
            scoreboardCaptureRequested = false;
        }

        captureRequested = kawaseCaptureRequested || scoreboardCaptureRequested;
        regionDirty = true;
        uploadRegionBuffer();
        pendingBlurs.clear();
    }

    public TextureSetup textureSetup(BlurAlgorithm algorithm) {
        return algorithm == BlurAlgorithm.SCOREBOARD_SEPARABLE
                ? scoreboardBlur.textureSetup()
                : blurTextureSetup;
    }

    public TextureSetup textureSetup() {
        return blurTextureSetup;
    }

    public void requestCapture(GuiGraphics graphics, BlurPass blur) {
        if (graphics == null || blur == null || !blur.visible()) {
            return;
        }

        try {
            BlurPass normalized = normalize(blur);
            Matrix3x2f pose = Render2DCoordinateSpace.pose(graphics);
            pendingBlurs.add(new BlurCaptureRequest(normalized, pose, ScissorUtil.current()));
        } catch (RuntimeException failure) {
        }
    }

    public void prepareGuiDraw() {
        if (!captureRequested || regionWidth <= 0 || regionHeight <= 0) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        if (mainTarget == null || mainTarget.getColorTextureView() == null) {
            return;
        }

         
        GpuTextureView guiSource = null;
        if (kawaseCaptureRequested) {
            renderBlurChain(mainTarget, guiSource);
        }
        if (scoreboardCaptureRequested) {
            GpuBuffer fullscreenQuad = ensureFullscreenQuadBuffer();
            scoreboardBlur.render(
                    mainTarget,
                    guiSource,
                    regionX,
                    regionY,
                    regionWidth,
                    regionHeight,
                    fullscreenQuad
            );
        }
    }

    private boolean shouldUseMainTargetSource(Minecraft minecraft) {
        return minecraft.screen instanceof ClickGui;
    }

    public boolean isBlurPipeline(RenderPipeline pipeline) {
        return pipeline == BATCHED_BLUR_PIPELINE;
    }

    public void bindBlurParams(RenderPass renderPass) {
        if (renderPass == null || preparedBlurs.isEmpty()) {
            return;
        }

        GpuBuffer buffer = ensureParamsBuffer();
        if (buffer != null) {
            renderPass.setUniform("BlurParamsArray", buffer);
        }
        GpuBuffer region = ensureRegionBuffer();
        if (region != null) {
            renderPass.setUniform("BlurRegion", region);
        }
    }

    public void bindBlurRegion(RenderPass renderPass) {
        if (renderPass == null) {
            return;
        }

        GpuBuffer region = ensureRegionBuffer();
        if (region != null) {
            renderPass.setUniform("BlurRegion", region);
        }
    }

    int reserve(BlurPass blur) {
        int index = preparedBlurs.size();
        if (index == MAX_BLUR_RECTS) {
            return -1;
        }

        preparedBlurs.add(blur);
        paramsDirty = true;
        maxBlurRadius = Math.max(maxBlurRadius, blur.blurRadius());
        return index;
    }

    private void submit(GuiGraphics graphics, BlurPass blur) {
        if (blur == null || !blur.visible()) {
            return;
        }

        if (graphics == null) {
            return;
        }

        try {
            BlurPass normalized = normalize(blur);
            Matrix3x2f pose = Render2DCoordinateSpace.pose(graphics);
            ScreenRectangle scissorArea = ScissorUtil.current();
            pendingBlurs.add(new BlurCaptureRequest(normalized, pose, scissorArea));
            ((GuiGraphicsExtractorAccessor) graphics).universalmod$getGuiRenderState().submitGuiElement(new BlurRenderState(pose, normalized, scissorArea));
        } catch (RuntimeException failure) {
        }
    }

    private GpuBuffer ensureParamsBuffer() {
        if (paramsDirty) {
            prepareBuffers();
        }

        if (!paramsDirty && paramsBuffer != null && !paramsBuffer.isClosed()
                && paramsBuffer.size() >= UNIFORM_BYTES) {
            return paramsBuffer;
        }

        if (paramsDirty) {
            closeParamsBuffer();

            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer uniformData = BlurUniformEncoder.rects(stack, preparedBlurs);
                paramsBuffer = RenderSystem.getDevice().createBuffer(
                        () -> "UNIVERSALMOD_blur_params",
                        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                        uniformData
                );
            }
            paramsDirty = false;
            return paramsBuffer;
        }

        if (paramsBuffer == null || paramsBuffer.isClosed() || paramsBuffer.size() < UNIFORM_BYTES) {
            closeParamsBuffer();
            paramsBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "UNIVERSALMOD_blur_params",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UNIFORM_BYTES
            );
        }

        return paramsBuffer;
    }

    public void prepareBuffers() {
        if (preparedBlurs.isEmpty() || !paramsDirty) {
            return;
        }

        GpuBuffer buffer = ensureWritableParamsBuffer();
        if (buffer == null) {
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer uniformData = BlurUniformEncoder.rects(stack, preparedBlurs);
            RenderSystem.getDevice()
                    .createCommandEncoder()
                    .writeToBuffer(buffer.slice(0, uniformData.remaining()), uniformData);
            paramsDirty = false;
        } catch (RuntimeException failure) {
            paramsDirty = true;
        }
    }

    private GpuBuffer ensureWritableParamsBuffer() {
        if (paramsBuffer != null && !paramsBuffer.isClosed() && paramsBuffer.size() >= UNIFORM_BYTES) {
            return paramsBuffer;
        }

        closeParamsBuffer();

        try {
            paramsBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "UNIVERSALMOD_blur_params",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UNIFORM_BYTES
            );
            return paramsBuffer;
        } catch (RuntimeException failure) {
            return null;
        }
    }

    private boolean computeBlurRegion(Minecraft minecraft, RenderTarget mainTarget) {
        Window window = minecraft.getWindow();
        BlurRegionPlanner.Result region = BlurRegionPlanner.compute(
                pendingBlurs,
                Math.max(window.getGuiScaledWidth(), 1),
                Math.max(window.getGuiScaledHeight(), 1),
                mainTarget.width,
                mainTarget.height
        );
        if (region == null) {
            return false;
        }

        regionX = region.x();
        regionY = region.y();
        regionWidth = region.width();
        regionHeight = region.height();
        maxBlurRadius = region.maxBlurRadius();
        maxKawaseBlurRadius = region.maxKawaseRadius();
        kawaseCaptureRequested = region.kawase();
        scoreboardCaptureRequested = region.scoreboard();
        if (scoreboardCaptureRequested) {
            scoreboardBlur.request(region.maxScoreboardRadius());
        }
        return true;
    }

    private void renderBlurChain(RenderTarget mainTarget, GpuTextureView sourceOverride) {
        TextureTarget blurTarget = targets.result();
        if (blurTarget == null) {
            return;
        }

        int iterations = preparedIterations > 0 ? preparedIterations : BlurGeometry.iterations(maxKawaseBlurRadius);
        float offsetScale = clamp(maxKawaseBlurRadius / 18.0f, 0.85f, 2.0f);
        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuTextureView source = sourceOverride != null ? sourceOverride : mainTarget.getColorTextureView();
        int sourceWidth = mainTarget.width;
        int sourceHeight = mainTarget.height;
        float sourceX = (float) regionX / Math.max(mainTarget.width, 1);
        float sourceY = (float) regionY / Math.max(mainTarget.height, 1);
        float sourceW = (float) regionWidth / Math.max(mainTarget.width, 1);
        float sourceH = (float) regionHeight / Math.max(mainTarget.height, 1);

        for (int i = 0; i < iterations; i++) {
            TextureTarget target = targets.down(i);
            renderKawasePass(KAWASE_DOWN_PIPELINE, source, sourceWidth, sourceHeight, target, offsetScale, sourceX, sourceY, sourceW, sourceH, sampler);
            source = target.getColorTextureView();
            sourceWidth = target.width;
            sourceHeight = target.height;
            sourceX = 0.0f;
            sourceY = 0.0f;
            sourceW = 1.0f;
            sourceH = 1.0f;
        }

        for (int i = iterations - 2; i >= 0; i--) {
            TextureTarget target = targets.up(i);
            renderKawasePass(KAWASE_UP_PIPELINE, source, sourceWidth, sourceHeight, target, offsetScale, 0.0f, 0.0f, 1.0f, 1.0f, sampler);
            source = target.getColorTextureView();
            sourceWidth = target.width;
            sourceHeight = target.height;
        }

        renderKawasePass(KAWASE_UP_PIPELINE, source, sourceWidth, sourceHeight, blurTarget, offsetScale, 0.0f, 0.0f, 1.0f, 1.0f, sampler);
    }

    private void renderKawasePass(
            RenderPipeline pipeline,
            GpuTextureView source,
            int sourceWidth,
            int sourceHeight,
            TextureTarget target,
            float offsetScale,
            float sourceX,
            float sourceY,
            float sourceW,
            float sourceH,
            GpuSampler sampler
    ) {
        if (source == null || target == null) {
            return;
        }

        GpuBuffer fullscreenQuad = ensureFullscreenQuadBuffer();
        if (fullscreenQuad == null) {
            return;
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        RenderSystem.AutoStorageIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indices = indexBuffer.getBuffer(6);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer uniformData = stack.calloc(KAWASE_UNIFORM_BYTES);
            uniformData.putFloat(0, sourceX);
            uniformData.putFloat(4, sourceY);
            uniformData.putFloat(8, sourceW);
            uniformData.putFloat(12, sourceH);
            uniformData.putFloat(16, offsetScale / Math.max(sourceWidth, 1));
            uniformData.putFloat(20, offsetScale / Math.max(sourceHeight, 1));
            uniformData.putFloat(32, skyFallbackRed);
            uniformData.putFloat(36, skyFallbackGreen);
            uniformData.putFloat(40, skyFallbackBlue);
            uniformData.putFloat(44, 1.0f);
            uniformData.position(0);

            GpuBuffer uniformBuffer = ensureKawaseParamsBuffer();
            if (uniformBuffer == null) {
                return;
            }

            encoder.writeToBuffer(uniformBuffer.slice(0, KAWASE_UNIFORM_BYTES), uniformData);

            try (RenderPass renderPass = encoder.createRenderPass(
                         () -> "UNIVERSALMOD_kawase_blur",
                         target.getColorTextureView(),
                         OptionalInt.of(ColorUtil.TRANSPARENT),
                         null,
                         OptionalDouble.empty()
                 )) {
                renderPass.setPipeline(pipeline);
                renderPass.bindTexture("Sampler0", source, sampler);
                renderPass.setUniform("KawaseParams", uniformBuffer);
                renderPass.setVertexBuffer(0, fullscreenQuad);
                renderPass.setIndexBuffer(indices, indexBuffer.type());
                renderPass.drawIndexed(0, 0, 6, 1);
            }
        }
    }

    private GpuBuffer ensureRegionBuffer() {
        if (regionDirty) {
            closeRegionBuffer();

            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer data = BlurUniformEncoder.region(stack, regionX, regionY, regionWidth, regionHeight);
                regionBuffer = RenderSystem.getDevice().createBuffer(
                        () -> "UNIVERSALMOD_blur_region",
                        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                        data
                );
            }
            regionDirty = false;
            return regionBuffer;
        }

        if (regionBuffer == null || regionBuffer.isClosed() || regionBuffer.size() < REGION_UNIFORM_BYTES) {
            closeRegionBuffer();
            regionBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "UNIVERSALMOD_blur_region",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    REGION_UNIFORM_BYTES
            );
        }

        return regionBuffer;
    }

    private void uploadRegionBuffer() {
        GpuBuffer buffer = ensureWritableRegionBuffer();
        if (buffer == null) {
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = BlurUniformEncoder.region(stack, regionX, regionY, regionWidth, regionHeight);
            RenderSystem.getDevice()
                    .createCommandEncoder()
                    .writeToBuffer(buffer.slice(0, REGION_UNIFORM_BYTES), data);
            regionDirty = false;
        } catch (RuntimeException failure) {
            regionDirty = true;
        }
    }

    private GpuBuffer ensureWritableRegionBuffer() {
        if (regionBuffer != null && !regionBuffer.isClosed() && regionBuffer.size() >= REGION_UNIFORM_BYTES) {
            return regionBuffer;
        }

        closeRegionBuffer();

        try {
            regionBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "UNIVERSALMOD_blur_region",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    REGION_UNIFORM_BYTES
            );
            return regionBuffer;
        } catch (RuntimeException failure) {
            regionDirty = true;
            return null;
        }
    }

    private GpuBuffer ensureKawaseParamsBuffer() {
        if (kawaseParamsBuffer == null || kawaseParamsBuffer.isClosed() || kawaseParamsBuffer.size() < KAWASE_UNIFORM_BYTES) {
            closeKawaseParamsBuffer();
            kawaseParamsBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "UNIVERSALMOD_kawase_params",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    KAWASE_UNIFORM_BYTES
            );
        }

        return kawaseParamsBuffer;
    }

    private GpuBuffer ensureFullscreenQuadBuffer() {
        if (fullscreenQuadBuffer != null && !fullscreenQuadBuffer.isClosed()) {
            return fullscreenQuadBuffer;
        }
        fullscreenQuadBuffer = null;

        try (ByteBufferBuilder allocator = new ByteBufferBuilder(4 * DefaultVertexFormat.POSITION.getVertexSize());
             MeshData meshData = buildFullscreenQuad(allocator)) {
            fullscreenQuadBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "UNIVERSALMOD_blur_fullscreen_quad",
                    GpuBuffer.USAGE_VERTEX,
                    meshData.vertexBuffer()
            );
            return fullscreenQuadBuffer;
        }
    }

    private MeshData buildFullscreenQuad(ByteBufferBuilder allocator) {
        BufferBuilder builder = new BufferBuilder(allocator, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        builder.addVertex(-1.0f, -1.0f, 0.0f);
        builder.addVertex(-1.0f, 1.0f, 0.0f);
        builder.addVertex(1.0f, 1.0f, 0.0f);
        builder.addVertex(1.0f, -1.0f, 0.0f);
        return builder.buildOrThrow();
    }

    private BlurPass normalize(BlurPass blur) {
        return BlurGeometry.normalize(blur);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

private void closeParamsBuffer() {
        if (paramsBuffer != null) {
            paramsBuffer.close();
            paramsBuffer = null;
        }
    }

    private void closeRegionBuffer() {
        if (regionBuffer != null) {
            regionBuffer.close();
            regionBuffer = null;
        }
    }

    private void closeKawaseParamsBuffer() {
        if (kawaseParamsBuffer != null) {
            kawaseParamsBuffer.close();
            kawaseParamsBuffer = null;
        }
    }

    @Override
    public void close() {
        pendingBlurs.clear();
        preparedBlurs.clear();
        activeGraphics = null;
        captureRequested = false;
        kawaseCaptureRequested = false;
        scoreboardCaptureRequested = false;
        blurTextureSetup = TextureSetup.noTexture();
        scoreboardBlur.beginFrame();
        closeParamsBuffer();
        closeRegionBuffer();
        closeKawaseParamsBuffer();
        scoreboardBlur.close();
        targets.close();
        if (fullscreenQuadBuffer != null) {
            fullscreenQuadBuffer.close();
            fullscreenQuadBuffer = null;
        }
    }

}
