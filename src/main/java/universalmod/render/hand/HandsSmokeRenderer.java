package universalmod.render.hand;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

public final class HandsSmokeRenderer {
    private static final String SHADER_ROOT = "/assets/universalmod/shaders/effects/hands_flame/";
    private static boolean flameEnabled;
    private static float flameStrength = 0.85F;
    private static float flameRiseSpeed;
    private static float flameWobble = 0.65F;
    private static float flameLength = 0.95F;
    private static float flameBrightness = 0.9F;
    private static int flameColorMode = 1;
    private static int flameColor = 0xE6FFFFFF;
    private static int trailProgram;
    private static int blurProgram;
    private static int compositeProgram;
    private static int cacheProgram;
    private static HandsGl.Target sceneTexture;
    private static HandsGl.Target trailTextureA;
    private static HandsGl.Target trailTextureB;
    private static HandsGl.Target smokeCache;
    private static boolean cacheValid;
    private static float cachedTime;
    private static int lastWidth = -1;
    private static int lastHeight = -1;
    private static boolean disabledAfterError;

    private HandsSmokeRenderer() {
    }

    public static void renderCapturedHandsFlame(Framebuffer target, int beforeColorTexture, int handMaskTexture, boolean updateEffects) {
        if (!shouldRenderFlame() || target == null || !usable(target) || !ensureReady(target.textureWidth, target.textureHeight)) return;
        try {
            if (updateEffects || !cacheValid) {
                if (beforeColorTexture <= 0 || handMaskTexture <= 0) return;
                cachedTime = flameTime() * 1.2F;
                renderTrail(target, beforeColorTexture, handMaskTexture);
                renderKawaseBlur(trailTextureA, trailTextureB, 0.75F);
                renderKawaseBlur(trailTextureB, trailTextureA, 1.55F);
                renderKawaseBlur(trailTextureA, trailTextureB, 2.45F);
                renderKawaseBlur(trailTextureB, trailTextureA, 3.35F);
                HandsGl.copy(target, sceneTexture, true, false);
                renderCompositeCache(beforeColorTexture, handMaskTexture);
                cacheValid = true;
            }
            renderCache(target);
        } catch (Throwable throwable) {
            disableAfterError();
        } finally {
            target.beginWrite(false);
        }
    }

    public static boolean hasPreparedTrail() {
        return cacheValid;
    }

    public static void resetTrail() {
        cacheValid = false;
        cachedTime = 0.0F;
        if (trailTextureA == null || trailTextureB == null || smokeCache == null) return;
        try (HandsGl.State state = HandsGl.state(1)) {
            HandsGl.prepareTarget(trailTextureA, true, false);
            HandsGl.prepareTarget(trailTextureB, true, false);
            HandsGl.prepareTarget(smokeCache, true, false);
        } catch (Throwable throwable) {
            disableAfterError();
        }
    }

