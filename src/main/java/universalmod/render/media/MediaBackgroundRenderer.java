package universalmod.render.media;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;
import java.nio.ByteBuffer;
import java.util.OptionalInt;

import universalmod.service.media.ArtworkPalette;

public final class MediaBackgroundRenderer {
    private static final String NAME="background",UNIFORM="MediaBackgroundData";
    private static final int BYTES=96;
    private MediaBackgroundRenderer() {}
    public static boolean render(float width,float height,ArtworkPalette colors,float energy,float phase,float opacity) {
        if(width<=0||height<=0||colors==null||opacity<=0||System.currentTimeMillis()<retryAt)return false;
        try(MemoryStack stack=MemoryStack.stackPush()) {
            ByteBuffer b=stack.calloc(BYTES);
            vec4(b,0,width,height,phase,Math.max(0,Math.min(1,opacity)));
            rgb(b,1,colors.primary());rgb(b,2,colors.secondary());rgb(b,3,colors.highlight());rgb(b,4,colors.fourth());
            vec4(b,5,Math.max(0,Math.min(1,energy)),0,0,0);
            return draw(b);
        }
    }
    private static void rgb(ByteBuffer b,int i,int c) {
        vec4(b,i,((c>>>16)&255)/255f,((c>>>8)&255)/255f,(c&255)/255f,1f);
    }
    public static void reload(){retryAt=0;}
    private static RenderPipeline pipeline;
    private static GpuBuffer uniformBuffer;
    private static long retryAt;
    private static void ensure() {
        if (pipeline == null) pipeline = RenderPipelines.register(RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("universalmod", "pipeline/post/media/" + NAME))
            .withVertexShader(Identifier.fromNamespaceAndPath("universalmod", "post/winter/winter"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("universalmod", "post/media/" + NAME))
            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withCull(false)
            .withBlend(BlendFunction.TRANSLUCENT).withUniform(UNIFORM, UniformType.UNIFORM_BUFFER)
            .build());
        if (uniformBuffer == null || uniformBuffer.isClosed())
            uniformBuffer = RenderSystem.getDevice().createBuffer(() -> "universalmod:media_"+NAME,
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, BYTES);
    }
    private static boolean draw(ByteBuffer data) {
        try {
            ensure();
            Minecraft client = Minecraft.getInstance();
            if (client == null || client.getMainRenderTarget() == null ||
                client.getMainRenderTarget().getColorTextureView() == null) return false;
            CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
            enc.writeToBuffer(uniformBuffer.slice(0,BYTES), data);
            try (RenderPass pass = enc.createRenderPass(() -> "universalmod:media_"+NAME,
                    client.getMainRenderTarget().getColorTextureView(), OptionalInt.empty())) {
                pass.setPipeline(pipeline);
                pass.setUniform(UNIFORM, uniformBuffer);
                pass.draw(0,6);
            }
            return true;
        } catch (RuntimeException | LinkageError failed) {
            retryAt = System.currentTimeMillis() + 2500L;
            return false;
        }
    }
    private static void vec4(ByteBuffer b,int index,float x,float y,float z,float w) {
        int offset=index*16;
        b.putFloat(offset,x).putFloat(offset+4,y).putFloat(offset+8,z).putFloat(offset+12,w);
    }
}
