package universalmod.render.world;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import universalmod.render.ClientPipelines;
import universalmod.render.Render3D;
import universalmod.render.font.FontType;
import universalmod.render.font.msdf.MsdfIconRenderer;
import universalmod.render.world.WorldLyricsAnimation;

import java.util.List;

public final class WorldMsdfTextRenderer {
    private static final float LAYOUT_SIZE = 32.0F;
    private static final float EDGE_FEATHER = 0.95F;

    private static BufferBuilder builder;
    private static int textureBefore;
    private static ShaderProgram shaderBefore;
    private static boolean wasBlending, wasDepthTest, wasCull, wasDepthWrite;
    private static boolean submitted;

    private WorldMsdfTextRenderer() {}

    public static MsdfIconRenderer.GlyphLayout layout(String text) {
        return MsdfIconRenderer.getInstance().glyphLayout(FontType.DEFAULT, text, LAYOUT_SIZE);
    }

    public static boolean beginBatch(Identifier atlasTexture) {
        if (builder != null) throw new IllegalStateException("World MSDF frame already begun");
        if (atlasTexture == null) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return false;
        AbstractTexture texture = client.getTextureManager().getTexture(atlasTexture);
        if (texture == null) return false;
        ShaderProgram previousShader = RenderSystem.getShader();
        ShaderProgram shader = ClientPipelines.bind(ClientPipelines.WORLD_LYRICS_MSDF, "world_lyrics_msdf");
        if (shader == null) {
            if (previousShader != null) RenderSystem.setShader(previousShader);
            else RenderSystem.clearShader();
            return false;
        }
        wasBlending = GL11.glIsEnabled(GL11.GL_BLEND);
        wasDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        wasCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        wasDepthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        textureBefore = RenderSystem.getShaderTexture(0);
        shaderBefore = previousShader;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShaderTexture(0, texture.getGlId());
        RenderSystem.setShader(shader);

        var modelView = shader.getUniform("ModelViewMat");
        if (modelView != null) modelView.set(new Matrix4f());
        var projection = shader.getUniform("ProjMat");
        if (projection != null) projection.set(Render3D.lastProjMat);
        builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        submitted = false;
        return true;
    }

    public static void drawWord(MatrixStack.Entry pose, MsdfIconRenderer.GlyphLayout layout,
                                float progress, int baseColor, int accentColor) {
        if (builder == null || layout == null || layout.empty()) return;
        List<MsdfIconRenderer.LayoutGlyph> glyphs = layout.glyphs();
        for (MsdfIconRenderer.LayoutGlyph glyph : glyphs) {
            quad(pose, glyph, glyph.x0(), glyph.x1(), baseColor);
        }
        float reveal = WorldLyricsAnimation.clamp01(progress) * layout.width();
        if (reveal <= 0.0F) return;
        for (MsdfIconRenderer.LayoutGlyph glyph : glyphs) {
            float end = Math.min(reveal - EDGE_FEATHER * 0.5F, glyph.x1());
            if (end > glyph.x0()) quad(pose, glyph, glyph.x0(), end, accentColor);
            float start = Math.max(glyph.x0(), reveal - EDGE_FEATHER * 0.5F);
            float featherEnd = Math.min(glyph.x1(), reveal + EDGE_FEATHER * 0.5F);
            if (featherEnd > start) {
                int soft = (accentColor & 0x00FFFFFF)
                        | (Math.round(((accentColor >>> 24) & 255) * 0.40F) << 24);
                quad(pose, glyph, start, featherEnd, soft);
            }
        }
    }

    private static void quad(MatrixStack.Entry pose, MsdfIconRenderer.LayoutGlyph glyph,
                             float x0, float x1, int argb) {
        float width = glyph.x1() - glyph.x0();
        if (width <= 0.00001F || x1 <= x0 || (argb >>> 24) == 0) return;
        float u0 = glyph.u0() + (x0 - glyph.x0()) / width * (glyph.u1() - glyph.u0());
        float u1 = glyph.u0() + (x1 - glyph.x0()) / width * (glyph.u1() - glyph.u0());
        vertex(pose, x0, glyph.y0(), u0, glyph.v0(), argb);
        vertex(pose, x0, glyph.y1(), u0, glyph.v1(), argb);
        vertex(pose, x1, glyph.y1(), u1, glyph.v1(), argb);
        vertex(pose, x1, glyph.y0(), u1, glyph.v0(), argb);
        submitted = true;
    }

    private static void vertex(MatrixStack.Entry pose, float x, float y, float u, float v, int argb) {
        builder.vertex(pose.getPositionMatrix(), x, y, 0.0F).texture(u, v).color(argb);
    }

    public static void flush() {
        if (builder == null) return;
        try {
            if (submitted) BufferRenderer.drawWithGlobalProgram(builder.end());
            else builder.end();
        } finally {
            builder = null;
            submitted = false;
            RenderSystem.setShaderTexture(0, textureBefore);
            if (shaderBefore != null) RenderSystem.setShader(shaderBefore);
            else RenderSystem.clearShader();
            if (wasDepthWrite) RenderSystem.depthMask(true);
            if (!wasDepthTest) RenderSystem.disableDepthTest();
            if (wasDepthTest) RenderSystem.enableDepthTest();
            if (wasCull) RenderSystem.enableCull();
            else RenderSystem.disableCull();
            if (wasBlending) RenderSystem.enableBlend();
            else RenderSystem.disableBlend();
        }
    }
}
