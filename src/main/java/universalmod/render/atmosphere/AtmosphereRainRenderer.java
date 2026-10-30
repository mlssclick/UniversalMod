package universalmod.render.atmosphere;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
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
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import universalmod.module.render.Atmosphere;
import universalmod.render.RenderSampler;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

 
public final class AtmosphereRainRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("UniversalMod/Rain");
    private static final String ROOT = "post/atmosphere/rain/";
    private static final int UNIFORM_BYTES = 432;  
    private static final int DROP_COUNT = 13 * 11 * 13 * 2;
    private static final float PUDDLE_COVERAGE = 1.0f;
    private static final float RIPPLE_STRENGTH = 1.0f;
    private static final long RETRY_MILLIS = 5000;
    private static final RainHeightMap HEIGHT_MAP = new RainHeightMap();
    private static RenderPipeline compositePipeline, particlePipeline;
    private static GpuBuffer uniforms;
    private static GpuTexture scene, depth;
    private static GpuTextureView sceneView, depthView;
    private static int width = -1, height = -1;
    private static long retryAfter;
    private static double animationTime;
    private static long lastNanos;

    private AtmosphereRainRenderer() {}

    public static void render(Atmosphere module, Camera camera, Matrix4f view, Matrix4f projection, float partialTick) {
        Minecraft client = Minecraft.getInstance();
        if (module == null || !module.isRainActive() || module.getRainAmount() <= 0f || client.level == null
                || client.player == null || camera == null || view == null || projection == null
                || camera.getFluidInCamera() != FogType.NONE || System.currentTimeMillis() < retryAfter) return;
        RenderTarget target = client.getMainRenderTarget();
        if (target == null || target.width < 1 || target.height < 1 || target.getColorTexture() == null
                || target.getColorTextureView() == null || target.getDepthTexture() == null) return;
        try {
            init();
            ensureTargets(target.width, target.height);
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            Vec3 cam = camera.position();
            HEIGHT_MAP.update(client.level, cam, encoder);
            encoder.copyTextureToTexture(target.getColorTexture(), scene, 0, 0, 0, 0, 0, width, height);
            encoder.copyTextureToTexture(target.getDepthTexture(), depth, 0, 0, 0, 0, 0, width, height);
            updateTime(client.isPaused());
            upload(encoder, module, camera, cam, view, projection, partialTick);
            try (RenderPass pass = encoder.createRenderPass(() -> "universalmod:rain_surface", target.getColorTextureView(), OptionalInt.empty())) {
                pass.setPipeline(compositePipeline);
                bind(pass);
                pass.bindTexture("Sampler0", sceneView, RenderSampler.linear());
                pass.draw(0, 6);
            }
            try (RenderPass pass = encoder.createRenderPass(() -> "universalmod:rain_streaks", target.getColorTextureView(), OptionalInt.empty())) {
                pass.setPipeline(particlePipeline);
                bind(pass);
                pass.draw(0, DROP_COUNT * 6);
            }
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.error("Atmosphere rain render failed; retrying in {} ms", RETRY_MILLIS, failure);
            closeResources();
            retryAfter = System.currentTimeMillis() + RETRY_MILLIS;
        }
    }

    private static void updateTime(boolean paused) {
        long now = System.nanoTime();
        if (!paused && lastNanos != 0) animationTime += Math.min(0.1, Math.max(0.0, (now - lastNanos) * 1e-9));
        lastNanos = now;
    }

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("universalmod", path); }

    private static RenderPipeline pipeline(String name, String vertex, String fragment, boolean particles) {
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(id("pipeline/atmosphere/rain/" + name))
                .withVertexShader(id(vertex)).withFragmentShader(id(fragment))
                .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                .withUniform("RainData", UniformType.UNIFORM_BUFFER)
                .withSampler("DepthSampler").withSampler("HeightSampler")
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withCull(false);
        if (particles) builder.withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA);
        else builder.withSampler("Sampler0");
        return RenderPipelines.register(builder.build());
    }

    private static void init() {
        if (compositePipeline == null) compositePipeline = pipeline("composite", "post/winter/winter", ROOT + "composite", false);
        if (particlePipeline == null) particlePipeline = pipeline("particles", ROOT + "particles", ROOT + "particles", true);
        if (uniforms == null || uniforms.isClosed()) uniforms = RenderSystem.getDevice().createBuffer(
                () -> "universalmod:rain_uniforms", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, UNIFORM_BYTES);
    }

    private static void bind(RenderPass pass) {
        pass.setUniform("RainData", uniforms);
        pass.bindTexture("DepthSampler", depthView, RenderSampler.nearest());
        pass.bindTexture("HeightSampler", HEIGHT_MAP.view(), RenderSampler.nearest());
    }

    private static void ensureTargets(int w, int h) {
        if (w == width && h == height && scene != null && !scene.isClosed() && depth != null && !depth.isClosed()) return;
        closeTargets();
        var device = RenderSystem.getDevice();
        int flags = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING;
        scene = device.createTexture(() -> "universalmod:rain_scene", flags, TextureFormat.RGBA8, w, h, 1, 1);
        sceneView = device.createTextureView(scene);
        depth = device.createTexture(() -> "universalmod:rain_depth", flags, TextureFormat.DEPTH32, w, h, 1, 1);
        depthView = device.createTextureView(depth);
        width = w; height = h;
    }

    private static void upload(CommandEncoder encoder, Atmosphere settings, Camera camera, Vec3 cam,
                               Matrix4f view, Matrix4f projection, float partialTick) {
        Minecraft client = Minecraft.getInstance();
        AtmosphereSunProjection.Sun sun = AtmosphereSunProjection.sample(camera, partialTick, view, projection);
        int sky = camera.attributeProbe().getValue(EnvironmentAttributes.SKY_COLOR, partialTick);
        float daylight = Mth.clamp((sun.y() + 0.15f) / 0.55f, 0f, 1f);
        daylight = daylight * daylight * (3f - 2f * daylight);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer b = stack.calloc(UNIFORM_BYTES);
            projection.get(0, b);
            new Matrix4f(projection).invert().get(64, b);
            view.get(128, b);
            new Matrix4f(view).invert().get(192, b);
            new Matrix4f(projection).mul(view).invert().get(256, b);
            vec4(b, 320, (float)cam.x, (float)cam.y, (float)cam.z, 0f);
            vec4(b, 336, ((sky >> 16) & 255) / 255f, ((sky >> 8) & 255) / 255f, (sky & 255) / 255f,
                    height * Math.abs(projection.m11()) * 0.5f);
            vec4(b, 352, sun.x(), sun.y(), sun.z(), daylight);
            vec4(b, 368, (float)animationTime, settings.getRainAmount(),
                    WeatherWorldRenderer.flash(partialTick), 0f);
            vec4(b, 384, PUDDLE_COVERAGE, RIPPLE_STRENGTH, 0f, 0f);
            vec4(b, 400, (float)(HEIGHT_MAP.originX() - cam.x), (float)(HEIGHT_MAP.originZ() - cam.z),
                    RainHeightMap.SIZE, client.level.getMinY());
            vec4(b, 416, width, height, sun.reversedDepth(), 0f);
            encoder.writeToBuffer(uniforms.slice(0, UNIFORM_BYTES), b);
        }
    }

    private static void vec4(ByteBuffer b, int offset, float x, float y, float z, float w) {
        b.putFloat(offset, x); b.putFloat(offset + 4, y); b.putFloat(offset + 8, z); b.putFloat(offset + 12, w);
    }

    public static void reset() {
        closeResources();
        animationTime = 0; lastNanos = 0; retryAfter = 0;
    }

    private static void closeResources() {
        closeTargets();
        HEIGHT_MAP.close();
        if (uniforms != null) { uniforms.close(); uniforms = null; }
    }

    private static void closeTargets() {
        if (sceneView != null) { sceneView.close(); sceneView = null; }
        if (depthView != null) { depthView.close(); depthView = null; }
        if (scene != null) { scene.close(); scene = null; }
        if (depth != null) { depth.close(); depth = null; }
        width = height = -1;
    }
}
