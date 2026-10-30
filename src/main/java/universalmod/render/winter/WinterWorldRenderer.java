package universalmod.render.winter;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import universalmod.module.render.Atmosphere;
import universalmod.render.RenderSampler;
import universalmod.render.atmosphere.AtmosphereSunProjection;
import universalmod.render.atmosphere.WeatherUniformLayout;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

public final class WinterWorldRenderer {
    
    private static final int WEATHER_BYTES = WeatherUniformLayout.TOTAL_BYTES;
    private static final int BLUR_BYTES = 16;
    private static final String ROOT = "post/winter/";

    private static RenderPipeline surfacePipeline;
    private static RenderPipeline flakesPipeline;
    private static RenderPipeline blurPipeline;
    private static RenderPipeline compositePipeline;
    private static RenderPipeline corePipeline;
    private static GpuBuffer surfaceWeatherUniforms;
    private static GpuBuffer snowfallUniforms;
    private static GpuBuffer blurHorizontal;
    private static GpuBuffer blurVertical;
    private static GpuTexture sceneCopy;
    private static GpuTextureView sceneView;
    private static GpuTexture depthCopy;
    private static GpuTextureView depthView;
    private static GpuTexture snowCapture;
    private static GpuTextureView snowCaptureView;
    private static GpuTexture snowPing;
    private static GpuTextureView snowPingView;
    private static GpuTexture snowPong;
    private static GpuTextureView snowPongView;
    private static int width = -1;
    private static int height = -1;
    private static long startMillis = -1L;
    private static boolean disabledAfterError;
    private static long retryAfterMillis;

    private WinterWorldRenderer() {}

