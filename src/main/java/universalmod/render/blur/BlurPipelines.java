package universalmod.render.blur;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.Identifier;

final class BlurPipelines {
    static final RenderPipeline BATCHED = RenderPipeline.builder()
            .withLocation(id("pipeline/batched_blur"))
            .withVertexShader(id("ui/batched_blur/batched_blur"))
            .withFragmentShader(id("ui/batched_blur/batched_blur"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_LINE_WIDTH, VertexFormat.Mode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withSampler("Sampler0")
            .withUniform("DynamicTransforms", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("BlurParamsArray", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("BlurRegion", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .build();

    static final RenderPipeline KAWASE_DOWN = RenderPipeline.builder()
            .withLocation(id("pipeline/kawase_down"))
            .withVertexShader(id("ui/kawase/down"))
            .withFragmentShader(id("ui/kawase/down"))
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withSampler("Sampler0")
            .withUniform("KawaseParams", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .build();

    static final RenderPipeline KAWASE_UP = RenderPipeline.builder()
            .withLocation(id("pipeline/kawase_up"))
            .withVertexShader(id("ui/kawase/down"))
            .withFragmentShader(id("ui/kawase/up"))
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withSampler("Sampler0")
            .withUniform("KawaseParams", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .build();

    static final RenderPipeline SCOREBOARD_CROP = RenderPipeline.builder()
            .withLocation(id("pipeline/scoreboard_blur_crop"))
            .withVertexShader(id("ui/scoreboard_blur/scoreboard_blur"))
            .withFragmentShader(id("ui/scoreboard_blur/crop"))
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withSampler("InSampler")
            .withUniform("CropConfig", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .build();

    static final RenderPipeline SCOREBOARD_SEPARABLE = RenderPipeline.builder()
            .withLocation(id("pipeline/scoreboard_separable_blur"))
            .withVertexShader(id("ui/scoreboard_blur/scoreboard_blur"))
            .withFragmentShader(id("ui/scoreboard_blur/scoreboard_blur"))
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withSampler("InSampler")
            .withUniform("SamplerInfo", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("BlurConfig", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .build();

    private BlurPipelines() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("universalmod", path);
    }
}
