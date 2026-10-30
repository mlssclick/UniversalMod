package universalmod.render.blur;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import universalmod.render.ScissorStack;
import universalmod.render.ShaderDiagnostics;

 










final class BlurFramebuffer implements AutoCloseable {
     
    private static final BlurFramebuffer SHARED = new BlurFramebuffer();
    static BlurFramebuffer shared() { return SHARED; }
    private final BlurCaptureState captureState = new BlurCaptureState();
    private SimpleFramebuffer scene;

    void beginFrame() {
        captureState.beginFrame();
    }

    void invalidateScene() {
        captureState.invalidate();
    }

    long revision() {
        return captureState.epoch();
    }

    int capture(Framebuffer main) {
        RenderSystem.assertOnRenderThread();
        if (main == null || main.textureWidth < 1 || main.textureHeight < 1) return 0;
        if (scene != null && (scene.textureWidth != main.textureWidth
                || scene.textureHeight != main.textureHeight)) {
            captureState.invalidate();
        }
        if (!captureState.needsCapture() && scene != null) return scene.getColorAttachment();

        ScissorStack.suspend();
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] previousViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, previousViewport);
        try {
            if (scene == null) scene = new SimpleFramebuffer(main.textureWidth, main.textureHeight, false, false);
            else if (scene.textureWidth != main.textureWidth || scene.textureHeight != main.textureHeight)
                scene.resize(main.textureWidth, main.textureHeight, false);
            scene.setTexFilter(GL11.GL_LINEAR);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.fbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scene.fbo);
            GL30.glBlitFramebuffer(0, 0, main.textureWidth, main.textureHeight,
                    0, 0, scene.textureWidth, scene.textureHeight,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            captureState.captured();
            return scene.getColorAttachment();
        } catch (RuntimeException failure) {
            captureState.invalidate();
            ShaderDiagnostics.failure("blur_scene_capture", failure);
            return 0;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
            RenderSystem.viewport(previousViewport[0], previousViewport[1],
                    previousViewport[2], previousViewport[3]);
            ScissorStack.resume();
        }
    }

    int framebufferId() { return scene == null ? 0 : scene.fbo; }
    int textureWidth() { return scene == null ? 0 : scene.textureWidth; }
    int textureHeight() { return scene == null ? 0 : scene.textureHeight; }

    @Override public void close() {
        RenderSystem.assertOnRenderThread();
        if (scene != null) {
            scene.delete();
            scene = null;
        }
        captureState.reset();
    }
}
