package universalmod.render.blur;

import universalmod.render.GlUiState;

import universalmod.render.UiShaders;
import universalmod.render.ShaderDiagnostics;
import universalmod.render.ScissorStack;
import universalmod.render.Render2D;
import com.mojang.blaze3d.systems.RenderSystem;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

public final class KawaseBlurRenderer {
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final int MAX_ITERATIONS = 4;
    private static final int MAX_CACHED_RESULTS = 24;
    private static final BlurFramebuffer capture = BlurFramebuffer.shared();
    private static final BlurTargetPool targets = new BlurTargetPool(MAX_ITERATIONS);
    private static final Map<BlurKey, SimpleFramebuffer> results = new LinkedHashMap<>(32, 0.75f, true);
    
    
    
    private static final Set<BlurKey> preparedThisFrame = new HashSet<>();
    private static final Set<BlurKey> usedDuringFrame = new HashSet<>();
    private static boolean frameOpen;
    private static float previousFrameMaxRadius;
    private static float currentFrameMaxRadius;

    private KawaseBlurRenderer() {
    }

    public static void beginFrame() {
        frameOpen = true;
        capture.beginFrame();
        currentFrameMaxRadius = 0.0f;
        preparedThisFrame.clear();
        usedDuringFrame.clear();
    }

    public static void endFrame() {
        if (frameOpen) {
            previousFrameMaxRadius = currentFrameMaxRadius;
        }
        frameOpen = false;
        
        
        evictUnusedResults();
        preparedThisFrame.clear();
        usedDuringFrame.clear();
    }

     
    public static void invalidateScene() {
        capture.invalidateScene();
        
        
        preparedThisFrame.clear();
    }

    public static int prepareBlur(float blurRadius, float logicalWidth, float logicalHeight) {
        float requestedRadius = Math.max(0.0f, blurRadius);
        currentFrameMaxRadius = Math.max(currentFrameMaxRadius, requestedRadius);
        float effectiveRadius = Math.max(requestedRadius, Math.max(previousFrameMaxRadius, currentFrameMaxRadius));
        return prepareInternal(effectiveRadius, logicalWidth, logicalHeight, 0);
    }

    public static int prepareGlass(float blurRadius, float logicalWidth, float logicalHeight) {
        return prepareInternal(Math.max(0.0f, blurRadius), logicalWidth, logicalHeight, 1);
    }

