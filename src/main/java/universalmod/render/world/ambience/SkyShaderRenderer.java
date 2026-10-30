package universalmod.render.world.ambience;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import universalmod.module.render.Ambience;
import universalmod.module.render.Atmosphere;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import java.awt.Color;
import universalmod.render.Render3D;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;

public final class SkyShaderRenderer {
    private static final int UNIFORM_SIZE = 128;
    private static final int ATMOSPHERE_UNIFORM_SIZE = 6 * 16;
    private static final int ALLOCATOR_SIZE = 4096;
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static RenderPipeline skyPipeline;
    private static RenderPipeline auroraPipeline;
    private static RenderPipeline northernLightsPipeline;
    private static RenderPipeline nebulaPipeline;
    private static RenderPipeline plasmaPipeline;
    private static RenderPipeline atmosphereWeatherPipeline;
    private static GpuBuffer atmosphereBuffer;
    private static boolean atmosphereSkyRendered;
    private static boolean regularSkyRendered;
    private static GpuBuffer uniformBuffer;
    private static GpuBuffer skyCubeVertices;
    private static int skyCubeIndexCount;
    private static volatile Config config;
    private static long startTime = System.currentTimeMillis();

    private SkyShaderRenderer() {
    }

    public static void resetTime() {
        startTime = System.currentTimeMillis();
    }

    public static void updateConfig(Ambience module) {
        if (module == null) {
            return;
        }
        int color = module.getSkyShaderColor();
        float r = (color >> 16 & 0xFF) / 255.0F;
        float g = (color >> 8 & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        config = new Config(
                module.getSkyShaderMode(),
                color,
                r,
                g,
                b,
                module.isSkyStarsEnabled(),
                module.getSkyStarDensity(),
                module.getSkyNebulaStrength(),
                module.getSkyPlasmaScale(),
                module.getSkyPlasmaSpeed(),
                module.getSkySpeed()
        );
    }

    public static boolean render(Ambience module) {
        if (module == null || !module.isSkyShaderEnabled()) {
            return false;
        }
        Config current = config;
        if (current == null
                || !current.mode.equals(module.getSkyShaderMode())
                || current.color != module.getSkyShaderColor()
                || current.showStars != module.isSkyStarsEnabled()
                || current.starDensity != module.getSkyStarDensity()
                || current.nebulaStrength != module.getSkyNebulaStrength()
                || current.plasmaScale != module.getSkyPlasmaScale()
                || current.plasmaSpeed != module.getSkyPlasmaSpeed()
                || current.skySpeed != module.getSkySpeed()) {
            updateConfig(module);
            current = config;
        }
        if (current == null) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getMainRenderTarget() == null
                || client.getMainRenderTarget().getColorTextureView() == null
                || client.getMainRenderTarget().getDepthTextureView() == null) {
            return false;
        }
        RenderPipeline pipeline = pipeline(current.mode);
        if (pipeline == null) {
            return false;
        }
        try {
            ensureSkyCube();
            updateUniform(current);
            draw(client, pipeline, "SkyShaderData", uniformBuffer);
            regularSkyRendered = true;
            return true;
        } catch (RuntimeException | LinkageError throwable) {
            regularSkyRendered = false;
            return false;
        }
    }

