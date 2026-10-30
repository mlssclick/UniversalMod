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

public final class MediaCoverShadowRenderer {
    private static final String NAME="shadow",UNIFORM="MediaShadowData";
    private static final int BYTES=64;
    private MediaCoverShadowRenderer() {}
    public static boolean render(float sw,float sh,float x,float y,float side,float radius,float yOffset,float sigma,float alpha){
        if(sw<=0||sh<=0||side<=0||alpha<=0||System.currentTimeMillis()<retryAt)return false;
        try(MemoryStack stack=MemoryStack.stackPush()){
            ByteBuffer b=stack.calloc(BYTES);
            vec4(b,0,sw,sh,x,y+yOffset);
            vec4(b,1,side,radius,Math.max(.5f,sigma),Math.max(0,Math.min(1,alpha)));
            vec4(b,2,0,0,0,0);
            vec4(b,3,0,0,0,0);
            return draw(b);
        }
    }
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
