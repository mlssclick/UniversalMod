package universalmod.render.atmosphere;

import com.mojang.blaze3d.buffers.GpuBuffer;
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
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import universalmod.module.render.Atmosphere;
import universalmod.render.RenderSampler;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.OptionalInt;

public final class AtmosphereMoodRenderer {
    private static final String BASE = "post/atmosphere/";
    private static final int HALLOWEEN_PUMPKIN_LIMIT = 20;
    private static final int MOOD_BYTES = WeatherUniformLayout.TOTAL_BYTES + HALLOWEEN_PUMPKIN_LIMIT * 16;
    private static final int BRIGHT_BYTES = 32;
    private static final int BLUR_BYTES = 32;

    private static RenderPipeline weatherPipeline;
    private static RenderPipeline brightPipeline;
    private static RenderPipeline blurPipeline;
    private static GpuBuffer weatherData;
    private static GpuBuffer brightData;
    private static GpuBuffer blurHorizontal;
    private static GpuBuffer blurVertical;

    private static GpuTexture scene;
    private static GpuTextureView sceneView;
    private static GpuTexture depth;
    private static GpuTextureView depthView;
    private static GpuTexture bright;
    private static GpuTextureView brightView;
    private static GpuTexture horizontal;
    private static GpuTextureView horizontalView;
    private static GpuTexture vertical;
    private static GpuTextureView verticalView;
    private static int width = -1;
    private static int height = -1;
    private static long epoch = -1L;
    private static boolean disabledAfterError;
    private static long retryAfterMillis;

    private AtmosphereMoodRenderer() {}

