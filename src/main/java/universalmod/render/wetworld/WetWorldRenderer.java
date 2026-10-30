package universalmod.render.wetworld;

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
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import universalmod.module.render.Ambience;
import universalmod.module.render.Atmosphere;
import universalmod.render.RenderSampler;
import universalmod.render.atmosphere.RainHeightMap;
import net.minecraft.world.level.biome.Biome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

public final class WetWorldRenderer {
    private static final int UNIFORM_SIZE = 240;
    private static final RainHeightMap rainHeights = new RainHeightMap();
    private static final Logger LOGGER = LoggerFactory.getLogger("UniversalMod/Wet");
    private static final Identifier PIPELINE_ID = Identifier.fromNamespaceAndPath("universalmod", "pipeline/post/wetworld");
    private static final Identifier SHADER_ID = Identifier.fromNamespaceAndPath("universalmod", "post/wetworld/wetworld");
    private static RenderPipeline pipeline;
    private static GpuBuffer uniformBuffer;
    private static GpuTexture sceneCopyTexture;
    private static GpuTextureView sceneCopyView;
    private static GpuTexture depthCopyTexture;
    private static GpuTextureView depthCopyView;
    private static int width = -1;
    private static int height = -1;
    private static boolean disabledAfterError;
    private static long retryAfterMillis;

    private WetWorldRenderer() {}

    public static void render(Camera camera, Matrix4f positionMatrix, Matrix4f projectionMatrix, float tickDelta) {
        Ambience module = Ambience.getInstance();
        Atmosphere wetAtmosphere = Atmosphere.getInstance();
        if (wetAtmosphere != null && wetAtmosphere.isThunderActive()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if ((disabledAfterError && System.currentTimeMillis() < retryAfterMillis) || module == null || !module.isWetActive() || minecraft.level == null || camera == null || positionMatrix == null || projectionMatrix == null) return;
        RenderTarget target = minecraft.getMainRenderTarget();
        if (target == null || target.getColorTexture() == null || target.getColorTextureView() == null || target.getDepthTexture() == null || target.width <= 0 || target.height <= 0) return;
        try {
            disabledAfterError = false;
            init();
            if (pipeline == null || uniformBuffer == null || !ensureTargets(target.width, target.height)) return;
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.copyTextureToTexture(target.getColorTexture(), sceneCopyTexture, 0, 0, 0, 0, 0, target.width, target.height);
            encoder.copyTextureToTexture(target.getDepthTexture(), depthCopyTexture, 0, 0, 0, 0, 0, target.width, target.height);

            Matrix4f viewProjection = new Matrix4f(projectionMatrix).mul(positionMatrix);
            Matrix4f inverseViewProjection = new Matrix4f(viewProjection).invert();
            Vec3 cameraPos = camera.position();
            int skyColor = camera.attributeProbe().getValue(EnvironmentAttributes.SKY_COLOR, tickDelta);
            float skyRed = ((skyColor >> 16) & 255) / 255.0f;
            float skyGreen = ((skyColor >> 8) & 255) / 255.0f;
            float skyBlue = (skyColor & 255) / 255.0f;
            float skyAngle = camera.attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, tickDelta) * Mth.DEG_TO_RAD;
            float sunX = -Mth.sin(skyAngle);
            float sunY = Mth.cos(skyAngle);
            float gloss = minecraft.level.dimensionType().hasSkyLight() ? 0.45f : 0.0f;
            float rain = rainIntensity(module, tickDelta);
            if (rain > 0f) rainHeights.update(minecraft.level, cameraPos, encoder);
            else rainHeights.close();
            writeUniforms(encoder, viewProjection, inverseViewProjection, cameraPos, skyRed, skyGreen, skyBlue,
                    sunX, sunY, module.wetReflectionStrength(), module.wetness(), gloss, module.wetQualitySteps(),
                    rain, tickDelta, projectionMatrix.m22() > -0.5f ? 1f : 0f);

            try (RenderPass pass = encoder.createRenderPass(() -> "universalmod:wet_world", target.getColorTextureView(), OptionalInt.empty())) {
                pass.setPipeline(pipeline);
                pass.setUniform("WetWorldData", uniformBuffer);
                pass.bindTexture("Sampler0", sceneCopyView, RenderSampler.linear());
                pass.bindTexture("Sampler1", depthCopyView, RenderSampler.nearest());
                pass.bindTexture("HeightSampler", rain > 0f ? rainHeights.view() : sceneCopyView, RenderSampler.nearest());
                pass.draw(0, 6);
            }
        } catch (RuntimeException | LinkageError throwable) {
            LOGGER.error("Wet surface render failed; retrying in 2000 ms", throwable);
            disabledAfterError = true;
            retryAfterMillis = System.currentTimeMillis() + 2000L;
            closeTargets();
            closeBuffer();
            rainHeights.close();
        }
    }

