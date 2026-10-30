package universalmod.render.hand;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import universalmod.module.render.Hands;
import universalmod.render.ShaderRefreshGate;

public final class HandsRenderer {
    private static final String SHADER_ROOT = "/assets/universalmod/shaders/post/shaderhands/";
    private static HandsGl.Target captureTarget;
    private static HandsGl.Target maskTarget;
    private static HandsGl.Target blurTargetA;
    private static HandsGl.Target blurTargetB;
    private static HandsGl.Target effectCacheTarget;
    private static int maskProgram;
    private static int waveProgram;
    private static int nebulaProgram;
    private static int outlineBlurProgram;
    private static int outlineCompositeProgram;
    private static int cacheBlitProgram;
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
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer main = client == null ? null : client.getFramebuffer();
        if (!usable(main)) {
            frameActive = false;
            return;
        }
        boolean smoke = module.shouldRenderSmoke();
        boolean sizeChanged = main.textureWidth != width || main.textureHeight != height;
        boolean refreshDue = ShaderRefreshGate.shouldUpdate(ShaderRefreshGate.Channel.HANDS);
        boolean update = refreshDue || sizeChanged || (!smoke && !effectCacheValid) || (smoke && !HandsSmokeRenderer.hasPreparedTrail());
        try {
            ensurePrograms(module);
            ensureTargets(main.textureWidth, main.textureHeight, smoke, module.shouldRenderOutline());
            if (smoke) {
                if (update) HandsGl.copy(main, captureTarget, true, true);
                HandsSmokeRenderer.setFlameEnabled(true);
                HandsSmokeRenderer.configure(module.getSmokeStrength(), module.getSmokeRiseSpeed(), module.getSmokeWobble(), module.getSmokeLength(), module.getSmokeBrightness(), 1, module.getSmokeResolvedColor());
            } else if (update) {
                HandsGl.copy(main, captureTarget, false, true);
                HandsSmokeRenderer.setFlameEnabled(false);
            } else {
                HandsSmokeRenderer.setFlameEnabled(false);
            }
            smokeFrame = smoke;
            updateEffectsThisFrame = update;
            frameActive = true;
        } catch (Throwable throwable) {
            frameActive = false;
            reset();
        } finally {
            main.beginWrite(false);
        }
    }

    public static void captureAfterHands() {
        Hands module = Hands.getInstance();
        if (!Hands.isActive() || !frameActive || module == null) {
            frameActive = false;
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer main = client == null ? null : client.getFramebuffer();
        if (!usable(main) || main.textureWidth != width || main.textureHeight != height) {
            frameActive = false;
            return;
        }
        try {
            if (smokeFrame && module.shouldRenderSmoke()) {
                if (captureTarget == null || maskTarget == null) return;
                if (updateEffectsThisFrame) renderMask(main);
                HandsSmokeRenderer.setFlameEnabled(true);
                HandsSmokeRenderer.configure(module.getSmokeStrength(), module.getSmokeRiseSpeed(), module.getSmokeWobble(), module.getSmokeLength(), module.getSmokeBrightness(), 1, module.getSmokeResolvedColor());
                HandsSmokeRenderer.renderCapturedHandsFlame(main, captureTarget.colorTexture, maskTarget.colorTexture, updateEffectsThisFrame);
            } else {
                HandsSmokeRenderer.setFlameEnabled(false);
                if (updateEffectsThisFrame) {
                    if (captureTarget == null || maskTarget == null || effectCacheTarget == null) return;
                    renderMask(main);
                    rebuildEffectCache(module);
                }
                renderEffectCache(main);
            }
        } catch (Throwable throwable) {
            reset();
        } finally {
            frameActive = false;
            main.beginWrite(false);
        }
    }

    public static boolean hasCapturedHands() {
        return frameActive;
    }

    public static void reset() {
        destroyTargets();
        deletePrograms();
        frameActive = false;
        smokeFrame = false;
        updateEffectsThisFrame = false;
        effectCacheValid = false;
        ShaderRefreshGate.reset(ShaderRefreshGate.Channel.HANDS);
        HandsSmokeRenderer.reloadResources();
    }

    private static void ensurePrograms(Hands module) {
        if (maskProgram == 0) maskProgram = HandsGl.program(SHADER_ROOT + "fullscreen.vsh", SHADER_ROOT + "mask.fsh", "hands mask");
        if (cacheBlitProgram == 0) cacheBlitProgram = HandsGl.program(SHADER_ROOT + "fullscreen.vsh", SHADER_ROOT + "cache_blit.fsh", "hands cache blit");
        if (module.shouldRenderShader()) {
            if (waveProgram == 0) waveProgram = HandsGl.program(SHADER_ROOT + "fullscreen.vsh", SHADER_ROOT + "wave.fsh", "hands wave");
            if (nebulaProgram == 0) nebulaProgram = HandsGl.program(SHADER_ROOT + "fullscreen.vsh", SHADER_ROOT + "nebula.fsh", "hands nebula");
        }
        if (module.shouldRenderOutline()) {
            if (outlineBlurProgram == 0) outlineBlurProgram = HandsGl.program(SHADER_ROOT + "fullscreen.vsh", SHADER_ROOT + "outline_blur.fsh", "hands outline blur");
            if (outlineCompositeProgram == 0) outlineCompositeProgram = HandsGl.program(SHADER_ROOT + "fullscreen.vsh", SHADER_ROOT + "outline_composite.fsh", "hands outline composite");
        }
    }

    private static void ensureTargets(int nextWidth, int nextHeight, boolean smoke, boolean outline) {
        int captureColor = smoke ? HandsGl.RGBA8 : HandsGl.NO_COLOR;
        captureTarget = HandsGl.target(captureTarget, nextWidth, nextHeight, captureColor, true, GL11.GL_NEAREST);
        maskTarget = HandsGl.target(maskTarget, nextWidth, nextHeight, HandsGl.R8, false, GL11.GL_LINEAR);
        if (outline && !smoke) {
            blurTargetA = HandsGl.target(blurTargetA, nextWidth, nextHeight, HandsGl.R8, false, GL11.GL_LINEAR);
            blurTargetB = HandsGl.target(blurTargetB, nextWidth, nextHeight, HandsGl.R8, false, GL11.GL_LINEAR);
        } else {
            HandsGl.delete(blurTargetA);
            HandsGl.delete(blurTargetB);
            blurTargetA = null;
            blurTargetB = null;
        }
        if (smoke) {
            HandsGl.delete(effectCacheTarget);
            effectCacheTarget = null;
            effectCacheValid = false;
        } else {
            effectCacheTarget = HandsGl.target(effectCacheTarget, nextWidth, nextHeight, HandsGl.RGBA8, false, GL11.GL_LINEAR);
        }
        if (width != nextWidth || height != nextHeight) effectCacheValid = false;
        width = nextWidth;
        height = nextHeight;
    }

    private static void renderMask(Framebuffer main) {
        if (captureTarget == null || maskTarget == null || main == null) return;
        try (HandsGl.State state = HandsGl.state(2)) {
            HandsGl.prepareTarget(maskTarget, true, false);
            GL20.glUseProgram(maskProgram);
            HandsGl.texture(0, captureTarget.depthTexture);
            HandsGl.texture(1, main.getDepthAttachment());
            HandsGl.i(maskProgram, "BeforeDepth", 0);
            HandsGl.i(maskProgram, "AfterDepth", 1);
            HandsGl.drawFullscreen();
        }
    }

    private static void rebuildEffectCache(Hands module) {
        if (effectCacheTarget == null) return;
        try (HandsGl.State state = HandsGl.state(1)) {
            HandsGl.prepareTarget(effectCacheTarget, true, false);
        }
        if (module.shouldRenderShader()) renderShader(effectCacheTarget, module);
        if (module.shouldRenderOutline()) renderOutline(effectCacheTarget, module);
        effectCacheValid = true;
    }

    private static void renderEffectCache(Framebuffer main) {
        if (!effectCacheValid || effectCacheTarget == null || cacheBlitProgram == 0) return;
        try (HandsGl.State state = HandsGl.state(1)) {
            HandsGl.prepareTarget(main, false, true);
            GL20.glUseProgram(cacheBlitProgram);
            HandsGl.texture(0, effectCacheTarget.colorTexture);
            HandsGl.i(cacheBlitProgram, "CacheSampler", 0);
            HandsGl.drawFullscreen();
        }
    }

    private static void renderShader(HandsGl.Target target, Hands module) {
        if (maskTarget == null || target == null) return;
        int program = "Nebula".equalsIgnoreCase(module.getShaderMode()) ? nebulaProgram : waveProgram;
        if (program == 0) return;
        int color = module.getShaderColor();
        float speed = module.getWaveSpeed() / 100.0F;
        float scale = module.getWaveScale() / 100.0F;
        float fill = module.getFill() / 100.0F;
        float alpha = module.getAlpha() / 100.0F;
        HandsGl.filterTexture(maskTarget.colorTexture, GL11.GL_LINEAR);
        try (HandsGl.State state = HandsGl.state(1)) {
            HandsGl.prepareTarget(target, false, true);
            GL20.glUseProgram(program);
            HandsGl.texture(0, maskTarget.colorTexture);
            HandsGl.i(program, "MaskSampler", 0);
            HandsGl.f4(program, "TexelAndColor", 1.0F / Math.max(width, 1), 1.0F / Math.max(height, 1), ((color >> 16) & 255) / 255.0F, ((color >> 8) & 255) / 255.0F);
            HandsGl.f4(program, "BlueTimeSpeedScale", (color & 255) / 255.0F, (System.currentTimeMillis() % 100000L) / 1000.0F, speed, scale);
            HandsGl.f4(program, "FillAlphaReserved", fill, alpha, 0.0F, 0.0F);
            HandsGl.drawFullscreen();
        }
    }

    private static void renderOutline(HandsGl.Target target, Hands module) {
        if (maskTarget == null || blurTargetA == null || blurTargetB == null || target == null) return;
        HandsGl.filterTexture(maskTarget.colorTexture, GL11.GL_LINEAR);
        renderBlurPass(maskTarget, blurTargetA, module.getOutlineRadius(), true);
        renderBlurPass(blurTargetA, blurTargetB, module.getOutlineRadius(), false);
        int topColor = module.getOutlineTopColor();
        int bottomColor = module.getOutlineBottomColor();
        try (HandsGl.State state = HandsGl.state(2)) {
            HandsGl.prepareTarget(target, false, true);
            GL20.glUseProgram(outlineCompositeProgram);
            HandsGl.texture(0, maskTarget.colorTexture);
            HandsGl.texture(1, blurTargetB.colorTexture);
            HandsGl.i(outlineCompositeProgram, "MaskSampler", 0);
            HandsGl.i(outlineCompositeProgram, "BlurSampler", 1);
            HandsGl.f4(outlineCompositeProgram, "TexelOutlineGlow", 1.0F / Math.max(width, 1), 1.0F / Math.max(height, 1), module.getOutlineWidth(), module.getGlowStrength());
            HandsGl.f4(outlineCompositeProgram, "StrengthOpacityTimeStyle", 1.35F, module.getOutlineOpacity(), (System.nanoTime() % 30_000_000_000L) / 1.0E9F, module.isOutlineStatic() ? 1.0F : 0.0F);
            HandsGl.f4(outlineCompositeProgram, "ColorTop", ((topColor >> 16) & 255) / 255.0F, ((topColor >> 8) & 255) / 255.0F, (topColor & 255) / 255.0F, ((topColor >>> 24) & 255) / 255.0F);
            HandsGl.f4(outlineCompositeProgram, "ColorBottom", ((bottomColor >> 16) & 255) / 255.0F, ((bottomColor >> 8) & 255) / 255.0F, (bottomColor & 255) / 255.0F, ((bottomColor >>> 24) & 255) / 255.0F);
            HandsGl.drawFullscreen();
        }
    }

    private static void renderBlurPass(HandsGl.Target source, HandsGl.Target target, float radius, boolean horizontal) {
        if (source == null || target == null) return;
        try (HandsGl.State state = HandsGl.state(1)) {
            HandsGl.prepareTarget(target, true, false);
            GL20.glUseProgram(outlineBlurProgram);
            HandsGl.texture(0, source.colorTexture);
            HandsGl.i(outlineBlurProgram, "SourceSampler", 0);
            HandsGl.f4(outlineBlurProgram, "TexelAndDirection", 1.0F / Math.max(width, 1), 1.0F / Math.max(height, 1), horizontal ? 1.0F : 0.0F, horizontal ? 0.0F : 1.0F);
            HandsGl.f4(outlineBlurProgram, "RadiusData", radius, 0.0F, 0.0F, 0.0F);
            HandsGl.drawFullscreen();
        }
    }

    private static boolean usable(Framebuffer framebuffer) {
        return framebuffer != null && framebuffer.textureWidth > 0 && framebuffer.textureHeight > 0 && framebuffer.getColorAttachment() > 0 && framebuffer.getDepthAttachment() > 0;
    }

    private static void destroyTargets() {
        HandsGl.delete(captureTarget);
        HandsGl.delete(maskTarget);
        HandsGl.delete(blurTargetA);
        HandsGl.delete(blurTargetB);
        HandsGl.delete(effectCacheTarget);
        captureTarget = null;
        maskTarget = null;
        blurTargetA = null;
        blurTargetB = null;
        effectCacheTarget = null;
        width = -1;
        height = -1;
    }

    private static void deletePrograms() {
        HandsGl.deleteProgram(maskProgram);
        HandsGl.deleteProgram(waveProgram);
        HandsGl.deleteProgram(nebulaProgram);
        HandsGl.deleteProgram(outlineBlurProgram);
        HandsGl.deleteProgram(outlineCompositeProgram);
        HandsGl.deleteProgram(cacheBlitProgram);
        maskProgram = 0;
        waveProgram = 0;
        nebulaProgram = 0;
        outlineBlurProgram = 0;
        outlineCompositeProgram = 0;
        cacheBlitProgram = 0;
    }
}
