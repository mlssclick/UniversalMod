package universalmod.render.glass;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.BufferUtils;
import universalmod.render.gpu.GpuUniformBuffer;
import universalmod.render.gpu.RenderPass;
import universalmod.render.gpu.IndexedGuiBatch;

final class GlassRenderUniforms {
    private static final GpuUniformBuffer REGION = new GpuUniformBuffer("Glass BlurRegion",16);
    private GlassRenderUniforms() { }
    static void bindRegion(RenderPass pass) {
        var main=MinecraftClient.getInstance().getFramebuffer();
        var data=BufferUtils.createByteBuffer(16);
        IndexedGuiBatch.vec4(data,0,0,0,Math.max(1,main.textureWidth),Math.max(1,main.textureHeight));
        if(!REGION.write(data)) throw new IllegalStateException("Glass BlurRegion upload failed");
        pass.setUniform("BlurRegion",REGION.buffer());
    }
    static void close() { REGION.close(); }
}