    public static void render(Atmosphere module, Camera camera, Matrix4f view,
                              Matrix4f projection, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if ((disabledAfterError && System.currentTimeMillis() < retryAfterMillis) || module == null || (!module.isMoodActive() || module.isThunderMode())
                || minecraft.level == null || minecraft.player == null
                || camera == null || view == null || projection == null) return;
        RenderTarget target = minecraft.getMainRenderTarget();
        if (target == null || target.width < 1 || target.height < 1
                || target.getColorTexture() == null || target.getColorTextureView() == null
                || target.getDepthTexture() == null) return;
        try {
            disabledAfterError = false;
            init();
            if (!ensureTargets(target.width, target.height)) return;
            if (epoch < 0L) epoch = System.currentTimeMillis();
            float time = module.isSakuraMode()
                    ? (System.currentTimeMillis() - epoch) * 0.001f * module.getSakuraSkySpeed()
                    : minecraft.level.getGameTime() * 0.05f;
            
            Vec3 pos = camera.position();
            Matrix4f viewProjection = new Matrix4f(projection).mul(view);
            Matrix4f inverseViewProjection = new Matrix4f(viewProjection).invert();
            AtmosphereSunProjection.Sun sun = AtmosphereSunProjection.sample(
                    camera, partialTick, view, projection);
            float sunX = sun.x(), sunY = sun.y(), sunZ = sun.z();
            float sunU = sun.u(), sunV = sun.v(), sunVisibility = sun.onScreen();
            float viewDistance = Math.max(64f, minecraft.options.renderDistance().get() * 16.0f);
            Color warmth = module.getMoodSunColor();

            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.copyTextureToTexture(target.getColorTexture(), scene, 0, 0, 0, 0, 0,
                    target.width, target.height);
            encoder.copyTextureToTexture(target.getDepthTexture(), depth, 0, 0, 0, 0, 0,
                    target.width, target.height);

             
            if (module.isSakuraMode()) {
                uploadBright(encoder, target.width / 2, target.height / 2);
                try (RenderPass pass = encoder.createRenderPass(() -> "universalmod:atmosphere_bright",
                        brightView, OptionalInt.empty())) {
                    pass.setPipeline(brightPipeline);
                    pass.setUniform("BrightData", brightData);
                    pass.bindTexture("InSampler", sceneView, RenderSampler.linear());
                    pass.draw(0, 6);
                }
                uploadBlur(encoder, blurHorizontal, target.width / 2, target.height / 2, 1, 0, 5.0f);
                try (RenderPass pass = encoder.createRenderPass(() -> "universalmod:atmosphere_blur_h",
                        horizontalView, OptionalInt.empty())) {
                    pass.setPipeline(blurPipeline);
                    pass.setUniform("MoodBlurData", blurHorizontal);
                    pass.bindTexture("InSampler", brightView, RenderSampler.linear());
                    pass.draw(0, 6);
                }
                uploadBlur(encoder, blurVertical, target.width / 2, target.height / 2, 0, 1, 5.0f);
                try (RenderPass pass = encoder.createRenderPass(() -> "universalmod:atmosphere_blur_v",
                        verticalView, OptionalInt.empty())) {
                    pass.setPipeline(blurPipeline);
                    pass.setUniform("MoodBlurData", blurVertical);
                    pass.bindTexture("InSampler", horizontalView, RenderSampler.linear());
                    pass.draw(0, 6);
                }
            }
                uploadWeather(encoder, inverseViewProjection, projection, pos, target.width,
                        target.height, time, sunX, sunY, sunZ, sunU, sunV,
                        sunVisibility, sun.reversedDepth(), viewDistance, module, warmth);
                try (RenderPass pass = encoder.createRenderPass(() -> "universalmod:atmosphere_dust_ash",
                        target.getColorTextureView(), OptionalInt.empty())) {
                    pass.setPipeline(weatherPipeline);
                    pass.setUniform("MoodWeatherData", weatherData);
                    pass.bindTexture("Sampler0", sceneView, RenderSampler.linear());
                    pass.bindTexture("Sampler1", depthView, RenderSampler.nearest());
                    pass.bindTexture("Sampler2", verticalView, RenderSampler.linear());
                    pass.draw(0, 6);
                }
        } catch (RuntimeException | LinkageError failure) {
            disabledAfterError = true;
            retryAfterMillis = System.currentTimeMillis() + 2000L;
            closeTextures();
            closeBuffers();
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("universalmod", path);
    }

    private static RenderPipeline pipeline(String name, String fragment, String block, String... samplers) {
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(id("pipeline/post/atmosphere/" + name))
                .withVertexShader(id("post/winter/winter"))
                .withFragmentShader(id(fragment))
                .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(false);
        if (block != null) builder.withUniform(block, UniformType.UNIFORM_BUFFER);
        for (String sampler : samplers) builder.withSampler(sampler);
        return RenderPipelines.register(builder.build());
    }

    private static void init() {
        if (weatherPipeline == null) {
            weatherPipeline = pipeline("dust_ash", BASE + "dust_ash",
                    "MoodWeatherData", "Sampler0", "Sampler1", "Sampler2");
            brightPipeline = pipeline("bright", BASE + "bright", "BrightData", "InSampler");
            blurPipeline = pipeline("blur", BASE + "blur", "MoodBlurData", "InSampler");
        }
        if (weatherData == null || weatherData.isClosed()) weatherData = buffer("dust_ash", MOOD_BYTES);
        if (brightData == null || brightData.isClosed()) brightData = buffer("bright", BRIGHT_BYTES);
        if (blurHorizontal == null || blurHorizontal.isClosed()) blurHorizontal = buffer("blur_h", BLUR_BYTES);
        if (blurVertical == null || blurVertical.isClosed()) blurVertical = buffer("blur_v", BLUR_BYTES);
    }

    private static GpuBuffer buffer(String name, int size) {
        return RenderSystem.getDevice().createBuffer(() -> "universalmod:atmosphere_" + name,
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, size);
    }

    private static GpuTexture texture(GpuDevice device, String name, int flags,
                                      TextureFormat format, int w, int h) {
        return device.createTexture(() -> "universalmod:atmosphere_" + name,
                flags, format, w, h, 1, 1);
    }

    private static boolean ensureTargets(int w, int h) {
        GpuDevice device = RenderSystem.tryGetDevice();
        if (device == null) return false;
        if (w == width && h == height && scene != null && !scene.isClosed() &&
                depth != null && !depth.isClosed() && vertical != null && !vertical.isClosed()) return true;
        closeTextures();
        scene = texture(device, "scene", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                TextureFormat.RGBA8, w, h);
        sceneView = device.createTextureView(scene);
        depth = texture(device, "depth", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                TextureFormat.DEPTH32, w, h);
        depthView = device.createTextureView(depth);
        int halfW = Math.max(1, w / 2), halfH = Math.max(1, h / 2);
        int flags = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;
        bright = texture(device, "bright", flags, TextureFormat.RGBA8, halfW, halfH);
        brightView = device.createTextureView(bright);
        horizontal = texture(device, "blur_h", flags, TextureFormat.RGBA8, halfW, halfH);
        horizontalView = device.createTextureView(horizontal);
        vertical = texture(device, "blur_v", flags, TextureFormat.RGBA8, halfW, halfH);
        verticalView = device.createTextureView(vertical);
        width = w;
        height = h;
        return true;
    }

    private static void uploadWeather(CommandEncoder encoder, Matrix4f inverse,
                                      Matrix4f projection, Vec3 cam, int w, int h, float time,
                                      float sx, float sy, float sz, float sunU, float sunV,
                                      float sunVisible, float reversedDepth, float viewDistance, Atmosphere settings,
                                      Color warm) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer b = stack.calloc(MOOD_BYTES);
            inverse.get(0, b);
            vec3(b, 1, (float)cam.x, (float)cam.y, (float)cam.z);
            vec2(b, 2, w, h);
            scalar(b, 3, time);
            scalar(b, 4, 2.0f / Math.max(0.01f, Math.abs(projection.m11())) / Math.max(1, h));
            vec3(b, 5, sx, sy, sz);
            vec3(b, 6, sunU, sunV, sunVisible);
            scalar(b, 7, viewDistance);
            scalar(b, 8, (float)cam.y - 5.0f);
            scalar(b, 9, settings.getMoodKind()); 
            scalar(b, 10, settings.getRainAmount());   
            scalar(b, 11, settings.getMoodWindSpeed());
            scalar(b, 12, 0.12f + settings.getMoodHaze() * 0.40f);
            scalar(b, 13, settings.getMoodGrade());
            scalar(b, 14, settings.isSakuraMode() ? settings.getSakuraBloom()*0.78f : 0f);
            scalar(b, 15, settings.getMoodVignette());
            scalar(b, 16, settings.isSundayMode() ? settings.getMoodGodRays() : settings.isSakuraMode() ? settings.getMoodGodRays()*0.28f : 0f);
            scalar(b, 17, 0.0f);
            scalar(b, 18, 0.0f);
            scalar(b, 19, 0.015f);
            scalar(b, 20, 0.05f);
            scalar(b, 21, 0.0f);
            scalar(b, 22, 0.0f);
            if (settings.isSakuraMode()) {
                vec3(b,23,.69f,.80f,1.0f); vec3(b,24,1.0f,.79f,.85f);
                vec3(b,25,1.0f,.91f,.80f); vec3(b,26,.94f,.73f,.81f);
                vec3(b,27,1.0f,.79f,.86f); vec3(b,28,.22f,.08f,.22f);
                vec3(b,29,1.0f,.92f,.91f);
            } else if (settings.isThunderMode()) {
                vec3(b,23,.09f,.12f,.16f); vec3(b,24,.21f,.23f,.26f);
                vec3(b,25,.32f,.39f,.50f); vec3(b,26,.24f,.26f,.29f);
                vec3(b,27,.39f,.46f,.55f); vec3(b,28,.12f,.15f,.19f);
                vec3(b,29,.68f,.72f,.78f);
            } else if (settings.isAutumnMode()) {
                vec3(b,23,.37f,.42f,.48f); vec3(b,24,.68f,.63f,.55f);
                vec3(b,25,.92f,.72f,.44f); vec3(b,26,.55f,.51f,.46f);
                vec3(b,27,.76f,.55f,.33f); vec3(b,28,.21f,.20f,.22f);
                vec3(b,29,.89f,.72f,.49f);
            } else if (settings.isSundayMode()) {
                vec3(b,23,.25f,.48f,.82f); vec3(b,24,.77f,.88f,.98f);
                vec3(b,25,1.0f,.87f,.63f); vec3(b,26,.67f,.78f,.86f);
                vec3(b,27,.84f,.92f,1.0f); vec3(b,28,.17f,.23f,.33f);
                vec3(b,29,1.0f,.92f,.76f);
            } else {
                vec3(b,23,.04f,.04f,.07f); vec3(b,24,.21f,.14f,.17f);
                vec3(b,25,.65f,.10f,.10f); vec3(b,26,.24f,.22f,.24f);
                vec3(b,27,.55f,.11f,.14f); vec3(b,28,.16f,.06f,.08f);
                vec3(b,29,.83f,.25f,.28f);
            }
            vec3(b, 30, sx, sy, sz);
            vec3(b, 31, 1.0f, 1.0f, 1.0f);
            vec4(b, 32, 1.04f, 1.01f, 1.06f, 0.02f);
            scalar(b, 33, reversedDepth); 
            scalar(b, 34, 1.0f);
            b.position(0);
            encoder.writeToBuffer(weatherData.slice(0, MOOD_BYTES), b);
        }
    }

    private static void uploadBright(CommandEncoder encoder, int w, int h) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer b = stack.calloc(BRIGHT_BYTES);
            b.putFloat(0, Math.max(1, w)); b.putFloat(4, Math.max(1, h));
            b.putFloat(8, 0.78f); b.putFloat(12, 1.0f);
            b.position(0);
            encoder.writeToBuffer(brightData.slice(0, BRIGHT_BYTES), b);
        }
    }

    private static void uploadBlur(CommandEncoder encoder, GpuBuffer dst, int w, int h,
                                   float dirX, float dirY, float radius) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer b = stack.calloc(BLUR_BYTES);
            b.putFloat(0, Math.max(1, w)); b.putFloat(4, Math.max(1, h));
            b.putFloat(8, dirX); b.putFloat(12, dirY);
            b.putFloat(16, radius);
            b.position(0);
            encoder.writeToBuffer(dst.slice(0, BLUR_BYTES), b);
        }
    }

    private static void scalar(ByteBuffer b, int i, float v) { WeatherUniformLayout.scalar(b, i, v); }
    private static void vec2(ByteBuffer b, int i, float x, float y) { WeatherUniformLayout.vec2(b, i, x, y); }
    private static void vec3(ByteBuffer b, int i, float x, float y, float z) { WeatherUniformLayout.vec3(b, i, x, y, z); }
    private static void vec4(ByteBuffer b, int i, float x, float y, float z, float w) { WeatherUniformLayout.vec4(b, i, x, y, z, w); }

    public static void resetTime() { epoch = -1L; }

    public static void close() {
        resetTime();
        closeTextures();
        closeBuffers();
        disabledAfterError = false;
        retryAfterMillis = 0L;
    }

    private static void closeBuffers() {
        if (weatherData != null) { weatherData.close(); weatherData = null; }
        if (brightData != null) { brightData.close(); brightData = null; }
        if (blurHorizontal != null) { blurHorizontal.close(); blurHorizontal = null; }
        if (blurVertical != null) { blurVertical.close(); blurVertical = null; }
    }

    private static void closeTextures() {
        if (sceneView != null) { sceneView.close(); sceneView = null; }
        if (scene != null) { scene.close(); scene = null; }
        if (depthView != null) { depthView.close(); depthView = null; }
        if (depth != null) { depth.close(); depth = null; }
        if (brightView != null) { brightView.close(); brightView = null; }
        if (bright != null) { bright.close(); bright = null; }
        if (horizontalView != null) { horizontalView.close(); horizontalView = null; }
        if (horizontal != null) { horizontal.close(); horizontal = null; }
        if (verticalView != null) { verticalView.close(); verticalView = null; }
        if (vertical != null) { vertical.close(); vertical = null; }
        width = height = -1;
    }
}