    public static boolean renderAtmosphere(Atmosphere atmosphere) {
        atmosphereSkyRendered = false;
        if (atmosphere == null || !(atmosphere.isMoodActive() || atmosphere.isWinterActive())) return false;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.gameRenderer == null
                || client.getMainRenderTarget() == null
                || client.getMainRenderTarget().getColorTextureView() == null
                || client.getMainRenderTarget().getDepthTextureView() == null) return false;
        Camera camera = client.gameRenderer.getMainCamera();
        if (camera == null) return false;
        try {
            initAtmospherePipelines();
            ensureAtmosphereBuffer();
            updateAtmosphereUniform(atmosphere, camera);
            ensureSkyCube();
            draw(client, atmosphereWeatherPipeline, "AtmosphereSkyData", atmosphereBuffer);
            atmosphereSkyRendered = true;
            return true;
        } catch (RuntimeException | LinkageError exception) {
            
            
            atmosphereSkyRendered = false;
            return false;
        }
    }

    public static void beginFrame() { atmosphereSkyRendered = false; regularSkyRendered = false; }
    public static boolean wasAnySkyRendered() { return atmosphereSkyRendered || regularSkyRendered; }
    public static boolean wasAtmosphereSkyRendered() { return atmosphereSkyRendered; }
    public static void resetAtmosphereTime() { startTime = System.currentTimeMillis(); atmosphereSkyRendered = false; }

    private static void ensureAtmosphereBuffer() {
        if (atmosphereBuffer == null || atmosphereBuffer.isClosed()) {
            atmosphereBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "universalmod:unified_atmosphere_sky_uniforms",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, ATMOSPHERE_UNIFORM_SIZE);
        }
    }

    private static void updateAtmosphereUniform(Atmosphere settings, Camera camera) {
        float angle = camera.attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE,
                Render3D.lastTickDelta) * Mth.DEG_TO_RAD;
        float sx = -Mth.sin(angle), sy = Mth.cos(angle);
         
         
        float time = settings.isWinterMode()
                ? (System.currentTimeMillis() - startTime) * 0.001F
                : Minecraft.getInstance().level.getGameTime() * 0.05F;
        if (settings.isSakuraMode()) time *= settings.getSakuraSkySpeed();
        Color warmth = settings.getMoodSunColor();
        float r = warmth.getRed() / 255.0F;
        float g = warmth.getGreen() / 255.0F;
        float b = warmth.getBlue() / 255.0F;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.calloc(ATMOSPHERE_UNIFORM_SIZE);
            putVec4(data, 0, sx, sy, 0F, time);
            if (settings.isWinterMode()) {
                putVec4(data, 16, 0.60F, 0.70F, 0.90F, 0F);
                putVec4(data, 32, 0.85F, 0.90F, 1.00F, 0F);
                putVec4(data, 48, 1.00F, 0.94F, 0.79F, 0F);
            } else if (settings.isSakuraMode()) {
                putVec4(data, 16, 0.65F, 0.77F, 0.99F, 0F);
                putVec4(data, 32, 0.99F, 0.75F, 0.86F, 0F);
                putVec4(data, 48, 1.00F, 0.91F, 0.77F, 0F);
            } else if (settings.isThunderMode()) {
                putVec4(data, 16, 0.09F, 0.12F, 0.16F, 0F);
                putVec4(data, 32, 0.20F, 0.22F, 0.26F, 0F);
                putVec4(data, 48, 0.44F, 0.49F, 0.57F, 0F);
            } else if (settings.isAutumnMode()) {
                putVec4(data, 16, 0.37F, 0.41F, 0.47F, 0F);
                putVec4(data, 32, 0.68F, 0.63F, 0.55F, 0F);
                putVec4(data, 48, 0.90F, 0.71F, 0.40F, 0F);
            } else if (settings.isSundayMode()) {
                putVec4(data, 16, 0.25F, 0.48F, 0.82F, 0F);
                putVec4(data, 32, 0.77F, 0.88F, 0.98F, 0F);
                putVec4(data, 48, 1.00F, 0.96F, 0.77F, 0F);
            } else {
                putVec4(data, 16, 0.04F, 0.04F, 0.07F, 0F);
                putVec4(data, 32, 0.21F, 0.14F, 0.17F, 0F);
                putVec4(data, 48, 0.65F, 0.10F, 0.10F, 0F);
            }
            putVec4(data, 80, settings.isWinterMode() ? 1F : settings.getMoodKind(), 0F, 0F, 0F);
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(
                    atmosphereBuffer.slice(0, ATMOSPHERE_UNIFORM_SIZE), data);
        }
    }

    public static void close() {
        if (skyCubeVertices != null) {
            skyCubeVertices.close();
            skyCubeVertices = null;
            skyCubeIndexCount = 0;
        }
        if (uniformBuffer != null) {
            uniformBuffer.close();
            uniformBuffer = null;
        }
        skyPipeline = null;
        auroraPipeline = null;
        northernLightsPipeline = null;
        nebulaPipeline = null;
        plasmaPipeline = null;
        config = null;
        atmosphereSkyRendered = false;
        regularSkyRendered = false;
        if (atmosphereBuffer != null) {
            atmosphereBuffer.close();
            atmosphereBuffer = null;
        }
        atmosphereWeatherPipeline = null;
    }

    private static MeshData buildSkyCube(ByteBufferBuilder allocator) {
        float size = 100.0F;
        BufferBuilder buffer = new BufferBuilder(allocator, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        face(buffer, -size, -size, size, size, -size, size, size, size, size, -size, size, size);
        face(buffer, size, -size, -size, -size, -size, -size, -size, size, -size, size, size, -size);
        face(buffer, -size, -size, -size, -size, -size, size, -size, size, size, -size, size, -size);
        face(buffer, size, -size, size, size, -size, -size, size, size, -size, size, size, size);
        face(buffer, -size, size, size, size, size, size, size, size, -size, -size, size, -size);
        face(buffer, -size, -size, -size, size, -size, -size, size, -size, size, -size, -size, size);
        return buffer.buildOrThrow();
    }

    private static void face(
            BufferBuilder buffer,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float x4, float y4, float z4
    ) {
        buffer.addVertex(x1, y1, z1).setColor(0xFFFFFFFF);
        buffer.addVertex(x2, y2, z2).setColor(0xFFFFFFFF);
        buffer.addVertex(x3, y3, z3).setColor(0xFFFFFFFF);
        buffer.addVertex(x4, y4, z4).setColor(0xFFFFFFFF);
    }

    private static void updateUniform(Config current) {
        ensureUniformBuffer();
        float time = (System.currentTimeMillis() - startTime) / 1000.0F;
        float mode = genericMode(current.mode);
        float stars = current.showStars ? current.starDensity : 0.0F;
        boolean northernLights = "Северное сияние".equals(current.mode);
        float intensity = northernLights ? 0.03F : 0.8F;
        float scale = northernLights ? 5.8F : current.plasmaScale;
        float speed = northernLights ? current.skySpeed * 0.7F : current.plasmaSpeed;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.calloc(UNIFORM_SIZE);
            putVec4(data, 0, time, mode, stars, current.nebulaStrength);
            putVec4(data, 16, intensity, scale, speed, current.showStars ? 1.0F : 0.0F);
            putVec4(data, 32, current.r * 0.10F, current.g * 0.10F, current.b * 0.18F + 0.02F, 0.0F);
            putVec4(data, 48, current.r * 0.45F + 0.04F, current.g * 0.45F + 0.04F, current.b * 0.45F + 0.06F, 0.0F);
            putVec4(data, 64, current.r, current.g, current.b, 0.0F);
            putVec4(data, 80, current.b, Math.min(1.0F, current.r + 0.25F), Math.min(1.0F, current.g + 0.20F), 0.0F);
            putVec4(data, 96, 0.90F, 0.94F, 1.0F, 0.0F);
            putVec4(data, 112, current.r, current.g, current.b, 0.0F);
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(uniformBuffer.slice(0, UNIFORM_SIZE), data);
        }
    }

    private static void ensureSkyCube() {
        if (skyCubeVertices != null && !skyCubeVertices.isClosed()) {
            return;
        }
        try (ByteBufferBuilder allocator = new ByteBufferBuilder(ALLOCATOR_SIZE);
             MeshData mesh = buildSkyCube(allocator)) {
            skyCubeIndexCount = mesh.drawState().indexCount();
            skyCubeVertices = RenderSystem.getDevice().createBuffer(
                    () -> "universalmod:sky_shader_cube",
                    GpuBuffer.USAGE_VERTEX,
                    mesh.vertexBuffer());
        }
    }

    private static void draw(Minecraft client, RenderPipeline pipeline, String block, GpuBuffer ubo) {
        RenderSystem.AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indices = sequential.getBuffer(skyCubeIndexCount);
        Matrix4f skyView = new Matrix4f(Render3D.lastModMat);
        skyView.m30(0.0F);
        skyView.m31(0.0F);
        skyView.m32(0.0F);
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
                skyView, COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);
        drawPass(client, pipeline, skyCubeVertices, indices, skyCubeIndexCount, sequential.type(),
                dynamicTransforms, block, ubo);
    }

    private static void drawPass(Minecraft client, RenderPipeline pipeline, GpuBuffer vertices,
                                 GpuBuffer indices, int indexCount, VertexFormat.IndexType indexType,
                                 GpuBufferSlice dynamicTransforms, String block, GpuBuffer ubo) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (RenderPass pass = encoder.createRenderPass(
                () -> "universalmod:sky_shader",
                client.getMainRenderTarget().getColorTextureView(),
                java.util.OptionalInt.empty(),
                client.getMainRenderTarget().getDepthTextureView(),
                OptionalDouble.empty()
        )) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamicTransforms);
            pass.setUniform(block, ubo);
            pass.setVertexBuffer(0, vertices);
            pass.setIndexBuffer(indices, indexType);
            pass.drawIndexed(0, 0, indexCount, 1);
        }
    }

    private static void ensureUniformBuffer() {
        if (uniformBuffer == null || uniformBuffer.isClosed() || uniformBuffer.size() < UNIFORM_SIZE) {
            if (uniformBuffer != null) {
                uniformBuffer.close();
            }
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "universalmod:sky_shader_uniforms",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UNIFORM_SIZE
            );
        }
    }

    private static RenderPipeline pipeline(String mode) {
        initPipelines();
        return switch (mode) {
            case "Aurora" -> auroraPipeline;
            case "Северное сияние" -> northernLightsPipeline;
            case "Nebula" -> nebulaPipeline;
            case "Plasma" -> plasmaPipeline;
            default -> skyPipeline;
        };
    }

    private static void initPipelines() {
        if (skyPipeline != null) {
            return;
        }
        skyPipeline = register("sky", "sky/sky");
        auroraPipeline = register("sky_aurora", "sky/sky_aurora");
        northernLightsPipeline = register("sky_severnoesiyanie", "sky/sky_severnoesiyanie");
        nebulaPipeline = register("sky_nebula", "sky/sky_nebula");
        plasmaPipeline = register("sky_plasma", "sky/sky_plasma");

    }

    private static RenderPipeline register(String name, String fragment) {
        return RenderPipelines.register(
                RenderPipeline.builder()
                        .withLocation(Identifier.fromNamespaceAndPath("universalmod", "pipeline/sky/" + name))
                        .withVertexShader(Identifier.fromNamespaceAndPath("universalmod", "sky/sky"))
                        .withFragmentShader(Identifier.fromNamespaceAndPath("universalmod", fragment))
                        .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                        .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                        .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                        .withUniform("SkyShaderData", UniformType.UNIFORM_BUFFER)
                        .withCull(false)
                        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                        .withDepthWrite(false)
                        .build()
        );
    }

    private static void initAtmospherePipelines() {
        if (atmosphereWeatherPipeline == null) {
            atmosphereWeatherPipeline = registerAtmosphere("atmosphere_dust_ash", "sky/atmosphere_dust_ash");
        }
    }

    private static RenderPipeline registerAtmosphere(String name, String fragment) {
        return RenderPipelines.register(RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("universalmod", "pipeline/sky/" + name))
                .withVertexShader(Identifier.fromNamespaceAndPath("universalmod", "sky/sky"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("universalmod", fragment))
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withUniform("AtmosphereSkyData", UniformType.UNIFORM_BUFFER)
                .withCull(false)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .build());
    }

    private static float genericMode(String mode) {
        return switch (mode) {
            case "Cosmic Veil" -> 2.0F;
            case "Deep Space" -> 3.0F;
            case "Void" -> 4.0F;
            default -> 0.0F;
        };
    }

    private static void putVec4(ByteBuffer data, int offset, float x, float y, float z, float w) {
        data.putFloat(offset, x);
        data.putFloat(offset + 4, y);
        data.putFloat(offset + 8, z);
        data.putFloat(offset + 12, w);
    }

    private record Config(
            String mode,
            int color,
            float r,
            float g,
            float b,
            boolean showStars,
            float starDensity,
            float nebulaStrength,
            float plasmaScale,
            float plasmaSpeed,
            float skySpeed
    ) {
    }
}
