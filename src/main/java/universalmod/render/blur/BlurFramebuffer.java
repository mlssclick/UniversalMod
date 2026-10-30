package universalmod.render.blur;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import universalmod.render.ScissorUtil;
import universalmod.render.RenderSampler;
import universalmod.render.ClientPipelines;
import universalmod.render.Render2DCoordinateSpace;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

public final class BlurFramebuffer implements AutoCloseable {
    private int guiTexture;
    private static final BlurFramebuffer INSTANCE = new BlurFramebuffer();
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final int MAX_ITERATIONS = 4;

    private SimpleFramebuffer capture;
    private static final int MAX_KAWASE_RESULT_TARGETS = 16;
    private BlurTargetPool<SimpleFramebuffer> targets = newTargetPool();

    private static BlurTargetPool<SimpleFramebuffer> newTargetPool() {
        return new BlurTargetPool<>(MAX_ITERATIONS, MAX_KAWASE_RESULT_TARGETS,
                new BlurTargetPool.Factory<>() {
            @Override public SimpleFramebuffer create(int w, int h) { return ensure(null, w, h); }
            @Override public int width(SimpleFramebuffer t) { return t.textureWidth; }
            @Override public int height(SimpleFramebuffer t) { return t.textureHeight; }
            @Override public void resize(SimpleFramebuffer t, int w, int h) {
                t.resize(w, h);
                RenderSampler.linear(t);
            }
            @Override public void destroy(SimpleFramebuffer t) { t.delete(); }
        });
    }
    private final Map<BlurKey, SimpleFramebuffer> preparedThisFrame = new HashMap<>();
    private final Map<ScoreboardKey, ScoreboardBlurChain> scoreboardChains = new HashMap<>();
    private final Map<ScoreboardKey, Integer> scoreboardTextures = new HashMap<>();
    private static final int MAX_SCOREBOARD_CHAINS = 8;
    private DrawContext activeGraphics;
    private boolean frameOpen, capturedThisFrame;
    private float previousFrameMaxRadius, currentFrameMaxRadius;

    private BlurFramebuffer() {
    }

    public static BlurFramebuffer getInstance() {
        return INSTANCE;
    }

    public static void closeInstance() {
        INSTANCE.close();
    }

    public void beginFrame(DrawContext graphics) {
        activeGraphics = graphics;
        frameOpen = true;
        capturedThisFrame = false;
        currentFrameMaxRadius = 0;
        preparedThisFrame.clear();
        scoreboardTextures.clear();
    }

    public void enqueue(BlurPass blur) {
        draw(activeGraphics, blur);
    }

    public void draw(DrawContext graphics, BlurPass blur) {
        if (blur == null || !blur.visible()) return;
        Matrix4f pose = new Matrix4f(Render2DCoordinateSpace.matrix());
        int[] clip = ScissorUtil.currentFramebufferBounds();
        BlurRegion scissor = clip == null ? null : new BlurRegion(clip[0], clip[1], clip[2], clip[3]);
        drawCaptured(graphics, new BlurCaptureRequest(blur, pose, scissor));
    }

