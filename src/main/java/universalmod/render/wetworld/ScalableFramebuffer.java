package universalmod.render.wetworld;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.opengl.GL11;

public final class ScalableFramebuffer extends Framebuffer {
    public float scale = 1.0f;
    public boolean linearFilter;

    public ScalableFramebuffer(boolean useDepth) {
        super(useDepth);
    }

    public ScalableFramebuffer(int width, int height, boolean useDepth) {
        super(useDepth);
        resize(width, height);
    }

    public void bindAndClear() {
        bind(true);
    }

    public void bind(boolean clear) {
        resizeIfNeeded();
        if (clear) {
            clear();
            setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        }
        beginWrite(false);
    }

    private boolean needsResize() {
        int width = Math.max(1, (int) Math.floor(MinecraftClient.getInstance().getWindow().getFramebufferWidth() * scale));
        int height = Math.max(1, (int) Math.floor(MinecraftClient.getInstance().getWindow().getFramebufferHeight() * scale));
        return textureWidth != width || textureHeight != height;
    }

    public ScalableFramebuffer enableLinearFilter() {
        linearFilter = true;
        if (colorAttachment > 0) {
            RenderSystem.recordRenderCall(this::performLinearFilterUpdate);
        }
        return this;
    }

    public ScalableFramebuffer setScale(float scale) {
        this.scale = Math.max(0.1f, Math.min(1.0f, scale));
        return this;
    }

    public void unbind() {
        endWrite();
        MinecraftClient.getInstance().getFramebuffer().beginWrite(true);
    }

    private void performLinearFilterUpdate() {
        if (colorAttachment > 0) {
            setTexFilter(GL11.GL_LINEAR);
        }
    }

    @Override
    public void setTexFilter(int texFilter) {
        super.setTexFilter(linearFilter ? GL11.GL_LINEAR : texFilter);
    }

    @Override
    public void initFbo(int width, int height) {
        super.initFbo(width, height);
        if (linearFilter) {
            super.setTexFilter(GL11.GL_LINEAR);
        }
    }

    private void resizeIfNeeded() {
        if (needsResize()) {
            int width = Math.max(1, (int) Math.floor(MinecraftClient.getInstance().getWindow().getFramebufferWidth() * scale));
            int height = Math.max(1, (int) Math.floor(MinecraftClient.getInstance().getWindow().getFramebufferHeight() * scale));

            resize(width, height);
        }
    }
}