    private static void init() {
        if (disabledAfterError) return;
        if (pipeline == null) {
            pipeline = RenderPipelines.register(RenderPipeline.builder()
                    .withLocation(PIPELINE_ID)
                    .withVertexShader(SHADER_ID)
                    .withFragmentShader(SHADER_ID)
                    .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                    .withUniform("WetWorldData", UniformType.UNIFORM_BUFFER)
                    .withSampler("Sampler0")
                    .withSampler("Sampler1")
                    .withSampler("HeightSampler")
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build());
        }
        if (uniformBuffer == null || uniformBuffer.isClosed() || uniformBuffer.size() < UNIFORM_SIZE) {
            closeBuffer();
            uniformBuffer = RenderSystem.getDevice().createBuffer(() -> "universalmod:wet_world_uniforms", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, UNIFORM_SIZE);
        }
    }

    private static boolean ensureTargets(int w, int h) {
        GpuDevice device = RenderSystem.tryGetDevice();
        if (device == null) return false;
        if (sceneCopyTexture != null && depthCopyTexture != null && width == w && height == h) return true;
        closeTargets();
        sceneCopyTexture = device.createTexture(() -> "universalmod:wet_world_scene", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, TextureFormat.RGBA8, w, h, 1, 1);
        sceneCopyView = device.createTextureView(sceneCopyTexture);
        depthCopyTexture = device.createTexture(() -> "universalmod:wet_world_depth", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, TextureFormat.DEPTH32, w, h, 1, 1);
        depthCopyView = device.createTextureView(depthCopyTexture);
        width = w; height = h; return true;
    }

    private static void writeUniforms(CommandEncoder encoder, Matrix4f viewProjection, Matrix4f inverseViewProjection, Vec3 cameraPos, float skyRed, float skyGreen, float skyBlue, float sunX, float sunY, float reflectivity, float wetness, float gloss, float steps, float rain, float tickDelta, float reversedDepth) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.calloc(UNIFORM_SIZE);
            viewProjection.get(0, data);
            inverseViewProjection.get(64, data);
            data.putFloat(128, (float) cameraPos.x); data.putFloat(132, (float) cameraPos.y); data.putFloat(136, (float) cameraPos.z);
            data.putFloat(144, skyRed); data.putFloat(148, skyGreen); data.putFloat(152, skyBlue);
            data.putFloat(160, sunX); data.putFloat(164, sunY); data.putFloat(168, 0.0f);
            data.putFloat(176, reflectivity); data.putFloat(180, wetness); data.putFloat(184, 0.35f); data.putFloat(188, gloss);
            data.putFloat(192, steps); data.putFloat(196, 0.98f); data.putFloat(200, 24.0f); data.putFloat(204, 1.0f);
            data.putFloat(208, minecraftTimeSeconds(tickDelta));
            data.putFloat(212, rain);
            data.putFloat(216, reversedDepth);
            data.putFloat(220, 1f);
            data.putFloat(224, (float)(rainHeights.originX() - cameraPos.x));
            data.putFloat(228, (float)(rainHeights.originZ() - cameraPos.z));
            data.putFloat(232, RainHeightMap.SIZE);
            data.putFloat(236, Minecraft.getInstance().level.getMinY());
            data.position(0);
            encoder.writeToBuffer(uniformBuffer.slice(0, UNIFORM_SIZE), data);
        }
    }

    private static float minecraftTimeSeconds(float tickDelta) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0f : (minecraft.level.getGameTime() + tickDelta)*0.05f;
    }

    private static float rainIntensity(Ambience ambience, float tickDelta) {
        Atmosphere atmosphere = Atmosphere.getInstance();
         
        if (atmosphere != null && atmosphere.isRainActive() && atmosphere.getRainAmount() > 0f) return 0f;
        if (ambience.getForcedPrecipitation() != Biome.Precipitation.RAIN) return 0f;
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0f : Mth.clamp(minecraft.level.getRainLevel(tickDelta), 0f, 1f);
    }

    public static void close() { closeTargets(); closeBuffer(); rainHeights.close(); disabledAfterError = false; retryAfterMillis = 0L; }
    private static void closeBuffer() { if (uniformBuffer != null) { uniformBuffer.close(); uniformBuffer = null; } }
    private static void closeTargets() {
        if (sceneCopyView != null) { sceneCopyView.close(); sceneCopyView = null; }
        if (sceneCopyTexture != null) { sceneCopyTexture.close(); sceneCopyTexture = null; }
        if (depthCopyView != null) { depthCopyView.close(); depthCopyView = null; }
        if (depthCopyTexture != null) { depthCopyTexture.close(); depthCopyTexture = null; }
        width = height = -1;
    }
}
