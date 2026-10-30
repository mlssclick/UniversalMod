package universalmod.render;

import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.AbstractTexture;
import org.lwjgl.opengl.GL11;







public final class RenderSampler {
    private RenderSampler() {
    }

    public static void linear(Framebuffer framebuffer) {
        if (framebuffer != null) {
            framebuffer.setTexFilter(GL11.GL_LINEAR);
        }
    }

    public static void nearest(Framebuffer framebuffer) {
        if (framebuffer != null) {
            framebuffer.setTexFilter(GL11.GL_NEAREST);
        }
    }

    public static void linear(AbstractTexture texture) {
        if (texture != null) {
            texture.setFilter(true, false);
        }
    }

    public static void nearest(AbstractTexture texture) {
        if (texture != null) {
            texture.setFilter(false, false);
        }
    }
}
