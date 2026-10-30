package universalmod.render.image;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.ARBSamplerObjects;

 





final class ImageSamplers {
    private static final int SAMPLER_BINDING = 0x8919;
    private static final int CLAMP_TO_EDGE = 0x812F;
    private static int nearest;
    private static int linear;

    private ImageSamplers() { }

    static Binding bind(boolean nearestFilter) {
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        boolean core = GL.getCapabilities().OpenGL33;
        boolean arb = !core && GL.getCapabilities().GL_ARB_sampler_objects;
        if (core || arb) {
            int previous = GL11.glGetInteger(SAMPLER_BINDING);
            int sampler = nearestFilter ? nearest(core) : linear(core);
            bindSampler(core, sampler);
            return new Binding(activeTexture, previous, core);
        }
        
        
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int selectedTexture = RenderSystem.getShaderTexture(0);
        if (selectedTexture <= 0) {
            RenderSystem.activeTexture(activeTexture);
            return new Binding(activeTexture, previousTexture, 0, 0, 0, 0, 0);
        }
        RenderSystem.bindTexture(selectedTexture);
        int min = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
        int mag = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER);
        int wrapS = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S);
        int wrapT = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T);
        int filter = nearestFilter ? GL11.GL_NEAREST : GL11.GL_LINEAR;
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, CLAMP_TO_EDGE);
        RenderSystem.activeTexture(activeTexture);
        return new Binding(activeTexture, previousTexture, selectedTexture, min, mag, wrapS, wrapT);
    }

    private static int nearest(boolean core) {
        if (nearest == 0) nearest = create(GL11.GL_NEAREST, core);
        return nearest;
    }

    private static int linear(boolean core) {
        if (linear == 0) linear = create(GL11.GL_LINEAR, core);
        return linear;
    }

    private static int create(int filter, boolean core) {
        int sampler = core ? GL33.glGenSamplers() : ARBSamplerObjects.glGenSamplers();
        if (core) {
            GL33.glSamplerParameteri(sampler, GL11.GL_TEXTURE_MIN_FILTER, filter);
            GL33.glSamplerParameteri(sampler, GL11.GL_TEXTURE_MAG_FILTER, filter);
            GL33.glSamplerParameteri(sampler, GL11.GL_TEXTURE_WRAP_S, CLAMP_TO_EDGE);
            GL33.glSamplerParameteri(sampler, GL11.GL_TEXTURE_WRAP_T, CLAMP_TO_EDGE);
        } else {
            ARBSamplerObjects.glSamplerParameteri(sampler, GL11.GL_TEXTURE_MIN_FILTER, filter);
            ARBSamplerObjects.glSamplerParameteri(sampler, GL11.GL_TEXTURE_MAG_FILTER, filter);
            ARBSamplerObjects.glSamplerParameteri(sampler, GL11.GL_TEXTURE_WRAP_S, CLAMP_TO_EDGE);
            ARBSamplerObjects.glSamplerParameteri(sampler, GL11.GL_TEXTURE_WRAP_T, CLAMP_TO_EDGE);
        }
        return sampler;
    }

    private static void bindSampler(boolean core, int sampler) {
        if (core) GL33.glBindSampler(0, sampler);
        else ARBSamplerObjects.glBindSampler(0, sampler);
    }

    static void release() {
        if (nearest != 0) {
            if (GL.getCapabilities().OpenGL33) GL33.glDeleteSamplers(nearest);
            else if (GL.getCapabilities().GL_ARB_sampler_objects) ARBSamplerObjects.glDeleteSamplers(nearest);
        }
        if (linear != 0) {
            if (GL.getCapabilities().OpenGL33) GL33.glDeleteSamplers(linear);
            else if (GL.getCapabilities().GL_ARB_sampler_objects) ARBSamplerObjects.glDeleteSamplers(linear);
        }
        nearest = linear = 0;
    }

    static final class Binding implements AutoCloseable {
        private final int activeTexture;
        private final int previous;
        private final boolean core;
        private final int selectedTexture;
        private final int min, mag, wrapS, wrapT;
        private boolean closed;
        private Binding(int activeTexture, int previous, boolean core) {
            this(activeTexture, previous, 0, 0, 0, 0, 0, core);
        }
        private Binding(int activeTexture, int previous, int selectedTexture, int min, int mag, int wrapS, int wrapT) {
            this(activeTexture, previous, selectedTexture, min, mag, wrapS, wrapT, false);
        }
        private Binding(int activeTexture, int previous, int selectedTexture, int min, int mag, int wrapS, int wrapT, boolean core) {
            this.activeTexture = activeTexture;
            this.previous = previous;
            this.core = core;
            this.selectedTexture = selectedTexture;
            this.min = min;
            this.mag = mag;
            this.wrapS = wrapS;
            this.wrapT = wrapT;
        }
        @Override public void close() {
            if (closed) return;
            closed = true;
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            if (selectedTexture == 0) {
                if (GL.getCapabilities().OpenGL33) GL33.glBindSampler(0, previous);
                else if (GL.getCapabilities().GL_ARB_sampler_objects) ARBSamplerObjects.glBindSampler(0, previous);
            } else {
                RenderSystem.bindTexture(selectedTexture);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, min);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, mag);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, wrapS);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, wrapT);
                RenderSystem.bindTexture(previous);
            }
            RenderSystem.activeTexture(activeTexture);
        }
    }
}
