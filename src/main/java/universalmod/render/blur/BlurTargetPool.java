package universalmod.render.blur;

import net.minecraft.client.gl.SimpleFramebuffer;
import org.lwjgl.opengl.GL11;

 




final class BlurTargetPool implements AutoCloseable {
    private final SimpleFramebuffer[] downTargets;
    private final SimpleFramebuffer[] upTargets;

    BlurTargetPool(int maxIterations) {
        int capacity = Math.max(1, maxIterations);
        downTargets = new SimpleFramebuffer[capacity];
        upTargets = new SimpleFramebuffer[capacity];
    }

    void ensure(int width, int height, int iterations) {
        int count = Math.max(0, Math.min(iterations, downTargets.length));
        int w = Math.max(1, width);
        int h = Math.max(1, height);
        for (int i = 0; i < count; i++) {
            downTargets[i] = ensureTarget(downTargets[i], w, h);
            if (i < count - 1) upTargets[i] = ensureTarget(upTargets[i], w, h);
            w = Math.max(1, w / 2);
            h = Math.max(1, h / 2);
        }
    }

    SimpleFramebuffer down(int index) { return downTargets[index]; }
    SimpleFramebuffer up(int index) { return upTargets[index]; }

    private static SimpleFramebuffer ensureTarget(SimpleFramebuffer target, int width, int height) {
        if (target == null) target = new SimpleFramebuffer(width, height, false, false);
        else if (target.textureWidth != width || target.textureHeight != height)
            target.resize(width, height, false);
        target.setTexFilter(GL11.GL_LINEAR);
        return target;
    }

    @Override public void close() {
        for (int i = 0; i < downTargets.length; i++) {
            if (downTargets[i] != null) { downTargets[i].delete(); downTargets[i] = null; }
            if (upTargets[i] != null) { upTargets[i].delete(); upTargets[i] = null; }
        }
    }
}
