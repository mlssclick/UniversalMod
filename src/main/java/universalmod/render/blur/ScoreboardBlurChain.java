package universalmod.render.blur;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import universalmod.render.ClientPipelines;
import universalmod.render.RenderSampler;

final class ScoreboardBlurChain implements AutoCloseable {
    private static final Matrix4f IDENTITY = new Matrix4f();
    private SimpleFramebuffer sourceTarget;
    private SimpleFramebuffer horizontalTarget;
    private SimpleFramebuffer resultTarget;

    int render(int sceneTexture, int sceneWidth, int sceneHeight, BlurRegion region, float radius) {
        if (sceneTexture <= 0 || sceneWidth <= 0 || sceneHeight <= 0 || region == null) return 0;
        if (region.width() <= 0 || region.height() <= 0) return 0;
        sourceTarget = ensure(sourceTarget, region.width(), region.height());
        horizontalTarget = ensure(horizontalTarget, region.width(), region.height());
        resultTarget = ensure(resultTarget, region.width(), region.height());
        if (!drawCrop(sceneTexture, sceneWidth, sceneHeight, sourceTarget, region)) return 0;

        float clamped = Math.max(1.0f, Math.min(32.0f, radius));
        if (!drawDirection(sourceTarget.getColorAttachment(), sourceTarget.textureWidth,
                sourceTarget.textureHeight, horizontalTarget, 1.0f, 0.0f, clamped)) return 0;
        if (!drawDirection(horizontalTarget.getColorAttachment(), horizontalTarget.textureWidth,
                horizontalTarget.textureHeight, resultTarget, 0.0f, 1.0f, clamped)) return 0;
        return resultTarget.getColorAttachment();
    }

    private static boolean drawCrop(int texture, int w, int h, SimpleFramebuffer out, BlurRegion r) {
        return drawPass(ClientPipelines.SCOREBOARD_CROP, "ui_scoreboard_crop", texture, out,
                s -> {
                    var u = s.getUniform("SourceRect");
                    if (u == null) return false;
                    u.set(r.x() / (float) w, r.y() / (float) h,
                            r.width() / (float) w, r.height() / (float) h);
                    return true;
                });
    }

    private static boolean drawDirection(int texture, int w, int h, SimpleFramebuffer out,
                                         float dx, float dy, float radius) {
        return drawPass(ClientPipelines.SCOREBOARD_SEPARABLE, "ui_scoreboard_separable", texture, out,
                s -> {
                    var size = s.getUniform("InSize");
                    var direction = s.getUniform("BlurDir");
                    var r = s.getUniform("Radius");
                    if (size == null || direction == null || r == null) return false;
                    size.set((float) w, (float) h);
                    direction.set(dx, dy);
                    r.set(radius);
                    return true;
                });
    }

    private interface Configure { boolean apply(ShaderProgram shader); }

    private static boolean drawPass(ShaderProgramKey key, String name, int texture,
                                    SimpleFramebuffer out, Configure configure) {
        if (texture <= 0 || out == null) return false;
        out.beginWrite(true);
        RenderSystem.setShaderTexture(0, texture);
        ShaderProgram shader = ClientPipelines.bind(key, name);
        if (shader == null || !configure.apply(shader)) return false;
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        buffer.vertex(IDENTITY, -1, -1, 0).color(0xFFFFFFFF);
        buffer.vertex(IDENTITY, -1,  1, 0).color(0xFFFFFFFF);
        buffer.vertex(IDENTITY,  1,  1, 0).color(0xFFFFFFFF);
        buffer.vertex(IDENTITY,  1, -1, 0).color(0xFFFFFFFF);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        return true;
    }

    private static SimpleFramebuffer ensure(SimpleFramebuffer old, int width, int height) {
        if (old == null) old = new SimpleFramebuffer(width, height, false);
        else if (old.textureWidth != width || old.textureHeight != height) old.resize(width, height);
        RenderSampler.linear(old);
        return old;
    }

    @Override public void close() {
        if (sourceTarget != null) sourceTarget.delete();
        if (horizontalTarget != null) horizontalTarget.delete();
        if (resultTarget != null) resultTarget.delete();
        sourceTarget = horizontalTarget = resultTarget = null;
    }
}
