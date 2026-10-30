package universalmod.render.hand;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import universalmod.module.render.Hands;
import universalmod.render.RenderSampler;
import universalmod.render.ShaderRefreshGate;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

public final class HandsRenderer {
    private static final int MASK_UNIFORM_SIZE = 16;
    private static final int EFFECT_UNIFORM_SIZE = 48;
    private static final int BLUR_UNIFORM_SIZE = 32;
    private static final int OUTLINE_UNIFORM_SIZE = 64;

    private static final Identifier FULLSCREEN_VERTEX = Identifier.fromNamespaceAndPath("universalmod", "post/shaderhands/fullscreen");
    private static final Identifier MASK_SHADER = Identifier.fromNamespaceAndPath("universalmod", "post/shaderhands/mask");
    private static final Identifier WAVE_SHADER = Identifier.fromNamespaceAndPath("universalmod", "post/shaderhands/wave");
    private static final Identifier NEBULA_SHADER = Identifier.fromNamespaceAndPath("universalmod", "post/shaderhands/nebula");
    private static final Identifier OUTLINE_BLUR_SHADER = Identifier.fromNamespaceAndPath("universalmod", "post/shaderhands/outline_blur");
    private static final Identifier OUTLINE_COMPOSITE_SHADER = Identifier.fromNamespaceAndPath("universalmod", "post/shaderhands/outline_composite");
    private static final Identifier CACHE_BLIT_SHADER = Identifier.fromNamespaceAndPath("universalmod", "post/shaderhands/cache_blit");

    private static final Identifier MASK_PIPELINE_ID = Identifier.fromNamespaceAndPath("universalmod", "pipeline/post/hands/mask");
    private static final Identifier WAVE_PIPELINE_ID = Identifier.fromNamespaceAndPath("universalmod", "pipeline/post/hands/wave");
    private static final Identifier NEBULA_PIPELINE_ID = Identifier.fromNamespaceAndPath("universalmod", "pipeline/post/hands/nebula");
    private static final Identifier OUTLINE_BLUR_PIPELINE_ID = Identifier.fromNamespaceAndPath("universalmod", "pipeline/post/hands/outline_blur");
    private static final Identifier OUTLINE_COMPOSITE_PIPELINE_ID = Identifier.fromNamespaceAndPath("universalmod", "pipeline/post/hands/outline_composite");
    private static final Identifier CACHE_BLIT_PIPELINE_ID = Identifier.fromNamespaceAndPath("universalmod", "pipeline/post/hands/cache_blit");

    private static RenderPipeline maskPipeline;
    private static RenderPipeline wavePipeline;
    private static RenderPipeline nebulaPipeline;
    private static RenderPipeline outlineBlurPipeline;
    private static RenderPipeline outlineCompositePipeline;
    private static RenderPipeline cacheBlitPipeline;

    private static GpuBuffer maskUniformBuffer;
    private static GpuBuffer effectUniformBuffer;
    private static GpuBuffer blurUniformBuffer;
    private static GpuBuffer outlineUniformBuffer;
    private static GpuBuffer dummyVertexBuffer;
    private static ByteBuffer dummyVertexData;

    private static TextureTarget beforeTarget;
    private static TextureTarget maskTarget;
    private static TextureTarget blurTargetA;
    private static TextureTarget blurTargetB;
    private static TextureTarget effectCacheTarget;
    private static int width = -1;
    private static int height = -1;
    private static boolean frameActive;
    private static boolean smokeFrame;
    private static boolean updateEffectsThisFrame;
    private static boolean effectCacheValid;

    private HandsRenderer() {
    }

