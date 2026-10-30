package universalmod.render.blur;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.render.TextureSetup;
import org.lwjgl.system.MemoryStack;
import universalmod.render.color.ColorUtil;
import universalmod.render.gpu.GpuUniformBuffer;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

final class ScoreboardBlurChain implements AutoCloseable {
    private static final int CROP_UNIFORM_BYTES = 4 * Float.BYTES;
    private static final int SAMPLER_INFO_BYTES = 4 * Float.BYTES;
    private static final int BLUR_CONFIG_BYTES = 4 * Float.BYTES;

    private final GpuUniformBuffer cropConfig = new GpuUniformBuffer(
            "UNIVERSALMOD_scoreboard_crop_config", CROP_UNIFORM_BYTES);
    private final GpuUniformBuffer samplerInfo = new GpuUniformBuffer(
            "UNIVERSALMOD_scoreboard_sampler_info", SAMPLER_INFO_BYTES);
    private final GpuUniformBuffer blurConfig = new GpuUniformBuffer(
            "UNIVERSALMOD_scoreboard_blur_config", BLUR_CONFIG_BYTES);

    private TextureTarget sourceTarget;
    private TextureTarget horizontalTarget;
    private TextureTarget resultTarget;
    private TextureSetup textureSetup = TextureSetup.noTexture();
    private float maxRadius;

    void beginFrame() {
        maxRadius = 0.0f;
        textureSetup = TextureSetup.noTexture();
    }

    void request(float radius) {
        if (Float.isFinite(radius)) {
            maxRadius = Math.max(maxRadius, radius);
        }
    }

    boolean prepare(int width, int height) {
        if (width <= 0 || height <= 0) {
            textureSetup = TextureSetup.noTexture();
            return false;
        }

        try {
            sourceTarget = ensureTarget(sourceTarget, "UNIVERSALMOD_scoreboard_blur_source", width, height);
            horizontalTarget = ensureTarget(horizontalTarget, "UNIVERSALMOD_scoreboard_blur_horizontal", width, height);
            resultTarget = ensureTarget(resultTarget, "UNIVERSALMOD_scoreboard_blur_result", width, height);
        } catch (RuntimeException failure) {
            textureSetup = TextureSetup.noTexture();
            return false;
        }

        if (resultTarget == null || resultTarget.getColorTextureView() == null) {
            textureSetup = TextureSetup.noTexture();
            return false;
        }

        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        textureSetup = TextureSetup.singleTexture(resultTarget.getColorTextureView(), sampler);
        return true;
    }

    TextureSetup textureSetup() {
        return textureSetup;
    }

    void render(
            RenderTarget mainTarget,
            GpuTextureView sourceOverride,
            int regionX,
            int regionY,
            int regionWidth,
            int regionHeight,
            GpuBuffer fullscreenQuad
    ) {
        if (mainTarget == null
                || fullscreenQuad == null
                || sourceTarget == null
                || horizontalTarget == null
                || resultTarget == null) {
            return;
        }

        GpuTextureView source = sourceOverride != null ? sourceOverride : mainTarget.getColorTextureView();
        if (source == null) {
            return;
        }

        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        float sourceX = (float) regionX / Math.max(mainTarget.width, 1);
        float sourceY = (float) regionY / Math.max(mainTarget.height, 1);
        float sourceW = (float) regionWidth / Math.max(mainTarget.width, 1);
        float sourceH = (float) regionHeight / Math.max(mainTarget.height, 1);

        renderCropPass(source, sourceTarget, sourceX, sourceY, sourceW, sourceH, sampler, fullscreenQuad);

        float radius = clamp(maxRadius, 1.0f, 32.0f);
        renderDirectionalPass(
                sourceTarget.getColorTextureView(),
                sourceTarget.width,
                sourceTarget.height,
                horizontalTarget,
                1.0f,
                0.0f,
                radius,
                sampler,
                fullscreenQuad
        );
        renderDirectionalPass(
                horizontalTarget.getColorTextureView(),
                horizontalTarget.width,
                horizontalTarget.height,
                resultTarget,
                0.0f,
                1.0f,
                radius,
                sampler,
                fullscreenQuad
        );
    }