    private void drawCaptured(DrawContext graphics, BlurCaptureRequest request) {
        BlurPass blur = request.blur();
        Framebuffer main = MinecraftClient.getInstance().getFramebuffer();
        int texture;
        BlurRegionPlanner.Result plan = planRegion(request, main);
        if (plan == null) return;
        BlurRegion region = plan.region();
        if (blur.algorithm() == BlurAlgorithm.SCOREBOARD_SEPARABLE) {
            texture = prepareScoreboardTexture(blur.blurRadius(), region, main);
        } else {
            float requested = Math.max(0f, blur.blurRadius());
            currentFrameMaxRadius = Math.max(currentFrameMaxRadius, requested);

            float effective = Math.max(requested, Math.max(previousFrameMaxRadius, currentFrameMaxRadius));
            texture = prepareInternal(effective, region, 0);
        }
        if (texture == 0) return;

        int[] oldClip = ScissorUtil.currentFramebufferBounds();
        applyClip(request.scissorArea());
        try {
        if (BlurBatchRenderer.instance().renderCaptured(List.of(request), texture, region)) return;
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShaderTexture(0, texture);
        try {
            ShaderProgram s = ClientPipelines.bind(ClientPipelines.BLUR, "ui_batched_blur");
            if (s == null) return;
            set2(s, "Size", blur.width(), blur.height());
            set4(s, "Radius", blur.radiusTopLeft(), blur.radiusTopRight(), blur.radiusBottomRight(), blur.radiusBottomLeft());
            set1(s, "Smoothness", Math.max(.001f, blur.smoothness()));
            set4(s, "BlurRegion", region.x(), region.y(), region.width(), region.height());
            BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            var m = request.pose();
            int tl = splitColor(blur, 0, blur.colorTopLeft()), tr = splitColor(blur, 1, blur.colorTopRight()), br = splitColor(blur, 2, blur.colorBottomRight()), bl = splitColor(blur, 3, blur.colorBottomLeft());
            b.vertex(m, blur.x(), blur.y(), 0).color(tl);
            b.vertex(m, blur.x(), blur.y() + blur.height(), 0).color(bl);
            b.vertex(m, blur.x() + blur.width(), blur.y() + blur.height(), 0).color(br);
            b.vertex(m, blur.x() + blur.width(), blur.y(), 0).color(tr);
            BufferRenderer.drawWithGlobalProgram(b.end());
        } finally {
            RenderSystem.setShaderTexture(0, 0);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
        }
        } finally {
            restoreClip(oldClip);
        }
    }

    private static void applyClip(BlurRegion clip) {
        if (clip == null) RenderSystem.disableScissor();
        else RenderSystem.enableScissor(clip.x(), clip.y(), clip.width(), clip.height());
    }

    private static void restoreClip(int[] clip) {
        if (clip == null) RenderSystem.disableScissor();
        else RenderSystem.enableScissor(clip[0], clip[1], clip[2], clip[3]);
    }

    public void drawBatch(DrawContext graphics, List<BlurPass> input) {
        if (input == null || input.isEmpty()) return;
        Matrix4f pose = new Matrix4f(Render2DCoordinateSpace.matrix());
        int[] clip = ScissorUtil.currentFramebufferBounds();
        BlurRegion scissor = clip == null ? null : new BlurRegion(clip[0], clip[1], clip[2], clip[3]);
        List<BlurCaptureRequest> requests = new java.util.ArrayList<>(input.size());
        for (BlurPass blur : input) {
            if (blur != null) requests.add(new BlurCaptureRequest(blur, pose, scissor));
        }
        drawCapturedBatch(graphics, requests);
    }

    void drawCapturedBatch(DrawContext graphics, List<BlurCaptureRequest> input) {
        if (input == null || input.isEmpty()) return;
        int capacity;
        try {
            capacity = Math.max(1, BlurShaderCapacity.rectangles(
                    GL11.glGetInteger(org.lwjgl.opengl.GL31.GL_MAX_UNIFORM_BLOCK_SIZE)));
        } catch (Throwable unsupportedContext) {
            capacity = 1;
        }
        for (List<BlurCaptureRequest> batch : BlurBatchPartitioner.partition(input, capacity)) {
            Framebuffer main = MinecraftClient.getInstance().getFramebuffer();
            var window = MinecraftClient.getInstance().getWindow();
            var plan = BlurRegionPlanner.compute(batch, window.getScaledWidth(), window.getScaledHeight(),
                    main.textureWidth, main.textureHeight, Render2DCoordinateSpace.guiIndependentScale());
            int texture = 0;
            if (plan != null) {
                float radius = plan.maxBlurRadius();
                if (batch.get(0).blur().algorithm() == BlurAlgorithm.SCOREBOARD_SEPARABLE) {
                    texture = prepareScoreboardTexture(radius, plan.region(), main);
                } else {
                    texture = prepareInternal(radius, plan.region(), 0);
                }
            }
            boolean drawn = false;
            if (texture != 0 && plan != null) {
                int[] oldClip = ScissorUtil.currentFramebufferBounds();
                applyClip(batch.get(0).scissorArea());
                try { drawn = BlurBatchRenderer.instance().renderCaptured(batch, texture, plan.region()); }
                finally { restoreClip(oldClip); }
            }
            if (!drawn) {

                for (BlurCaptureRequest request : batch) drawCaptured(graphics, request);
            }
        }
    }

