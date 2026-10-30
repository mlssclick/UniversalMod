package universalmod.render.blur;

import universalmod.render.GlUiState;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import universalmod.render.ScissorStack;
import universalmod.render.ShaderDiagnostics;
import universalmod.render.UiRender;
import universalmod.render.UiShaders;

 





public final class ScoreboardBlurRenderer {
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static SimpleFramebuffer cropped;
    private static SimpleFramebuffer horizontal;
    private static SimpleFramebuffer vertical;

    private ScoreboardBlurRenderer() {
    }

    public record Region(int texture, int x, int y, int width, int height) {
    }

    public static Region prepare(float radius, float x, float y, float width, float height) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null || width <= 0 || height <= 0) return null;
        Framebuffer main = mc.getFramebuffer();
        if (main == null || main.textureWidth <= 0 || main.textureHeight <= 0) return null;
        if (UiShaders.SCOREBOARD_SEPARABLE == null) {
            ShaderDiagnostics.missing("ui_scoreboard_separable");
            return null;
        }

        BlurScreenRegion.Bounds region = BlurScreenRegion.compute(
                UiRender.matrix(), x, y, width, height, radius,
                mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(),
                main.textureWidth, main.textureHeight);
        if (region == null) return null;

        
        
        
        BlurFramebuffer source = BlurFramebuffer.shared();
        if (source.capture(main) == 0) return null;

        GlUiState savedState = GlUiState.capture();
        ScissorStack.suspend();
        try {
            cropped = ensure(cropped, region.width(), region.height());
            horizontal = ensure(horizontal, region.width(), region.height());
            vertical = ensure(vertical, region.width(), region.height());

            
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.framebufferId());
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, cropped.fbo);
            GL30.glBlitFramebuffer(region.x(), region.y(),
                    region.x() + region.width(), region.y() + region.height(),
                    0, 0, region.width(), region.height(),
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);

            RenderSystem.disableDepthTest();
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            float effectiveRadius = Math.max(1.0f, Math.min(32.0f, radius));
            if (!pass(cropped.getColorAttachment(), horizontal, 1f, 0f, effectiveRadius)) return null;
            if (!pass(horizontal.getColorAttachment(), vertical, 0f, 1f, effectiveRadius)) return null;
            return new Region(vertical.getColorAttachment(), region.x(), region.y(), region.width(), region.height());
        } catch (RuntimeException failure) {
            ShaderDiagnostics.failure("scoreboard_separable", failure);
            return null;
        } finally {
            ScissorStack.resume();
            savedState.close();
        }
    }

    private static boolean pass(int inputTexture, SimpleFramebuffer target,
                                float directionX, float directionY, float radius) {
        target.beginWrite(true);
        RenderSystem.setShaderTexture(0, inputTexture);
        ShaderProgram shader = UiShaders.bind(UiShaders.SCOREBOARD_SEPARABLE, "ui_scoreboard_separable");
        if (shader == null) return false;
        var inSize = shader.getUniform("InSize");
        var direction = shader.getUniform("BlurDir");
        var blurRadius = shader.getUniform("Radius");
        if (inSize == null || direction == null || blurRadius == null) {
            ShaderDiagnostics.invalid("ui_scoreboard_separable", "required uniform missing");
            return false;
        }
        inSize.set((float) target.textureWidth, (float) target.textureHeight);
        direction.set(directionX, directionY);
        blurRadius.set(radius);
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        buffer.vertex(IDENTITY, -1, -1, 0).color(0xFFFFFFFF).next();
        buffer.vertex(IDENTITY, -1, 1, 0).color(0xFFFFFFFF).next();
        buffer.vertex(IDENTITY, 1, 1, 0).color(0xFFFFFFFF).next();
        buffer.vertex(IDENTITY, 1, -1, 0).color(0xFFFFFFFF).next();
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        return true;
    }

    private static SimpleFramebuffer ensure(SimpleFramebuffer framebuffer, int width, int height) {
        if (framebuffer == null) framebuffer = new SimpleFramebuffer(width, height, false, false);
        else if (framebuffer.textureWidth != width || framebuffer.textureHeight != height) {
            framebuffer.resize(width, height, false);
        }
        framebuffer.setTexFilter(GL11.GL_LINEAR);
        return framebuffer;
    }

    public static void close() {
        if (cropped != null) cropped.delete();
        if (horizontal != null) horizontal.delete();
        if (vertical != null) vertical.delete();
        cropped = horizontal = vertical = null;
    }
}