    public static void shutdown() {
        closeTextures();
        HandsGl.deleteProgram(trailProgram);
        HandsGl.deleteProgram(blurProgram);
        HandsGl.deleteProgram(compositeProgram);
        HandsGl.deleteProgram(cacheProgram);
        trailProgram = 0;
        blurProgram = 0;
        compositeProgram = 0;
        cacheProgram = 0;
        cacheValid = false;
        cachedTime = 0.0F;
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
            cachedTime = 0.0F;
        }
    }

    public static void configure(float strength, float riseSpeed, float wobble, float length, float brightness, int colorMode, int color) {
        flameStrength = clamp(strength, 0.0F, 2.0F);
        flameRiseSpeed = clamp(riseSpeed, 0.0F, 2.0F);
        flameWobble = clamp(wobble, 0.0F, 2.0F);
        flameLength = clamp(length, 0.1F, 2.5F);
        flameBrightness = clamp(brightness, 0.0F, 2.0F);
        flameColorMode = Math.max(0, Math.min(2, colorMode));
        flameColor = color;
    }

    public static boolean shouldRenderFlame() {
        if (!flameEnabled || disabledAfterError) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.player != null && client.world != null;
    }

    private static boolean ensureReady(int width, int height) {
        if (trailProgram == 0 || blurProgram == 0 || compositeProgram == 0 || cacheProgram == 0) initPrograms();
        if (disabledAfterError) return false;
        ensureTextures(width, height);
        return trailProgram != 0 && blurProgram != 0 && compositeProgram != 0 && cacheProgram != 0
                && sceneTexture != null && trailTextureA != null && trailTextureB != null && smokeCache != null;
    }

    private static void initPrograms() {
        try {
            if (trailProgram == 0) trailProgram = HandsGl.program(SHADER_ROOT + "fullscreen.vsh", SHADER_ROOT + "trail.fsh", "hands smoke trail");
            if (blurProgram == 0) blurProgram = HandsGl.program(SHADER_ROOT + "fullscreen.vsh", SHADER_ROOT + "kawase_blur.fsh", "hands smoke blur");
            if (compositeProgram == 0) compositeProgram = HandsGl.program(SHADER_ROOT + "fullscreen.vsh", SHADER_ROOT + "composite.fsh", "hands smoke composite");
            if (cacheProgram == 0) cacheProgram = HandsGl.program(SHADER_ROOT + "fullscreen.vsh", SHADER_ROOT + "cache_blit.fsh", "hands smoke cache");
        } catch (Throwable throwable) {
            disableAfterError();
        }
    }

    private static void ensureTextures(int width, int height) {
        if (sceneTexture != null && smokeCache != null && width == lastWidth && height == lastHeight) return;
        closeTextures();
        int trailWidth = Math.max(1, width / 2);
        int trailHeight = Math.max(1, height / 2);
        sceneTexture = HandsGl.target(null, width, height, HandsGl.RGBA8, false, GL11.GL_LINEAR);
        smokeCache = HandsGl.target(null, width, height, HandsGl.RGBA8, false, GL11.GL_LINEAR);
        trailTextureA = HandsGl.target(null, trailWidth, trailHeight, HandsGl.RGBA8, false, GL11.GL_LINEAR);
        trailTextureB = HandsGl.target(null, trailWidth, trailHeight, HandsGl.RGBA8, false, GL11.GL_LINEAR);
        lastWidth = width;
        lastHeight = height;
        resetTrail();
    }

    private static void renderTrail(Framebuffer target, int beforeColorTexture, int handMaskTexture) {
        HandsGl.filterTexture(handMaskTexture, GL11.GL_NEAREST);
        try (HandsGl.State state = HandsGl.state(3)) {
            HandsGl.prepareTarget(trailTextureA, true, false);
            GL20.glUseProgram(trailProgram);
            HandsGl.texture(0, beforeColorTexture);
            HandsGl.texture(1, target.getColorAttachment());
            HandsGl.texture(2, handMaskTexture);
            HandsGl.i(trailProgram, "BeforeSampler", 0);
            HandsGl.i(trailProgram, "AfterSampler", 1);
            HandsGl.i(trailProgram, "HandMaskSampler", 2);
            writeUniforms(trailProgram, 1.0F);
            HandsGl.drawFullscreen();
        }
    }

    private static void renderKawaseBlur(HandsGl.Target input, HandsGl.Target output, float offset) {
        try (HandsGl.State state = HandsGl.state(1)) {
            HandsGl.prepareTarget(output, false, false);
            GL20.glUseProgram(blurProgram);
            HandsGl.texture(0, input.colorTexture);
            HandsGl.i(blurProgram, "InputSampler", 0);
            writeUniforms(blurProgram, offset);
            HandsGl.drawFullscreen();
        }
    }

    private static void renderCompositeCache(int beforeColorTexture, int handMaskTexture) {
        HandsGl.filterTexture(handMaskTexture, GL11.GL_NEAREST);
        try (HandsGl.State state = HandsGl.state(4)) {
            HandsGl.prepareTarget(smokeCache, true, false);
            GL20.glUseProgram(compositeProgram);
            HandsGl.texture(0, sceneTexture.colorTexture);
            HandsGl.texture(1, beforeColorTexture);
            HandsGl.texture(2, trailTextureA.colorTexture);
            HandsGl.texture(3, handMaskTexture);
            HandsGl.i(compositeProgram, "SceneSampler", 0);
            HandsGl.i(compositeProgram, "BeforeSampler", 1);
            HandsGl.i(compositeProgram, "TrailSampler", 2);
            HandsGl.i(compositeProgram, "HandMaskSampler", 3);
            writeUniforms(compositeProgram, 1.0F);
            HandsGl.drawFullscreen();
        }
    }

    private static void renderCache(Framebuffer target) {
        if (!cacheValid || smokeCache == null || cacheProgram == 0) return;
        try (HandsGl.State state = HandsGl.state(1)) {
            HandsGl.prepareTarget(target, false, true);
            GL20.glUseProgram(cacheProgram);
            HandsGl.texture(0, smokeCache.colorTexture);
            HandsGl.i(cacheProgram, "CacheSampler", 0);
            HandsGl.drawFullscreen();
        }
    }

    private static void writeUniforms(int program, float kawaseOffset) {
        int color = flameColor;
        float alpha = ((color >>> 24) & 255) / 255.0F;
        HandsGl.f4(program, "flameColor", ((color >> 16) & 255) / 255.0F, ((color >> 8) & 255) / 255.0F, (color & 255) / 255.0F, alpha);
        HandsGl.f4(program, "params0", flameStrength, flameRiseSpeed, clamp(flameWobble * 1.2F, 0.0F, 2.4F), flameLength);
        HandsGl.f4(program, "params1", flameBrightness, cachedTime, flameColorMode, alpha);
        HandsGl.f4(program, "screen", lastWidth, lastHeight, 1.0F / Math.max(lastWidth, 1), 1.0F / Math.max(lastHeight, 1));
        HandsGl.f4(program, "smokeMotion", 0.0F, 0.0F, 0.0F, kawaseOffset);
    }

    private static boolean usable(Framebuffer framebuffer) {
        return framebuffer.textureWidth > 0 && framebuffer.textureHeight > 0 && framebuffer.getColorAttachment() > 0;
    }

    private static float flameTime() {
        return (System.nanoTime() % 180_000_000_000L) / 1_000_000_000.0F;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void disableAfterError() {
        disabledAfterError = true;
        shutdown();
    }

    private static void closeTextures() {
        HandsGl.delete(sceneTexture);
        HandsGl.delete(trailTextureA);
        HandsGl.delete(trailTextureB);
        HandsGl.delete(smokeCache);
        sceneTexture = null;
        trailTextureA = null;
        trailTextureB = null;
        smokeCache = null;
        lastWidth = -1;
        lastHeight = -1;
        cacheValid = false;
        cachedTime = 0.0F;
    }
}