    private static int splitColor(BlurPass b, int corner, int normal) {
        if (b.verticalSplit() < 0) return normal;
        boolean top = corner == 0 || corner == 1;
        return top ? b.splitColor() : normal;
    }

    public int prepareBlurTexture(float radius, float logicalWidth, float logicalHeight) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getFramebuffer() == null) return 0;
        Framebuffer main = mc.getFramebuffer();
        currentFrameMaxRadius = Math.max(currentFrameMaxRadius, Math.max(radius, 0f));
        float effective = Math.max(radius, Math.max(previousFrameMaxRadius, currentFrameMaxRadius));
        return prepareInternal(effective, new BlurRegion(0, 0, main.textureWidth, main.textureHeight), 0);
    }

    public int prepareGlassTexture(float radius, float logicalWidth, float logicalHeight) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getFramebuffer() == null) return 0;
        Framebuffer main = mc.getFramebuffer();
        guiTexture = prepareInternal(Math.max(0, radius),
                new BlurRegion(0, 0, main.textureWidth, main.textureHeight), 1);
        return guiTexture;
    }

    public universalmod.render.gpu.TextureSetup textureSetup() {
        int texture = guiTexture;
        return universalmod.render.gpu.TextureSetup.single(() -> texture, false);
    }

    private int prepareInternal(float effectiveRadius, BlurRegion region, int channel) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || region == null) return 0;
        Framebuffer main = mc.getFramebuffer();
        if (main == null || main.textureWidth <= 0 || main.textureHeight <= 0
                || region.width() <= 0 || region.height() <= 0) return 0;
        int iterations = computeIterations(effectiveRadius);
        float scale = computeRenderScale(effectiveRadius, region.width(), region.height());
        int resultW = Math.max(Math.round(region.width() * scale), 1);
        int resultH = Math.max(Math.round(region.height() * scale), 1);
        float offsetScale = clamp(effectiveRadius / 18f, .85f, 2f);
        BlurKey key = new BlurKey(channel, main.textureWidth, main.textureHeight, region, iterations,
                Float.floatToIntBits(scale), Float.floatToIntBits(offsetScale));
        SimpleFramebuffer cached = targets.result(key, resultW, resultH);
        if (cached == null) return 0;
        if (preparedThisFrame.get(key) == cached) return cached.getColorAttachment();
        if (!captureSceneOnce(main)) return 0;
        ScissorUtil.suspend();
        try {
            RenderSystem.disableDepthTest();
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            int sourceTex = capture.getColorAttachment();
            int sourceW = capture.textureWidth;
            int sourceH = capture.textureHeight;

            if (region.x() != 0 || region.y() != 0 || region.width() != main.textureWidth
                    || region.height() != main.textureHeight) {
                SimpleFramebuffer crop = targets.crop(region.width(), region.height());
                if (!cropRegion(sourceTex, sourceW, sourceH, crop, region)) return 0;
                sourceTex = crop.getColorAttachment();
                sourceW = crop.textureWidth;
                sourceH = crop.textureHeight;
            }
            targets.ensure(region.width(), region.height(), scale, iterations);
            for (int i = 0; i < iterations; ++i) {
                SimpleFramebuffer target = targets.down(i);
                if (!pass(ClientPipelines.KAWASE_DOWN, "ui_kawase_down", sourceTex, sourceW, sourceH,
                        target, offsetScale)) return 0;
                sourceTex = target.getColorAttachment();
                sourceW = target.textureWidth;
                sourceH = target.textureHeight;
            }
            for (int i = iterations - 2; i >= 0; i--) {
                SimpleFramebuffer target = targets.up(i);
                if (!pass(ClientPipelines.KAWASE_UP, "ui_kawase_up", sourceTex, sourceW, sourceH,
                        target, offsetScale)) return 0;
                sourceTex = target.getColorAttachment();
                sourceW = target.textureWidth;
                sourceH = target.textureHeight;
            }
            if (!pass(ClientPipelines.KAWASE_UP, "ui_kawase_up", sourceTex, sourceW, sourceH,
                    cached, offsetScale)) return 0;
            preparedThisFrame.put(key, cached);
            return cached.getColorAttachment();
        } finally {
            main.beginWrite(true);
            RenderSystem.setShaderTexture(0, 0);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            ScissorUtil.resume();
        }
    }

    private static BlurRegionPlanner.Result planRegion(BlurCaptureRequest request, Framebuffer main) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return null;
        return BlurRegionPlanner.compute(List.of(request),
                mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(),
                main.textureWidth, main.textureHeight, Render2DCoordinateSpace.guiIndependentScale());
    }

    private boolean cropRegion(int sourceTexture, int sourceWidth, int sourceHeight,
                               SimpleFramebuffer target, BlurRegion region) {
        target.beginWrite(true);
        RenderSystem.setShaderTexture(0, sourceTexture);
        ShaderProgram shader = ClientPipelines.bind(ClientPipelines.SCOREBOARD_CROP, "ui_scoreboard_crop");
        if (shader == null || shader.getUniform("SourceRect") == null) return false;
        shader.getUniform("SourceRect").set(region.x() / (float) sourceWidth,
                region.y() / (float) sourceHeight, region.width() / (float) sourceWidth,
                region.height() / (float) sourceHeight);
        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        b.vertex(IDENTITY, -1, -1, 0).color(0xFFFFFFFF);
        b.vertex(IDENTITY, -1, 1, 0).color(0xFFFFFFFF);
        b.vertex(IDENTITY, 1, 1, 0).color(0xFFFFFFFF);
        b.vertex(IDENTITY, 1, -1, 0).color(0xFFFFFFFF);
        BufferRenderer.drawWithGlobalProgram(b.end());
        return true;
    }

    private int prepareScoreboardTexture(float radius, BlurRegion region, Framebuffer main) {
        if (region == null || main == null || main.textureWidth <= 0 || main.textureHeight <= 0) return 0;
        ScoreboardKey key = new ScoreboardKey(region, main.textureWidth, main.textureHeight,
                Float.floatToIntBits(Math.max(1.0f, Math.min(32.0f, radius))));
        Integer current = scoreboardTextures.get(key);
        if (current != null) return current;
        if (!captureSceneOnce(main)) return 0;
        if (!scoreboardChains.containsKey(key) && scoreboardChains.size() >= MAX_SCOREBOARD_CHAINS) {
            for (ScoreboardBlurChain chain : scoreboardChains.values()) chain.close();
            scoreboardChains.clear();
            scoreboardTextures.clear();
        }
        ScoreboardBlurChain chain = scoreboardChains.computeIfAbsent(key, ignored -> new ScoreboardBlurChain());
        ScissorUtil.suspend();
        try {
            RenderSystem.disableDepthTest();
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            int result = chain.render(capture.getColorAttachment(), capture.textureWidth,
                    capture.textureHeight, region, radius);
            if (result != 0) scoreboardTextures.put(key, result);
            return result;
        } finally {
            main.beginWrite(true);
            RenderSystem.setShaderTexture(0, 0);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            ScissorUtil.resume();
        }
    }

    private boolean captureSceneOnce(Framebuffer main) {
        if (capturedThisFrame && capture != null && capture.textureWidth == main.textureWidth && capture.textureHeight == main.textureHeight)
            return true;
        ScissorUtil.suspend();
        try {
            capture = ensure(capture, main.textureWidth, main.textureHeight);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.fbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, capture.fbo);
            GL30.glBlitFramebuffer(0, 0, main.textureWidth, main.textureHeight, 0, 0, capture.textureWidth, capture.textureHeight, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            capturedThisFrame = true;
            return true;
        } catch (Throwable t) {
            capturedThisFrame = false;

            return false;
        } finally {
            main.beginWrite(true);
            RenderSystem.setShaderTexture(0, 0);
            ScissorUtil.resume();
        }
    }

    private boolean pass(ShaderProgramKey key, String name, int sourceTexture, int sourceW, int sourceH, SimpleFramebuffer target, float offsetScale) {
        target.beginWrite(true);
        RenderSystem.setShaderTexture(0, sourceTexture);
        ShaderProgram s = ClientPipelines.bind(key, name);
        if (s == null) return false;
        var sourceRect = s.getUniform("SourceRect");
        var halfPixel = s.getUniform("HalfPixel");
        var fallback = s.getUniform("FallbackColor");
        if (sourceRect == null || halfPixel == null || fallback == null) return false;
        sourceRect.set(0.0F, 0.0F, 1.0F, 1.0F);
        halfPixel.set(offsetScale / Math.max(sourceW, 1), offsetScale / Math.max(sourceH, 1), 0.0F, 0.0F);
        fallback.set(0.0F, 0.0F, 0.0F, 1.0F);
        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        b.vertex(IDENTITY, -1, -1, 0).color(0xFFFFFFFF);
        b.vertex(IDENTITY, -1, 1, 0).color(0xFFFFFFFF);
        b.vertex(IDENTITY, 1, 1, 0).color(0xFFFFFFFF);
        b.vertex(IDENTITY, 1, -1, 0).color(0xFFFFFFFF);
        BufferRenderer.drawWithGlobalProgram(b.end());
        return true;
    }

    private static SimpleFramebuffer ensure(SimpleFramebuffer f, int w, int h) {
        if (f == null) f = new SimpleFramebuffer(w, h, false);
        else if (f.textureWidth != w || f.textureHeight != h) f.resize(w, h);
        RenderSampler.linear(f);
        return f;
    }

    private static int computeIterations(float r) {
        return BlurQualityPolicy.iterations(r);
    }

    private static float computeRenderScale(float r, int w, int h) {
        return BlurQualityPolicy.renderScale(r, w, h);
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    public void flush() {
        if (frameOpen) previousFrameMaxRadius = currentFrameMaxRadius;
        frameOpen = false;
        capturedThisFrame = false;
        preparedThisFrame.clear();
        scoreboardTextures.clear();
        activeGraphics = null;
    }

    @Override
    public void close() {
        flush();
        if (capture != null) {
            capture.delete();
            capture = null;
        }
        targets.close();
        BlurBatchRenderer.instance().close();

        targets = newTargetPool();
        previousFrameMaxRadius = currentFrameMaxRadius = 0f;
        preparedThisFrame.clear();
        for (ScoreboardBlurChain chain : scoreboardChains.values()) chain.close();
        scoreboardChains.clear();
        scoreboardTextures.clear();
    }

    private static void set1(ShaderProgram s, String n, float a) {
        var u = s.getUniform(n);
        if (u != null) u.set(a);
    }

    private static void set2(ShaderProgram s, String n, float a, float b) {
        var u = s.getUniform(n);
        if (u != null) u.set(a, b);
    }

    private static void set4(ShaderProgram s, String n, float a, float b, float c, float d) {
        var u = s.getUniform(n);
        if (u != null) u.set(a, b, c, d);
    }

    private record ScoreboardKey(BlurRegion region, int frameWidth, int frameHeight, int radiusBits) { }

    private record BlurKey(int channel, int framebufferWidth, int framebufferHeight,
                           BlurRegion region, int iterations, int renderScaleBits, int offsetScaleBits) {
    }
}
