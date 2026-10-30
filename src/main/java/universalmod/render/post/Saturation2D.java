package universalmod.render.post;

import universalmod.render.ClientPipelines;
import universalmod.render.RenderSampler;

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




public final class Saturation2D {
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static SimpleFramebuffer sceneCopy;
    private static boolean disabledAfterError;

    private Saturation2D() {
    }

    public static void applyWithCopy(float saturation) {
        if (disabledAfterError || !Float.isFinite(saturation)) {
            return;
        }
        float clamped = Math.max(0.0F, Math.min(2.0F, saturation));
        if (Math.abs(clamped - 1.0F) <= 0.0005F) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer main = client == null ? null : client.getFramebuffer();
        if (main == null || main.textureWidth <= 0 || main.textureHeight <= 0) {
            return;
        }

        try {
            sceneCopy = ensure(sceneCopy, main.textureWidth, main.textureHeight, false);
            sceneCopy.beginWrite(true);
            main.draw(sceneCopy.textureWidth, sceneCopy.textureHeight);
            main.beginWrite(true);

            RenderSystem.disableDepthTest();
            RenderSystem.disableCull();
            RenderSystem.disableBlend();
            RenderSystem.setShaderTexture(0, sceneCopy.getColorAttachment());

            ShaderProgram shader = ClientPipelines.bind(ClientPipelines.SATURATION, "render_saturation");
            if (shader == null) {
                return;
            }
            var uniform = shader.getUniform("saturation");
            if (uniform != null) {
                uniform.set(clamped);
            }
            drawFullscreen();
        } catch (Throwable throwable) {
            disabledAfterError = true;


        } finally {
            main.beginWrite(true);
            RenderSystem.setShaderTexture(0, 0);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    public static void reloadResources() {
        disabledAfterError = false;
        close();
    }

    public static void recoverAfterReload() {
        disabledAfterError = false;
    }

    public static void close() {
        if (sceneCopy != null) {
            sceneCopy.delete();
            sceneCopy = null;
        }
    }

    public static void drawFullscreen() {
        BufferBuilder buffer = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        buffer.vertex(IDENTITY, -1.0F, -1.0F, 0.0F).color(0xFFFFFFFF);
        buffer.vertex(IDENTITY, -1.0F, 1.0F, 0.0F).color(0xFFFFFFFF);
        buffer.vertex(IDENTITY, 1.0F, 1.0F, 0.0F).color(0xFFFFFFFF);
        buffer.vertex(IDENTITY, 1.0F, -1.0F, 0.0F).color(0xFFFFFFFF);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    public static SimpleFramebuffer ensure(SimpleFramebuffer framebuffer, int width, int height, boolean depth) {
        if (framebuffer == null) {
            framebuffer = new SimpleFramebuffer(width, height, depth);
        } else if (framebuffer.textureWidth != width || framebuffer.textureHeight != height) {
            framebuffer.resize(width, height);
        }
        RenderSampler.linear(framebuffer);
        return framebuffer;
    }
}
