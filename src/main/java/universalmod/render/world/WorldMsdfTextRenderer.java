package universalmod.render.world;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import universalmod.render.RenderLayerFactory;
import universalmod.render.color.ColorUtil;
import universalmod.render.font.FontType;
import universalmod.render.font.msdf.MsdfIconRenderer;

import java.util.List;
import java.util.function.Function;

 





public final class WorldMsdfTextRenderer {
    private static final Identifier SHADER = id("world/world_lyrics_msdf");
    private static final float LAYOUT_SIZE = 32.0F;
    private static final float EDGE_FEATHER = 0.95F;
     
     
    private static final VertexFormat WORLD_MSDF_FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("UV0", VertexFormatElement.UV0)
            .add("Color", VertexFormatElement.COLOR)
            .build();

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                    .withLocation(id("pipeline/world_lyrics_msdf"))
                    .withVertexShader(SHADER)
                    .withFragmentShader(SHADER)
                    .withVertexFormat(WORLD_MSDF_FORMAT, VertexFormat.Mode.QUADS)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withSampler("Sampler0")
                    .build());
    private static final Function<Identifier, RenderType> TYPES = Util.memoize(
            texture -> RenderLayerFactory.create("world_lyrics_msdf", 8192, PIPELINE, texture));

    private WorldMsdfTextRenderer() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("universalmod", path);
    }

    public static MsdfIconRenderer.GlyphLayout layout(String word) {
         
        return MsdfIconRenderer.getInstance().glyphLayout(FontType.DEFAULT, word, LAYOUT_SIZE);
    }

    public static void drawWord(MultiBufferSource.BufferSource buffers, PoseStack.Pose pose,
                                MsdfIconRenderer.GlyphLayout layout, float progress,
                                int baseColor, int accentColor) {
        if (layout == null || layout.empty()) return;
        VertexConsumer consumer = buffers.getBuffer(TYPES.apply(layout.textureId()));
        List<MsdfIconRenderer.Glyph> glyphs = layout.glyphs();
         
         
        for (MsdfIconRenderer.Glyph glyph : glyphs) {
            quad(consumer, pose, glyph, glyph.x0(), glyph.x1(), baseColor);
        }
        float reveal = WorldLyricsAnimation.clamp01(progress) * layout.width();
        if (reveal <= 0.0F) return;
        for (MsdfIconRenderer.Glyph glyph : glyphs) {
            float end = Math.min(reveal - EDGE_FEATHER * 0.5F, glyph.x1());
            if (end > glyph.x0()) quad(consumer, pose, glyph, glyph.x0(), end, accentColor);
             
            float featherStart = Math.max(glyph.x0(), reveal - EDGE_FEATHER * 0.5F);
            float featherEnd = Math.min(glyph.x1(), reveal + EDGE_FEATHER * 0.5F);
            if (featherEnd > featherStart) {
                int soft = ColorUtil.withAlpha(accentColor,
                        Math.round(ColorUtil.getAlpha(accentColor) * 0.40F));
                quad(consumer, pose, glyph, featherStart, featherEnd, soft);
            }
        }
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose,
                             MsdfIconRenderer.Glyph glyph, float startX, float endX, int color) {
        float span = glyph.x1() - glyph.x0();
        if (span <= 0.00001F || endX <= startX || ColorUtil.getAlpha(color) <= 0) return;
        float u0 = glyph.u0() + (startX - glyph.x0()) / span * (glyph.u1() - glyph.u0());
        float u1 = glyph.u0() + (endX - glyph.x0()) / span * (glyph.u1() - glyph.u0());
         
        vertex(consumer, pose, startX, glyph.y0(), u0, glyph.v0(), color);
        vertex(consumer, pose, startX, glyph.y1(), u0, glyph.v1(), color);
        vertex(consumer, pose, endX, glyph.y1(), u1, glyph.v1(), color);
        vertex(consumer, pose, endX, glyph.y0(), u1, glyph.v0(), color);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose,
                               float x, float y, float u, float v, int color) {
        consumer.addVertex(pose, x, y, 0.0F)
                .setUv(u, v)
                .setColor(color);
    }

    public static void flush(MultiBufferSource.BufferSource buffers, Identifier atlasTexture) {
        if (atlasTexture != null) buffers.endBatch(TYPES.apply(atlasTexture));
    }
}