    public static void captureBeforeHands() {
        Hands module = Hands.getInstance();
        if (!Hands.isActive() || module == null) {
            frameActive = false;
            smokeFrame = false;
            updateEffectsThisFrame = false;
            HandsSmokeRenderer.setFlameEnabled(false);
            return;
        }

        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (!usable(main)) {
            frameActive = false;
            return;
        }

        boolean smoke = module.shouldRenderSmoke();
        boolean sizeChanged = main.width != width || main.height != height;
        boolean refreshDue = ShaderRefreshGate.shouldUpdate(ShaderRefreshGate.Channel.HANDS);
        boolean update = refreshDue || sizeChanged || (!smoke && !effectCacheValid) || (smoke && !HandsSmokeRenderer.hasPreparedTrail());

        try {
            ensureResources(module);
            if (!ensureTargets(main.width, main.height, smoke, module.shouldRenderOutline())) {
                frameActive = false;
                return;
            }
            if (update) {
                CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
                encoder.copyTextureToTexture(main.getDepthTexture(), beforeTarget.getDepthTexture(), 0, 0, 0, 0, 0, main.width, main.height);
                if (smoke) {
                    encoder.copyTextureToTexture(main.getColorTexture(), beforeTarget.getColorTexture(), 0, 0, 0, 0, 0, main.width, main.height);
                }
            }
            if (smoke) {
                HandsSmokeRenderer.setFlameEnabled(true);
                HandsSmokeRenderer.configure(
                        module.getSmokeStrength(),
                        module.getSmokeRiseSpeed(),
                        module.getSmokeWobble(),
                        module.getSmokeLength(),
                        module.getSmokeBrightness(),
                        1,
                        module.getSmokeResolvedColor()
                );
            } else {
                HandsSmokeRenderer.setFlameEnabled(false);
            }
            smokeFrame = smoke;
            updateEffectsThisFrame = update;
            frameActive = true;
        } catch (RuntimeException | LinkageError throwable) {
            frameActive = false;
            reset();
        }
    }

    public static void captureAfterHands() {
        Hands module = Hands.getInstance();
        if (!Hands.isActive() || !frameActive || module == null) {
            frameActive = false;
            return;
        }

        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (!usable(main) || main.width != width || main.height != height) {
            frameActive = false;
            return;
        }

        try {
            if (smokeFrame && module.shouldRenderSmoke()) {
                if (beforeTarget == null || maskTarget == null) return;
                if (updateEffectsThisFrame) renderMask(main);
                HandsSmokeRenderer.setFlameEnabled(true);
                HandsSmokeRenderer.configure(
                        module.getSmokeStrength(),
                        module.getSmokeRiseSpeed(),
                        module.getSmokeWobble(),
                        module.getSmokeLength(),
                        module.getSmokeBrightness(),
                        1,
                        module.getSmokeResolvedColor()
                );
                HandsSmokeRenderer.renderCapturedHandsFlame(
                        main,
                        beforeTarget.getColorTextureView(),
                        maskTarget.getColorTextureView(),
                        updateEffectsThisFrame
                );
            } else {
                HandsSmokeRenderer.setFlameEnabled(false);
                if (updateEffectsThisFrame) {
                    renderMask(main);
                    rebuildEffectCache(module);
                }
                renderEffectCache(main);
            }
        } catch (RuntimeException | LinkageError failure) {
            reset();
        } finally {
            frameActive = false;
        }
    }

    public static boolean hasCapturedHands() {
        return frameActive;
    }

    public static void reset() {
        destroyTargets();
        closeBuffers();
        maskPipeline = null;
        wavePipeline = null;
        nebulaPipeline = null;
        outlineBlurPipeline = null;
        outlineCompositePipeline = null;
        cacheBlitPipeline = null;
        frameActive = false;
        smokeFrame = false;
        updateEffectsThisFrame = false;
        effectCacheValid = false;
        ShaderRefreshGate.reset(ShaderRefreshGate.Channel.HANDS);
        HandsSmokeRenderer.reloadResources();
    }