    public static void render(Atmosphere module, Camera camera, Matrix4f view, Matrix4f projection, float partialTick) {
        Minecraft client = Minecraft.getInstance();
        if ((disabledAfterError && System.currentTimeMillis() < retryAfterMillis) || module == null || !module.isWinterActive() || client.level == null
                || client.player == null || camera == null || view == null || projection == null) return;
        RenderTarget main = client.getMainRenderTarget();
        if (main == null || main.width <= 0 || main.height <= 0 || main.getColorTexture() == null
                || main.getColorTextureView() == null || main.getDepthTexture() == null) return;

        try {
            disabledAfterError = false;
            init();
            if (!ensureTextures(main.width, main.height)) return;
            if (startMillis < 0L) startMillis = System.currentTimeMillis();
            float time = (System.currentTimeMillis() - startMillis) * 0.001f;
            Vec3 pos = camera.position();
            Matrix4f viewProj = new Matrix4f(projection).mul(view);
            Matrix4f inverseViewProj = new Matrix4f(viewProj).invert();
            int sky = camera.attributeProbe().getValue(EnvironmentAttributes.SKY_COLOR, partialTick);
            float skyR = ((sky >> 16) & 255) / 255.0f;
            float skyG = ((sky >> 8) & 255) / 255.0f;
            float skyB = (sky & 255) / 255.0f;
            AtmosphereSunProjection.Sun sun = AtmosphereSunProjection.sample(
                    camera, partialTick, view, projection);

            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.copyTextureToTexture(main.getColorTexture(), sceneCopy, 0, 0, 0, 0, 0, main.width, main.height);
            encoder.copyTextureToTexture(main.getDepthTexture(), depthCopy, 0, 0, 0, 0, 0, main.width, main.height);

            writeWeatherUniforms(encoder, surfaceWeatherUniforms, inverseViewProj, pos, projection,
                    main.width, main.height, module, time, sun, skyR, skyG, skyB, false);
            try (RenderPass pass = encoder.createRenderPass(() -> "universalmod:winter_snow_surface",
                    main.getColorTextureView(), OptionalInt.empty())) {
                pass.setPipeline(surfacePipeline);
                pass.setUniform("WinterWeatherData", surfaceWeatherUniforms);
                pass.bindTexture("Sampler0", sceneView, RenderSampler.linear());
                pass.bindTexture("Sampler1", depthView, RenderSampler.nearest());
                pass.bindTexture("Sampler2", sceneView, RenderSampler.linear());
                pass.draw(0, 6);
            }

            if (module.isSnowflakesEnabled() && module.getSnowAmount() > 0.0f) {
                
                
                writeWeatherUniforms(encoder, snowfallUniforms, inverseViewProj, pos, projection,
                        main.width, main.height, module, time, sun, skyR, skyG, skyB, true);
                try (RenderPass pass = encoder.createRenderPass(() -> "universalmod:winter_snow_capture",
                        snowCaptureView, OptionalInt.empty())) {
                    pass.setPipeline(flakesPipeline);
                    pass.setUniform("WinterWeatherData", snowfallUniforms);
                    pass.bindTexture("Sampler0", sceneView, RenderSampler.linear());
                    pass.bindTexture("Sampler1", depthView, RenderSampler.nearest());
                    pass.bindTexture("Sampler2", sceneView, RenderSampler.linear());
                    pass.draw(0, 6);
                }
                
                writeBlur(encoder, blurHorizontal, 3.2f);
                try (RenderPass pass = encoder.createRenderPass(() -> "universalmod:winter_bloom_h",
                        snowPingView, OptionalInt.empty())) {
                    pass.setPipeline(blurPipeline);
                    pass.setUniform("WinterBlurData", blurHorizontal);
                    pass.bindTexture("Sampler0", snowCaptureView, RenderSampler.linear());
                    pass.draw(0, 6);
                }
                writeBlur(encoder, blurVertical, -1.6f);
                try (RenderPass pass = encoder.createRenderPass(() -> "universalmod:winter_bloom_v",
                        snowPongView, OptionalInt.empty())) {
                    pass.setPipeline(blurPipeline);
                    pass.setUniform("WinterBlurData", blurVertical);
                    pass.bindTexture("Sampler0", snowPingView, RenderSampler.linear());
                    pass.draw(0, 6);
                }
                try (RenderPass pass = encoder.createRenderPass(() -> "universalmod:winter_bloom_composite",
                        main.getColorTextureView(), OptionalInt.empty())) {
                    pass.setPipeline(compositePipeline);
                    pass.bindTexture("Sampler0", snowPongView, RenderSampler.linear());
                    pass.draw(0, 6);
                }
                try (RenderPass pass = encoder.createRenderPass(() -> "universalmod:winter_sharp_flakes",
                        main.getColorTextureView(), OptionalInt.empty())) {
                    pass.setPipeline(corePipeline);
                    pass.bindTexture("Sampler0", snowCaptureView, RenderSampler.linear());
                    pass.draw(0, 6);
                }
            }
        } catch (RuntimeException | LinkageError exception) {
            disabledAfterError = true;
            retryAfterMillis = System.currentTimeMillis() + 2000L;
            closeTextures();
            closeUniforms();
        }
    }

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("universalmod", path); }

    private static RenderPipeline makePipeline(String name, String fragmentShader, String uniform, boolean bloom,
                                               boolean s0, boolean s1, boolean s2) {
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(id("pipeline/post/winter/" + name))
                .withVertexShader(id(ROOT + "winter"))
                .withFragmentShader(id(ROOT + fragmentShader))
                .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(false);
        if (uniform != null) builder.withUniform(uniform, UniformType.UNIFORM_BUFFER);
        if (s0) builder.withSampler("Sampler0");
        if (s1) builder.withSampler("Sampler1");
        if (s2) builder.withSampler("Sampler2");
        if (bloom) builder.withBlend(BlendFunction.LIGHTNING);
        return RenderPipelines.register(builder.build());
    }

    private static void init() {
        if (surfacePipeline == null) {
            
            surfacePipeline = makePipeline("winter_surface", "winter_particles", "WinterWeatherData", false, true, true, true);
            flakesPipeline = makePipeline("winter_particles", "winter_particles", "WinterWeatherData", false, true, true, true);
            blurPipeline = makePipeline("winter_blur", "winter_blur", "WinterBlurData", false, true, false, false);
            compositePipeline = makePipeline("winter_composite", "winter_composite", null, true, true, false, false);
            corePipeline = makePipeline("winter_core", "winter_core", null, true, true, false, false);
        }
        if (surfaceWeatherUniforms == null || surfaceWeatherUniforms.isClosed()) surfaceWeatherUniforms = uniform("snow_surface", WEATHER_BYTES);
        if (snowfallUniforms == null || snowfallUniforms.isClosed()) snowfallUniforms = uniform("snowfall", WEATHER_BYTES);
        if (blurHorizontal == null || blurHorizontal.isClosed()) blurHorizontal = uniform("blur_h", BLUR_BYTES);
        if (blurVertical == null || blurVertical.isClosed()) blurVertical = uniform("blur_v", BLUR_BYTES);
    }

    private static GpuBuffer uniform(String name, int bytes) {
        return RenderSystem.getDevice().createBuffer(() -> "universalmod:winter_" + name,
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, bytes);
    }

    private static boolean ensureTextures(int w, int h) {
        GpuDevice device = RenderSystem.tryGetDevice();
        if (device == null) return false;
        if (width == w && height == h && sceneCopy != null && !sceneCopy.isClosed() &&
                snowCapture != null && !snowCapture.isClosed()) return true;
        closeTextures();
        sceneCopy = texture(device, "scene", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                TextureFormat.RGBA8, w, h);
        sceneView = device.createTextureView(sceneCopy);
        depthCopy = texture(device, "depth", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                TextureFormat.DEPTH32, w, h);
        depthView = device.createTextureView(depthCopy);
        int flags = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;
        snowCapture = texture(device, "snow_capture", flags, TextureFormat.RGBA8, w, h);
        snowCaptureView = device.createTextureView(snowCapture);
        snowPing = texture(device, "snow_ping", flags, TextureFormat.RGBA8, Math.max(w / 2, 1), Math.max(h / 2, 1));
        snowPingView = device.createTextureView(snowPing);
        snowPong = texture(device, "snow_pong", flags, TextureFormat.RGBA8, Math.max(w / 2, 1), Math.max(h / 2, 1));
        snowPongView = device.createTextureView(snowPong);
        width = w;
        height = h;
        return true;
    }

    private static GpuTexture texture(GpuDevice device, String name, int usage, TextureFormat format, int w, int h) {
        return device.createTexture(() -> "universalmod:winter_" + name, usage, format, w, h, 1, 1);
    }

    
    private static void writeWeatherUniforms(CommandEncoder encoder, GpuBuffer target, Matrix4f inverse, Vec3 pos,
                                             Matrix4f projection, int w, int h, Atmosphere module, float time,
                                             AtmosphereSunProjection.Sun sun, float sr, float sg, float sb,
                                             boolean captureParticles) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.calloc(WEATHER_BYTES);
            inverse.get(0, data);
            param3(data, 1, (float)pos.x, (float)pos.y, (float)pos.z);
            param3(data, 2, w, h, 0.0f);
            param(data, 3, time);
            param(data, 4, 2.0f / Math.max(0.01f, Math.abs(projection.m11())) / Math.max(1, h));
            param3(data, 5, sun.x(), sun.y(), sun.z());
            param3(data, 6, sun.u(), sun.v(), sun.onScreen());
            param(data, 7, Math.max(48.0f, module.getFogDistance()));
            param(data, 8, (float)pos.y - 7.0f);
            param(data, 9, 1.0f); 
            param(data, 10, captureParticles ? module.getSnowAmount() : 0.0f);
            param(data, 11, module.getSnowflakeSpeed());
            
            param(data, 12, 0.0f); 
            param(data, 13, 0.0f); 
            param(data, 14, 0.0f); 
            param(data, 15, 0.0f); 
            param(data, 16, captureParticles ? 0.0f : module.getWinterGodRays()); 
            param(data, 17, 0.0f); 
            param(data, 18, 0.0f); 
            param(data, 19, 0.0f); 
            param(data, 20, captureParticles ? 0.0f : 0.16f); 
            param(data, 21, captureParticles ? 0.0f : 0.25f); 
            param(data, 22, captureParticles ? 0.0f : module.getSnowIntensity()); 
            param3(data, 23, 0.6f, 0.7f, 0.9f);
            param3(data, 24, 0.85f, 0.9f, 1.0f);
            param3(data, 25, 1.0f, 0.94f, 0.79f); 
            param3(data, 26, 0.78f, 0.84f, 0.95f);
            param3(data, 27, 0.92f, 0.97f, 1.0f);
            param3(data, 28, 0.75f, 0.83f, 1.0f);
            param3(data, 29, 1.0f, 1.0f, 1.0f);
            param3(data, 30, sun.x(), sun.y(), sun.z());
            param3(data, 31, 1.0f, 1.0f, 1.0f);
            param4(data, 32, 1.0f, 1.0f, 1.0f, 0.0f);
            param(data, 33, sun.reversedDepth()); 
            param(data, 34, captureParticles ? 1.0f : 0.0f); 
            data.position(0);
            encoder.writeToBuffer(target.slice(0, WEATHER_BYTES), data);
        }
    }

    private static void writeBlur(CommandEncoder encoder, GpuBuffer target, float offset) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.calloc(BLUR_BYTES);
            data.putFloat(0, offset);
            encoder.writeToBuffer(target.slice(0, BLUR_BYTES), data);
        }
    }

    private static void param(ByteBuffer data, int index, float value) {
        WeatherUniformLayout.scalar(data, index, value);
    }
    private static void param3(ByteBuffer data, int index, float x, float y, float z) {
        WeatherUniformLayout.vec3(data, index, x, y, z);
    }
    private static void param4(ByteBuffer data, int index, float x, float y, float z, float w) {
        WeatherUniformLayout.vec4(data, index, x, y, z, w);
    }

    public static void resetTime() { startMillis = -1L; }
    public static void reset() { startMillis = -1L; }
    public static void close() {
        reset(); closeTextures(); closeUniforms();
        disabledAfterError = false;
        retryAfterMillis = 0L;
    }
    private static void closeUniforms() {
        if (surfaceWeatherUniforms != null) { surfaceWeatherUniforms.close(); surfaceWeatherUniforms = null; }
        if (snowfallUniforms != null) { snowfallUniforms.close(); snowfallUniforms = null; }
        if (blurHorizontal != null) { blurHorizontal.close(); blurHorizontal = null; }
        if (blurVertical != null) { blurVertical.close(); blurVertical = null; }
    }
    private static void closeTextures() {
        if (sceneView != null) { sceneView.close(); sceneView = null; }
        if (sceneCopy != null) { sceneCopy.close(); sceneCopy = null; }
        if (depthView != null) { depthView.close(); depthView = null; }
        if (depthCopy != null) { depthCopy.close(); depthCopy = null; }
        if (snowCaptureView != null) { snowCaptureView.close(); snowCaptureView = null; }
        if (snowCapture != null) { snowCapture.close(); snowCapture = null; }
        if (snowPingView != null) { snowPingView.close(); snowPingView = null; }
        if (snowPing != null) { snowPing.close(); snowPing = null; }
        if (snowPongView != null) { snowPongView.close(); snowPongView = null; }
        if (snowPong != null) { snowPong.close(); snowPong = null; }
        width = height = -1;
    }
}