    private static int prepareInternal(float effectiveRadius, float logicalWidth, float logicalHeight, int channel) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Framebuffer main = mc.getFramebuffer();
        if (main.textureWidth <= 0 || main.textureHeight <= 0) return 0;
        int iterations = BlurQualityPolicy.iterations(effectiveRadius);
        int physicalW = Math.max(1, Math.round(logicalWidth * main.textureWidth / (float) Math.max(1, mc.getWindow().getScaledWidth())));
        int physicalH = Math.max(1, Math.round(logicalHeight * main.textureHeight / (float) Math.max(1, mc.getWindow().getScaledHeight())));
        float renderScale = BlurQualityPolicy.renderScale(effectiveRadius, physicalW, physicalH);
        int resultWidth = Math.max(Math.round(main.textureWidth * renderScale), 1);
        int resultHeight = Math.max(Math.round(main.textureHeight * renderScale), 1);
        float offsetScale = clamp(effectiveRadius / 18.0f, 0.85f, 2.0f);
        BlurKey key = new BlurKey(channel, main.textureWidth, main.textureHeight, iterations, Float.floatToIntBits(renderScale), Float.floatToIntBits(offsetScale));
        GlUiState savedState = GlUiState.capture();
        try {
        SimpleFramebuffer cached = ensureResult(key, resultWidth, resultHeight);
        if (preparedThisFrame.contains(key)) {
            usedDuringFrame.add(key);
            return cached.getColorAttachment();
        }
        int sourceCapture = capture.capture(main);
        if (sourceCapture == 0) return 0;
        ScissorStack.suspend();
        try {
            targets.ensure(resultWidth, resultHeight, iterations);
            int sourceTex = sourceCapture;
            int sourceW = capture.textureWidth();
            int sourceH = capture.textureHeight();
            RenderSystem.disableDepthTest();
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            for (int i = 0; i < iterations; i++) {
                SimpleFramebuffer target = targets.down(i);
                if (!pass(UiShaders.KAWASE_DOWN, sourceTex, sourceW, sourceH, target, offsetScale)) return 0;
                sourceTex = target.getColorAttachment();
                sourceW = target.textureWidth;
                sourceH = target.textureHeight;
            }
            for (int i = iterations - 2; i >= 0; i--) {
                SimpleFramebuffer target = targets.up(i);
                if (!pass(UiShaders.KAWASE_UP, sourceTex, sourceW, sourceH, target, offsetScale)) return 0;
                sourceTex = target.getColorAttachment();
                sourceW = target.textureWidth;
                sourceH = target.textureHeight;
            }
            if (!pass(UiShaders.KAWASE_UP, sourceTex, sourceW, sourceH, cached, offsetScale)) return 0;
            preparedThisFrame.add(key);
            usedDuringFrame.add(key);
            return cached.getColorAttachment();
        } finally {
            ScissorStack.resume();
        }
        } finally {
            savedState.close();
        }
    }

    private static SimpleFramebuffer ensureResult(BlurKey key, int width, int height) {
        SimpleFramebuffer framebuffer = results.get(key);
        framebuffer = ensure(framebuffer, width, height);
        results.put(key, framebuffer);
        return framebuffer;
    }

     
    private static void evictUnusedResults() {
        if (results.size() <= MAX_CACHED_RESULTS) return;
        Iterator<Map.Entry<BlurKey, SimpleFramebuffer>> it = results.entrySet().iterator();
        while (results.size() > MAX_CACHED_RESULTS && it.hasNext()) {
            Map.Entry<BlurKey, SimpleFramebuffer> entry = it.next();
            if (usedDuringFrame.contains(entry.getKey())) continue;
            entry.getValue().delete();
            it.remove();
        }
    }

     
    public static void close() {
        RenderSystem.assertOnRenderThread();
        capture.close();
        targets.close();
        for (SimpleFramebuffer framebuffer : results.values()) framebuffer.delete();
        results.clear();
        preparedThisFrame.clear();
        usedDuringFrame.clear();
        frameOpen = false;
        previousFrameMaxRadius = 0f;
        currentFrameMaxRadius = 0f;
    }

    private static boolean pass(ShaderProgram key, int sourceTexture, int sourceW, int sourceH, SimpleFramebuffer target, float offsetScale) {
        target.beginWrite(true);
        RenderSystem.setShaderTexture(0, sourceTexture);
        ShaderProgram shader = UiShaders.bind(key, key == UiShaders.KAWASE_DOWN ? "kawase_down" : "kawase_up");
        if (shader == null) {
            ShaderDiagnostics.missing(key == UiShaders.KAWASE_DOWN ? "kawase_down" : "kawase_up");
            return false;
        }
        var sourceRect = shader.getUniform("SourceRect");
        var halfPixel = shader.getUniform("HalfPixel");
        var fallback = shader.getUniform("FallbackColor");
        if (sourceRect == null || halfPixel == null || fallback == null) {
            ShaderDiagnostics.invalid(key == UiShaders.KAWASE_DOWN ? "kawase_down" : "kawase_up", "required uniform missing");
            return false;
        }
        sourceRect.set(0.0f, 0.0f, 1.0f, 1.0f);
        halfPixel.set(offsetScale / Math.max(sourceW, 1), offsetScale / Math.max(sourceH, 1), 0.0f, 0.0f);
        fallback.set(0.0f, 0.0f, 0.0f, 1.0f);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        b.vertex(IDENTITY, -1.0f, -1.0f, 0.0f).color(0xFFFFFFFF).next();
        b.vertex(IDENTITY, -1.0f, 1.0f, 0.0f).color(0xFFFFFFFF).next();
        b.vertex(IDENTITY, 1.0f, 1.0f, 0.0f).color(0xFFFFFFFF).next();
        b.vertex(IDENTITY, 1.0f, -1.0f, 0.0f).color(0xFFFFFFFF).next();
        BufferRenderer.drawWithGlobalProgram(b.end());
        return true;
    }

    private static SimpleFramebuffer ensure(SimpleFramebuffer fbo, int width, int height) {
        if (fbo == null) fbo = new SimpleFramebuffer(width, height, false, false);
        else if (fbo.textureWidth != width || fbo.textureHeight != height) fbo.resize(width, height, false);
        fbo.setTexFilter(GL11.GL_LINEAR);
        return fbo;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private record BlurKey(int channel, int framebufferWidth, int framebufferHeight, int iterations,
                           int renderScaleBits, int offsetScaleBits) {
    }
}