    private static void ensureResources(Hands module) {
        if (maskPipeline == null) {
            maskPipeline = RenderPipelines.register(
                    RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                            .withLocation(MASK_PIPELINE_ID)
                            .withVertexShader(FULLSCREEN_VERTEX)
                            .withFragmentShader(MASK_SHADER)
                            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                            .withUniform("MaskData", UniformType.UNIFORM_BUFFER)
                            .withSampler("BeforeDepth")
                            .withSampler("AfterDepth")
                            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                            .withDepthWrite(false)
                            .withCull(false)
                            .build()
            );
        }
        if (cacheBlitPipeline == null) {
            cacheBlitPipeline = RenderPipelines.register(
                    RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                            .withLocation(CACHE_BLIT_PIPELINE_ID)
                            .withVertexShader(FULLSCREEN_VERTEX)
                            .withFragmentShader(CACHE_BLIT_SHADER)
                            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                            .withSampler("CacheSampler")
                            .withBlend(BlendFunction.TRANSLUCENT)
                            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                            .withDepthWrite(false)
                            .withCull(false)
                            .build()
            );
        }
        if (module.shouldRenderShader()) {
            if (wavePipeline == null) wavePipeline = buildEffectPipeline(WAVE_PIPELINE_ID, WAVE_SHADER);
            if (nebulaPipeline == null) nebulaPipeline = buildEffectPipeline(NEBULA_PIPELINE_ID, NEBULA_SHADER);
        }
        if (module.shouldRenderOutline()) {
            if (outlineBlurPipeline == null) {
                outlineBlurPipeline = RenderPipelines.register(
                        RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                                .withLocation(OUTLINE_BLUR_PIPELINE_ID)
                                .withVertexShader(FULLSCREEN_VERTEX)
                                .withFragmentShader(OUTLINE_BLUR_SHADER)
                                .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                                .withUniform("HandsBlurData", UniformType.UNIFORM_BUFFER)
                                .withSampler("SourceSampler")
                                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                                .withDepthWrite(false)
                                .withCull(false)
                                .build()
                );
            }
            if (outlineCompositePipeline == null) {
                outlineCompositePipeline = RenderPipelines.register(
                        RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                                .withLocation(OUTLINE_COMPOSITE_PIPELINE_ID)
                                .withVertexShader(FULLSCREEN_VERTEX)
                                .withFragmentShader(OUTLINE_COMPOSITE_SHADER)
                                .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                                .withUniform("HandsOutlineData", UniformType.UNIFORM_BUFFER)
                                .withSampler("MaskSampler")
                                .withSampler("BlurSampler")
                                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                                .withDepthWrite(false)
                                .withCull(false)
                                .build()
                );
            }
        }

        if (maskUniformBuffer == null || maskUniformBuffer.isClosed() || maskUniformBuffer.size() < MASK_UNIFORM_SIZE) {
            if (maskUniformBuffer != null) maskUniformBuffer.close();
            maskUniformBuffer = RenderSystem.getDevice().createBuffer(() -> "universalmod:hands_mask", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, MASK_UNIFORM_SIZE);
        }
        if (module.shouldRenderShader() && (effectUniformBuffer == null || effectUniformBuffer.isClosed() || effectUniformBuffer.size() < EFFECT_UNIFORM_SIZE)) {
            if (effectUniformBuffer != null) effectUniformBuffer.close();
            effectUniformBuffer = RenderSystem.getDevice().createBuffer(() -> "universalmod:hands_effect", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, EFFECT_UNIFORM_SIZE);
        }
        if (module.shouldRenderOutline()) {
            if (blurUniformBuffer == null || blurUniformBuffer.isClosed() || blurUniformBuffer.size() < BLUR_UNIFORM_SIZE) {
                if (blurUniformBuffer != null) blurUniformBuffer.close();
                blurUniformBuffer = RenderSystem.getDevice().createBuffer(() -> "universalmod:hands_blur", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, BLUR_UNIFORM_SIZE);
            }
            if (outlineUniformBuffer == null || outlineUniformBuffer.isClosed() || outlineUniformBuffer.size() < OUTLINE_UNIFORM_SIZE) {
                if (outlineUniformBuffer != null) outlineUniformBuffer.close();
                outlineUniformBuffer = RenderSystem.getDevice().createBuffer(() -> "universalmod:hands_outline", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, OUTLINE_UNIFORM_SIZE);
            }
        }
        if (dummyVertexBuffer == null || dummyVertexBuffer.isClosed()) {
            if (dummyVertexData == null) {
                dummyVertexData = MemoryUtil.memAlloc(4);
                dummyVertexData.putInt(0);
                dummyVertexData.flip();
            }
            dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "universalmod:hands_dummy_vertex",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                    dummyVertexData
            );
        }
    }

    private static RenderPipeline buildEffectPipeline(Identifier pipelineId, Identifier shader) {
        return RenderPipelines.register(
                RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                        .withLocation(pipelineId)
                        .withVertexShader(FULLSCREEN_VERTEX)
                        .withFragmentShader(shader)
                        .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                        .withUniform("HandsEffectData", UniformType.UNIFORM_BUFFER)
                        .withSampler("MaskSampler")
                        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                        .withDepthWrite(false)
                        .withCull(false)
                        .build()
        );
    }

    private static boolean ensureTargets(int nextWidth, int nextHeight, boolean smoke, boolean outline) {
        if (nextWidth != width || nextHeight != height) {
            destroyTargets();
            width = nextWidth;
            height = nextHeight;
        }
        if (beforeTarget == null) beforeTarget = new TextureTarget("UNIVERSALMOD_hands_before", nextWidth, nextHeight, true);
        if (maskTarget == null) maskTarget = new TextureTarget("UNIVERSALMOD_hands_mask", nextWidth, nextHeight, false);

        if (outline && !smoke) {
            if (blurTargetA == null) blurTargetA = new TextureTarget("UNIVERSALMOD_hands_blur_a", nextWidth, nextHeight, false);
            if (blurTargetB == null) blurTargetB = new TextureTarget("UNIVERSALMOD_hands_blur_b", nextWidth, nextHeight, false);
        } else {
            destroyTarget(blurTargetA);
            destroyTarget(blurTargetB);
            blurTargetA = null;
            blurTargetB = null;
        }

        if (smoke) {
            destroyTarget(effectCacheTarget);
            effectCacheTarget = null;
            effectCacheValid = false;
        } else if (effectCacheTarget == null) {
            effectCacheTarget = new TextureTarget("UNIVERSALMOD_hands_effect_cache", nextWidth, nextHeight, false);
            effectCacheValid = false;
        }

        if (beforeTarget.getDepthTextureView() == null || maskTarget.getColorTextureView() == null) return false;
        if (smoke && beforeTarget.getColorTextureView() == null) return false;
        if (!smoke && effectCacheTarget != null && effectCacheTarget.getColorTextureView() == null) return false;
        if (outline && !smoke && (blurTargetA == null || blurTargetB == null || blurTargetA.getColorTextureView() == null || blurTargetB.getColorTextureView() == null)) return false;
        return true;
    }

    private static void renderMask(RenderTarget main) {
        if (beforeTarget == null || maskTarget == null || main.getDepthTextureView() == null) return;
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.calloc(MASK_UNIFORM_SIZE);
            data.putFloat(0, 1.0F / Math.max(width, 1));
            data.putFloat(4, 1.0F / Math.max(height, 1));
            data.putFloat(8, 0.0F);
            data.putFloat(12, 0.0F);
            data.position(0);
            encoder.writeToBuffer(maskUniformBuffer.slice(0, MASK_UNIFORM_SIZE), data);
            try (RenderPass pass = encoder.createRenderPass(() -> "universalmod:hands_mask", maskTarget.getColorTextureView(), OptionalInt.of(0))) {
                pass.setPipeline(maskPipeline);
                pass.setVertexBuffer(0, dummyVertexBuffer);
                pass.bindTexture("BeforeDepth", beforeTarget.getDepthTextureView(), RenderSampler.nearest());
                pass.bindTexture("AfterDepth", main.getDepthTextureView(), RenderSampler.nearest());
                pass.setUniform("MaskData", maskUniformBuffer);
                pass.draw(0, 3);
            }
        }
    }

    private static void rebuildEffectCache(Hands module) {
        if (effectCacheTarget == null || effectCacheTarget.getColorTexture() == null) return;
        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(effectCacheTarget.getColorTexture(), 0);
        if (module.shouldRenderShader()) renderShader(effectCacheTarget, module);
        if (module.shouldRenderOutline()) renderOutline(effectCacheTarget, module);
        effectCacheValid = true;
    }

    private static void renderEffectCache(RenderTarget main) {
        if (!effectCacheValid || effectCacheTarget == null || effectCacheTarget.getColorTextureView() == null || cacheBlitPipeline == null) return;
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (RenderPass pass = encoder.createRenderPass(() -> "universalmod:hands_cache_blit", main.getColorTextureView(), OptionalInt.empty())) {
            pass.setPipeline(cacheBlitPipeline);
            pass.setVertexBuffer(0, dummyVertexBuffer);
            pass.bindTexture("CacheSampler", effectCacheTarget.getColorTextureView(), RenderSampler.linear());
            pass.draw(0, 3);
        }
    }

    private static void renderShader(RenderTarget target, Hands module) {
        if (maskTarget == null || target == null || target.getColorTextureView() == null || effectUniformBuffer == null) return;
        RenderPipeline pipeline = module.getShaderMode().equalsIgnoreCase("Nebula") ? nebulaPipeline : wavePipeline;
        if (pipeline == null) return;

        int color = module.getShaderColor();
        float speed = module.getWaveSpeed() / 100.0F;
        float scale = module.getWaveScale() / 100.0F;
        float fill = module.getFill() / 100.0F;
        float alpha = module.getAlpha() / 100.0F;

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.calloc(EFFECT_UNIFORM_SIZE);
            data.putFloat(0, 1.0F / Math.max(width, 1));
            data.putFloat(4, 1.0F / Math.max(height, 1));
            data.putFloat(8, ((color >> 16) & 0xFF) / 255.0F);
            data.putFloat(12, ((color >> 8) & 0xFF) / 255.0F);
            data.putFloat(16, (color & 0xFF) / 255.0F);
            data.putFloat(20, (System.currentTimeMillis() % 100000L) / 1000.0F);
            data.putFloat(24, speed);
            data.putFloat(28, scale);
            data.putFloat(32, fill);
            data.putFloat(36, alpha);
            data.position(0);
            encoder.writeToBuffer(effectUniformBuffer.slice(0, EFFECT_UNIFORM_SIZE), data);
            try (RenderPass pass = encoder.createRenderPass(() -> "universalmod:hands_shader_cache", target.getColorTextureView(), OptionalInt.empty())) {
                pass.setPipeline(pipeline);
                pass.setVertexBuffer(0, dummyVertexBuffer);
                pass.bindTexture("MaskSampler", maskTarget.getColorTextureView(), RenderSampler.linear());
                pass.setUniform("HandsEffectData", effectUniformBuffer);
                pass.draw(0, 3);
            }
        }
    }

    private static void renderOutline(RenderTarget target, Hands module) {
        if (maskTarget == null || blurTargetA == null || blurTargetB == null || target == null || target.getColorTextureView() == null || blurUniformBuffer == null || outlineUniformBuffer == null) return;
        renderBlurPass(maskTarget, blurTargetA, module.getOutlineRadius(), true);
        renderBlurPass(blurTargetA, blurTargetB, module.getOutlineRadius(), false);

        int topColor = module.getOutlineTopColor();
        int bottomColor = module.getOutlineBottomColor();
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.calloc(OUTLINE_UNIFORM_SIZE);
            data.putFloat(0, 1.0F / Math.max(width, 1));
            data.putFloat(4, 1.0F / Math.max(height, 1));
            data.putFloat(8, module.getOutlineWidth());
            data.putFloat(12, module.getGlowStrength());
            data.putFloat(16, 1.35F);
            data.putFloat(20, module.getOutlineOpacity());
            data.putFloat(24, (System.nanoTime() % 30_000_000_000L) / 1.0E9F);
            data.putFloat(28, module.isOutlineStatic() ? 1.0F : 0.0F);
            data.putFloat(32, ((topColor >> 16) & 0xFF) / 255.0F);
            data.putFloat(36, ((topColor >> 8) & 0xFF) / 255.0F);
            data.putFloat(40, (topColor & 0xFF) / 255.0F);
            data.putFloat(44, ((topColor >> 24) & 0xFF) / 255.0F);
            data.putFloat(48, ((bottomColor >> 16) & 0xFF) / 255.0F);
            data.putFloat(52, ((bottomColor >> 8) & 0xFF) / 255.0F);
            data.putFloat(56, (bottomColor & 0xFF) / 255.0F);
            data.putFloat(60, ((bottomColor >> 24) & 0xFF) / 255.0F);
            data.position(0);
            encoder.writeToBuffer(outlineUniformBuffer.slice(0, OUTLINE_UNIFORM_SIZE), data);
            try (RenderPass pass = encoder.createRenderPass(() -> "universalmod:hands_outline_cache", target.getColorTextureView(), OptionalInt.empty())) {
                pass.setPipeline(outlineCompositePipeline);
                pass.setVertexBuffer(0, dummyVertexBuffer);
                pass.bindTexture("MaskSampler", maskTarget.getColorTextureView(), RenderSampler.linear());
                pass.bindTexture("BlurSampler", blurTargetB.getColorTextureView(), RenderSampler.linear());
                pass.setUniform("HandsOutlineData", outlineUniformBuffer);
                pass.draw(0, 3);
            }
        }
    }

    private static void renderBlurPass(TextureTarget source, TextureTarget target, float radius, boolean horizontal) {
        if (source == null || target == null || source.getColorTextureView() == null || target.getColorTextureView() == null || blurUniformBuffer == null) return;
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.calloc(BLUR_UNIFORM_SIZE);
            data.putFloat(0, 1.0F / Math.max(width, 1));
            data.putFloat(4, 1.0F / Math.max(height, 1));
            data.putFloat(8, horizontal ? 1.0F : 0.0F);
            data.putFloat(12, horizontal ? 0.0F : 1.0F);
            data.putFloat(16, radius);
            data.position(0);
            encoder.writeToBuffer(blurUniformBuffer.slice(0, BLUR_UNIFORM_SIZE), data);
            try (RenderPass pass = encoder.createRenderPass(() -> "universalmod:hands_outline_blur", target.getColorTextureView(), OptionalInt.of(0))) {
                pass.setPipeline(outlineBlurPipeline);
                pass.setVertexBuffer(0, dummyVertexBuffer);
                pass.bindTexture("SourceSampler", source.getColorTextureView(), RenderSampler.linear());
                pass.setUniform("HandsBlurData", blurUniformBuffer);
                pass.draw(0, 3);
            }
        }
    }

    private static boolean usable(RenderTarget target) {
        return target != null
                && target.getColorTexture() != null
                && target.getColorTextureView() != null
                && target.getDepthTexture() != null
                && target.getDepthTextureView() != null
                && target.width > 0
                && target.height > 0;
    }

    private static void destroyTarget(TextureTarget target) {
        if (target != null) target.destroyBuffers();
    }

    private static void destroyTargets() {
        destroyTarget(beforeTarget);
        destroyTarget(maskTarget);
        destroyTarget(blurTargetA);
        destroyTarget(blurTargetB);
        destroyTarget(effectCacheTarget);
        beforeTarget = null;
        maskTarget = null;
        blurTargetA = null;
        blurTargetB = null;
        effectCacheTarget = null;
        width = -1;
        height = -1;
        effectCacheValid = false;
    }

    private static void closeBuffers() {
        if (maskUniformBuffer != null) maskUniformBuffer.close();
        if (effectUniformBuffer != null) effectUniformBuffer.close();
        if (blurUniformBuffer != null) blurUniformBuffer.close();
        if (outlineUniformBuffer != null) outlineUniformBuffer.close();
        if (dummyVertexBuffer != null) dummyVertexBuffer.close();
        if (dummyVertexData != null) MemoryUtil.memFree(dummyVertexData);
        maskUniformBuffer = null;
        effectUniformBuffer = null;
        blurUniformBuffer = null;
        outlineUniformBuffer = null;
        dummyVertexBuffer = null;
        dummyVertexData = null;
    }
}
