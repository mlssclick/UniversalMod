package universalmod.render.hand;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;
import universalmod.render.RenderSampler;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class HandsSmokeRenderer {
    private static final Identifier TRAIL_PIPELINE_ID = Identifier.fromNamespaceAndPath("universalmod", "pipeline/effects/hands_flame_trail");
    private static final Identifier BLUR_PIPELINE_ID = Identifier.fromNamespaceAndPath("universalmod", "pipeline/effects/hands_flame_kawase_blur");
    private static final Identifier COMPOSITE_PIPELINE_ID = Identifier.fromNamespaceAndPath("universalmod", "pipeline/effects/hands_flame_composite");
    private static final Identifier CACHE_PIPELINE_ID = Identifier.fromNamespaceAndPath("universalmod", "pipeline/effects/hands_flame_cache");
    private static final Identifier FULLSCREEN_VERTEX_SHADER = Identifier.fromNamespaceAndPath("universalmod", "effects/hands_flame/fullscreen");
    private static final Identifier CACHE_VERTEX_SHADER = Identifier.fromNamespaceAndPath("universalmod", "post/shaderhands/fullscreen");
    private static final Identifier TRAIL_FRAGMENT_SHADER = Identifier.fromNamespaceAndPath("universalmod", "effects/hands_flame/trail");
    private static final Identifier BLUR_FRAGMENT_SHADER = Identifier.fromNamespaceAndPath("universalmod", "effects/hands_flame/kawase_blur");
    private static final Identifier COMPOSITE_FRAGMENT_SHADER = Identifier.fromNamespaceAndPath("universalmod", "effects/hands_flame/composite");
    private static final Identifier CACHE_FRAGMENT_SHADER = Identifier.fromNamespaceAndPath("universalmod", "post/shaderhands/cache_blit");
    private static final int UNIFORM_BYTES = 80;

    private static boolean flameEnabled;
    private static float flameStrength = 0.85f;
    private static float flameRiseSpeed;
    private static float flameWobble = 0.65f;
    private static float flameLength = 0.95f;
    private static float flameBrightness = 0.9f;
    private static int flameColorMode = 1;
    private static int flameColor = 0xE6FFFFFF;

    private static RenderPipeline trailPipeline;
    private static RenderPipeline blurPipeline;
    private static RenderPipeline compositePipeline;
    private static RenderPipeline cachePipeline;
    private static GpuBuffer uniformBuffer;
    private static GpuBuffer dummyVertexBuffer;
    private static ByteBuffer dataBuffer;

    private static GpuTexture sceneTexture;
    private static GpuTexture trailTextureA;
    private static GpuTexture trailTextureB;
    private static GpuTexture smokeCache;
    private static GpuTextureView sceneTextureView;
    private static GpuTextureView trailTextureViewA;
    private static GpuTextureView trailTextureViewB;
    private static GpuTextureView smokeCacheView;
    private static boolean cacheValid;
    private static float cachedTime;
    private static int lastWidth = -1;
    private static int lastHeight = -1;
    private static boolean disabledAfterError;

    private HandsSmokeRenderer() {
    }

    public static void renderCapturedHandsFlame(RenderTarget target, GpuTextureView beforeColorView, GpuTextureView handMaskView, boolean updateEffects) {
        if (!shouldRenderFlame() || target == null || beforeColorView == null || handMaskView == null || !isUsable(target) || !ensureReady(target.width, target.height)) return;
        try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            if (updateEffects || !cacheValid) {
                cachedTime = flameTime() * 1.2f;
                writeUniforms(target.width, target.height, 1.0f);
                encoder.writeToBuffer(uniformBuffer.slice(0, dataBuffer.remaining()), dataBuffer);
                renderTrail(encoder, target, beforeColorView, handMaskView);
                renderKawaseBlur(encoder, trailTextureViewA, trailTextureViewB, 0.75f);
                renderKawaseBlur(encoder, trailTextureViewB, trailTextureViewA, 1.55f);
                renderKawaseBlur(encoder, trailTextureViewA, trailTextureViewB, 2.45f);
                renderKawaseBlur(encoder, trailTextureViewB, trailTextureViewA, 3.35f);
                encoder.copyTextureToTexture(target.getColorTexture(), sceneTexture, 0, 0, 0, 0, 0, target.width, target.height);
                renderCompositeCache(encoder, beforeColorView, handMaskView);
                cacheValid = true;
            }
            renderCache(encoder, target);
        } catch (RuntimeException | LinkageError throwable) {
            disableAfterError(throwable);
        }
    }

    public static boolean hasPreparedTrail() {
        return cacheValid;
    }

    public static void resetTrail() {
        cacheValid = false;
        cachedTime = 0.0f;
        if (trailTextureA == null || trailTextureB == null || smokeCache == null) return;
        try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.clearColorTexture(trailTextureA, 0);
            encoder.clearColorTexture(trailTextureB, 0);
            encoder.clearColorTexture(smokeCache, 0);
        } catch (RuntimeException | LinkageError throwable) {
            disableAfterError(throwable);
        }
    }

    public static void shutdown() {
        closeTextures();
        if (uniformBuffer != null) uniformBuffer.close();
        if (dummyVertexBuffer != null) dummyVertexBuffer.close();
        if (dataBuffer != null) MemoryUtil.memFree(dataBuffer);
        uniformBuffer = null;
        dummyVertexBuffer = null;
        dataBuffer = null;
        trailPipeline = null;
        blurPipeline = null;
        compositePipeline = null;
        cachePipeline = null;
        cacheValid = false;
        cachedTime = 0.0f;
    }

    public static void reloadResources() {
        shutdown();
        disabledAfterError = false;
    }

    public static void setFlameEnabled(boolean enabled) {
        if (flameEnabled == enabled) return;
        flameEnabled = enabled;
        if (!enabled) closeTextures();
        else {
            cacheValid = false;
            cachedTime = 0.0f;
        }
    }

    public static void configure(float strength, float riseSpeed, float wobble, float length, float brightness, int colorMode, int color) {
        flameStrength = clamp(strength, 0.0f, 2.0f);
        flameRiseSpeed = clamp(riseSpeed, 0.0f, 2.0f);
        flameWobble = clamp(wobble, 0.0f, 2.0f);
        flameLength = clamp(length, 0.1f, 2.5f);
        flameBrightness = clamp(brightness, 0.0f, 2.0f);
        flameColorMode = Math.max(0, Math.min(2, colorMode));
        flameColor = color;
    }

    public static boolean shouldRenderFlame() {
        if (!flameEnabled || disabledAfterError) return false;
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.player != null && minecraft.level != null;
    }

    private static boolean ensureReady(int width, int height) {
        if (trailPipeline == null || blurPipeline == null || compositePipeline == null || cachePipeline == null || uniformBuffer == null || dummyVertexBuffer == null || dataBuffer == null) {
            initPipelines();
        }
        if (disabledAfterError) return false;
        ensureTextures(width, height);
        return trailPipeline != null && blurPipeline != null && compositePipeline != null && cachePipeline != null
                && uniformBuffer != null && dummyVertexBuffer != null && dataBuffer != null
                && sceneTexture != null && trailTextureA != null && trailTextureB != null && smokeCache != null
                && sceneTextureView != null && trailTextureViewA != null && trailTextureViewB != null && smokeCacheView != null;
    }

    private static void initPipelines() {
        try {
            trailPipeline = RenderPipeline.builder()
                    .withLocation(TRAIL_PIPELINE_ID)
                    .withVertexShader(FULLSCREEN_VERTEX_SHADER)
                    .withFragmentShader(TRAIL_FRAGMENT_SHADER)
                    .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                    .withUniform("HandsFlameData", UniformType.UNIFORM_BUFFER)
                    .withSampler("BeforeSampler")
                    .withSampler("AfterSampler")
                    .withSampler("HandMaskSampler")
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build();
            blurPipeline = RenderPipeline.builder()
                    .withLocation(BLUR_PIPELINE_ID)
                    .withVertexShader(FULLSCREEN_VERTEX_SHADER)
                    .withFragmentShader(BLUR_FRAGMENT_SHADER)
                    .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                    .withUniform("HandsFlameData", UniformType.UNIFORM_BUFFER)
                    .withSampler("InputSampler")
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build();
            compositePipeline = RenderPipeline.builder()
                    .withLocation(COMPOSITE_PIPELINE_ID)
                    .withVertexShader(FULLSCREEN_VERTEX_SHADER)
                    .withFragmentShader(COMPOSITE_FRAGMENT_SHADER)
                    .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                    .withUniform("HandsFlameData", UniformType.UNIFORM_BUFFER)
                    .withSampler("SceneSampler")
                    .withSampler("BeforeSampler")
                    .withSampler("TrailSampler")
                    .withSampler("HandMaskSampler")
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build();
            cachePipeline = RenderPipeline.builder()
                    .withLocation(CACHE_PIPELINE_ID)
                    .withVertexShader(CACHE_VERTEX_SHADER)
                    .withFragmentShader(CACHE_FRAGMENT_SHADER)
                    .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                    .withSampler("CacheSampler")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build();

            dataBuffer = MemoryUtil.memAlloc(UNIFORM_BYTES);
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "universalmod:hands_flame_uniform",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UNIFORM_BYTES
            );
            ByteBuffer dummyData = MemoryUtil.memAlloc(4);
            dummyData.putInt(0);
            dummyData.flip();
            dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "universalmod:hands_flame_dummy_vertex",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                    dummyData
            );
            MemoryUtil.memFree(dummyData);
        } catch (RuntimeException | LinkageError throwable) {
            disableAfterError(throwable);
        }
    }

    private static void ensureTextures(int width, int height) {
        if (sceneTexture != null && smokeCache != null && width == lastWidth && height == lastHeight) return;
        closeTextures();
        int trailWidth = Math.max(1, width / 2);
        int trailHeight = Math.max(1, height / 2);
        sceneTexture = createTexture("universalmod:hands_flame_scene", width, height, GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING);
        smokeCache = createTexture("universalmod:hands_flame_cache", width, height, GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT);
        trailTextureA = createTexture("universalmod:hands_flame_trail_a", trailWidth, trailHeight, GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT);
        trailTextureB = createTexture("universalmod:hands_flame_trail_b", trailWidth, trailHeight, GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT);
        sceneTextureView = RenderSystem.getDevice().createTextureView(sceneTexture);
        smokeCacheView = RenderSystem.getDevice().createTextureView(smokeCache);
        trailTextureViewA = RenderSystem.getDevice().createTextureView(trailTextureA);
        trailTextureViewB = RenderSystem.getDevice().createTextureView(trailTextureB);
        lastWidth = width;
        lastHeight = height;
        resetTrail();
    }

    private static GpuTexture createTexture(String label, int width, int height, int usage) {
        return RenderSystem.getDevice().createTexture(() -> label, usage, TextureFormat.RGBA8, width, height, 1, 1);
    }

    private static void writeUniforms(int width, int height, float kawaseOffset) {
        int color = flameColor;
        float alpha = ((color >>> 24) & 0xFF) / 255.0f;
        dataBuffer.clear();
        dataBuffer.putFloat(((color >> 16) & 0xFF) / 255.0f);
        dataBuffer.putFloat(((color >> 8) & 0xFF) / 255.0f);
        dataBuffer.putFloat((color & 0xFF) / 255.0f);
        dataBuffer.putFloat(alpha);
        dataBuffer.putFloat(flameStrength);
        dataBuffer.putFloat(flameRiseSpeed);
        dataBuffer.putFloat(clamp(flameWobble * 1.2f, 0.0f, 2.4f));
        dataBuffer.putFloat(flameLength);
        dataBuffer.putFloat(flameBrightness);
        dataBuffer.putFloat(cachedTime);
        dataBuffer.putFloat(flameColorMode);
        dataBuffer.putFloat(alpha);
        dataBuffer.putFloat(width);
        dataBuffer.putFloat(height);
        dataBuffer.putFloat(1.0f / Math.max(width, 1));
        dataBuffer.putFloat(1.0f / Math.max(height, 1));
        dataBuffer.putFloat(0.0f);
        dataBuffer.putFloat(0.0f);
        dataBuffer.putFloat(0.0f);
        dataBuffer.putFloat(kawaseOffset);
        dataBuffer.flip();
    }

    private static void renderTrail(CommandEncoder encoder, RenderTarget target, GpuTextureView beforeColorView, GpuTextureView handMaskView) {
        try (var pass = encoder.createRenderPass(() -> "universalmod:hands_flame_trail", trailTextureViewA, OptionalInt.of(0), null, OptionalDouble.empty())) {
            pass.setPipeline(trailPipeline);
            pass.setVertexBuffer(0, dummyVertexBuffer);
            pass.bindTexture("BeforeSampler", beforeColorView, RenderSampler.linear());
            pass.bindTexture("AfterSampler", target.getColorTextureView(), RenderSampler.linear());
            pass.bindTexture("HandMaskSampler", handMaskView, RenderSampler.nearest());
            pass.setUniform("HandsFlameData", uniformBuffer.slice());
            pass.draw(0, 3);
        }
    }

    private static void renderKawaseBlur(CommandEncoder encoder, GpuTextureView inputView, GpuTextureView outputView, float offset) {
        writeUniforms(lastWidth, lastHeight, offset);
        encoder.writeToBuffer(uniformBuffer.slice(0, dataBuffer.remaining()), dataBuffer);
        try (var pass = encoder.createRenderPass(() -> "universalmod:hands_flame_kawase_blur", outputView, OptionalInt.empty(), null, OptionalDouble.empty())) {
            pass.setPipeline(blurPipeline);
            pass.setVertexBuffer(0, dummyVertexBuffer);
            pass.bindTexture("InputSampler", inputView, RenderSampler.linear());
            pass.setUniform("HandsFlameData", uniformBuffer.slice());
            pass.draw(0, 3);
        }
    }

    private static void renderCompositeCache(CommandEncoder encoder, GpuTextureView beforeColorView, GpuTextureView handMaskView) {
        try (var pass = encoder.createRenderPass(() -> "universalmod:hands_flame_composite_cache", smokeCacheView, OptionalInt.of(0), null, OptionalDouble.empty())) {
            pass.setPipeline(compositePipeline);
            pass.setVertexBuffer(0, dummyVertexBuffer);
            pass.bindTexture("SceneSampler", sceneTextureView, RenderSampler.linear());
            pass.bindTexture("BeforeSampler", beforeColorView, RenderSampler.linear());
            pass.bindTexture("TrailSampler", trailTextureViewA, RenderSampler.linear());
            pass.bindTexture("HandMaskSampler", handMaskView, RenderSampler.nearest());
            pass.setUniform("HandsFlameData", uniformBuffer.slice());
            pass.draw(0, 3);
        }
    }

    private static void renderCache(CommandEncoder encoder, RenderTarget target) {
        if (!cacheValid || smokeCacheView == null || cachePipeline == null) return;
        try (var pass = encoder.createRenderPass(() -> "universalmod:hands_flame_cache_blit", target.getColorTextureView(), OptionalInt.empty(), null, OptionalDouble.empty())) {
            pass.setPipeline(cachePipeline);
            pass.setVertexBuffer(0, dummyVertexBuffer);
            pass.bindTexture("CacheSampler", smokeCacheView, RenderSampler.linear());
            pass.draw(0, 3);
        }
    }

    private static boolean isUsable(RenderTarget target) {
        return target != null && target.getColorTexture() != null && target.getColorTextureView() != null && target.width > 0 && target.height > 0;
    }

    private static float flameTime() {
        return (System.nanoTime() % 180_000_000_000L) / 1_000_000_000.0f;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void disableAfterError(Throwable failure) {
        disabledAfterError = true;
        shutdown();
    }

    private static void closeTextures() {
        if (sceneTextureView != null) sceneTextureView.close();
        if (trailTextureViewA != null) trailTextureViewA.close();
        if (trailTextureViewB != null) trailTextureViewB.close();
        if (smokeCacheView != null) smokeCacheView.close();
        if (sceneTexture != null) sceneTexture.close();
        if (trailTextureA != null) trailTextureA.close();
        if (trailTextureB != null) trailTextureB.close();
        if (smokeCache != null) smokeCache.close();
        sceneTextureView = null;
        trailTextureViewA = null;
        trailTextureViewB = null;
        smokeCacheView = null;
        sceneTexture = null;
        trailTextureA = null;
        trailTextureB = null;
        smokeCache = null;
        lastWidth = -1;
        lastHeight = -1;
        cacheValid = false;
        cachedTime = 0.0f;
    }
}