    private void renderCropPass(
            GpuTextureView source,
            TextureTarget target,
            float sourceX,
            float sourceY,
            float sourceW,
            float sourceH,
            GpuSampler sampler,
            GpuBuffer fullscreenQuad
    ) {
        if (source == null || target == null || target.getColorTextureView() == null) {
            return;
        }

        GpuBuffer configBuffer = cropConfig.buffer();
        if (configBuffer == null) {
            return;
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        RenderSystem.AutoStorageIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indices = indexBuffer.getBuffer(6);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer config = stack.calloc(CROP_UNIFORM_BYTES);
            config.putFloat(0, sourceX);
            config.putFloat(4, sourceY);
            config.putFloat(8, sourceW);
            config.putFloat(12, sourceH);
            config.position(0);
            encoder.writeToBuffer(configBuffer.slice(0, CROP_UNIFORM_BYTES), config);

            try (RenderPass pass = encoder.createRenderPass(
                    () -> "UNIVERSALMOD_scoreboard_blur_crop",
                    target.getColorTextureView(),
                    OptionalInt.of(ColorUtil.TRANSPARENT),
                    null,
                    OptionalDouble.empty()
            )) {
                pass.setPipeline(BlurPipelines.SCOREBOARD_CROP);
                pass.bindTexture("InSampler", source, sampler);
                pass.setUniform("CropConfig", configBuffer);
                pass.setVertexBuffer(0, fullscreenQuad);
                pass.setIndexBuffer(indices, indexBuffer.type());
                pass.drawIndexed(0, 0, 6, 1);
            }
        } catch (RuntimeException failure) {
            
        }
    }

    private void renderDirectionalPass(
            GpuTextureView source,
            int sourceWidth,
            int sourceHeight,
            TextureTarget target,
            float directionX,
            float directionY,
            float radius,
            GpuSampler sampler,
            GpuBuffer fullscreenQuad
    ) {
        if (source == null || target == null || target.getColorTextureView() == null) {
            return;
        }

        GpuBuffer samplerInfoBuffer = samplerInfo.buffer();
        GpuBuffer blurConfigBuffer = blurConfig.buffer();
        if (samplerInfoBuffer == null || blurConfigBuffer == null) {
            return;
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        RenderSystem.AutoStorageIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indices = indexBuffer.getBuffer(6);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer samplerData = stack.calloc(SAMPLER_INFO_BYTES);
            samplerData.putFloat(0, target.width);
            samplerData.putFloat(4, target.height);
            samplerData.putFloat(8, Math.max(sourceWidth, 1));
            samplerData.putFloat(12, Math.max(sourceHeight, 1));
            samplerData.position(0);

            ByteBuffer blurData = stack.calloc(BLUR_CONFIG_BYTES);
            blurData.putFloat(0, directionX);
            blurData.putFloat(4, directionY);
            blurData.putFloat(8, radius);
            blurData.putFloat(12, 0.0f);
            blurData.position(0);

            encoder.writeToBuffer(samplerInfoBuffer.slice(0, SAMPLER_INFO_BYTES), samplerData);
            encoder.writeToBuffer(blurConfigBuffer.slice(0, BLUR_CONFIG_BYTES), blurData);

            try (RenderPass pass = encoder.createRenderPass(
                    () -> "UNIVERSALMOD_scoreboard_directional_blur",
                    target.getColorTextureView(),
                    OptionalInt.of(ColorUtil.TRANSPARENT),
                    null,
                    OptionalDouble.empty()
            )) {
                pass.setPipeline(BlurPipelines.SCOREBOARD_SEPARABLE);
                pass.bindTexture("InSampler", source, sampler);
                pass.setUniform("SamplerInfo", samplerInfoBuffer);
                pass.setUniform("BlurConfig", blurConfigBuffer);
                pass.setVertexBuffer(0, fullscreenQuad);
                pass.setIndexBuffer(indices, indexBuffer.type());
                pass.drawIndexed(0, 0, 6, 1);
            }
        } catch (RuntimeException failure) {
            
        }
    }

    private static TextureTarget ensureTarget(TextureTarget target, String name, int width, int height) {
        if (target == null) {
            return new TextureTarget(name, width, height, false);
        }
        if (target.width != width || target.height != height) {
            target.resize(width, height);
        }
        return target;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public void close() {
        cropConfig.close();
        samplerInfo.close();
        blurConfig.close();
        destroyTarget(sourceTarget);
        destroyTarget(horizontalTarget);
        destroyTarget(resultTarget);
        sourceTarget = null;
        horizontalTarget = null;
        resultTarget = null;
        textureSetup = TextureSetup.noTexture();
        maxRadius = 0.0f;
    }

    private static void destroyTarget(TextureTarget target) {
        if (target == null) {
            return;
        }
        try {
            target.destroyBuffers();
        } catch (RuntimeException failure) {
            
        }
    }
}
