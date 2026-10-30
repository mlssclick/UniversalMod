package universalmod.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgram;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

 










public final class GlUiState implements AutoCloseable {
    private final ShaderProgram shader;
    private final int texture0;
    private final float[] shaderColor;
    private final boolean blend;
    private final boolean depth;
    private final boolean cull;
    private final boolean scissor;
    private final boolean depthWrite;
    private final int[] colorMask = new int[4];
    private final int[] scissorBox = new int[4];
    private final int[] viewport = new int[4];
    private final int readFramebuffer;
    private final int drawFramebuffer;
    private final int blendSrcRgb;
    private final int blendDstRgb;
    private final int blendSrcAlpha;
    private final int blendDstAlpha;
    private final int blendEquation;
    private final float lineWidth;
    private boolean closed;

    private GlUiState() {
        RenderSystem.assertOnRenderThread();
        shader = RenderSystem.getShader();
        texture0 = RenderSystem.getShaderTexture(0);
        shaderColor = RenderSystem.getShaderColor().clone();
        blend = GL11.glIsEnabled(GL11.GL_BLEND);
        depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        GL11.glGetIntegerv(GL11.GL_COLOR_WRITEMASK, colorMask);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        blendEquation = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        lineWidth = GL11.glGetFloat(GL11.GL_LINE_WIDTH);
    }

    public static GlUiState capture() { return new GlUiState(); }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        RenderSystem.assertOnRenderThread();
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        RenderSystem.colorMask(colorMask[0] != 0, colorMask[1] != 0,
                colorMask[2] != 0, colorMask[3] != 0);
        RenderSystem.depthMask(depthWrite);
        RenderSystem.lineWidth(lineWidth);
        RenderSystem.blendEquation(blendEquation);
        RenderSystem.blendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
        if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
        if (scissor) RenderSystem.enableScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
        else RenderSystem.disableScissor();
        RenderSystem.setShaderTexture(0, texture0);
        RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
        RenderSystem.setShader(() -> shader);
    }
}
