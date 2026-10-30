package universalmod.render.media;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import universalmod.render.ClientPipelines;

public final class MediaCoverShadowRenderer {
    private static final Matrix4f IDENTITY = new Matrix4f();
    private MediaCoverShadowRenderer() {}

    public static boolean render(float sw,float sh,float x,float y,float side,
                                 float radius,float yOffset,float sigma,float alpha) {
        if(sw<=0 || sh<=0 || side<=0 || alpha<=.0001f) return false;
        ShaderProgram prior=RenderSystem.getShader();
        ShaderProgram shader;
        try { shader=ClientPipelines.bind(ClientPipelines.MEDIA_SHADOW,"ui_media_shadow"); }
        catch (Throwable ex) {return false;}
        if(shader==null) return false;
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend=GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthMask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        try {
            float pad=Math.max(1f,sigma*3f);
            float bx=x-pad,by=y+yOffset-pad;
            float boxSize=side+2*pad;
            shader.getUniform("BoxBounds").set(bx/sw,by/sh,boxSize/sw,boxSize/sh);
            shader.getUniform("Size").set(boxSize,boxSize);
            shader.getUniform("ShapeSize").set(side,side);
            shader.getUniform("Radius").set(radius,radius,radius,radius);
            shader.getUniform("Padding").set(pad);
            shader.getUniform("Sigma").set(sigma);
            shader.getUniform("Spread").set(0f);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1f,1f,1f,1f);
            float x1=bx/sw*2f-1f,x2=(bx+boxSize)/sw*2f-1f;
            float y1=1f-by/sh*2f,y2=1f-(by+boxSize)/sh*2f;
            int c=(Math.max(0,Math.min(255,Math.round(alpha*255f)))<<24);
            BufferBuilder builder=Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);
            builder.vertex(IDENTITY,x1,y1,0f).color(c);
            builder.vertex(IDENTITY,x1,y2,0f).color(c);
            builder.vertex(IDENTITY,x2,y2,0f).color(c);
            builder.vertex(IDENTITY,x2,y1,0f).color(c);
            BufferRenderer.drawWithGlobalProgram(builder.end());
            return true;
        } catch(Throwable ex) {
            return false;
        } finally {
            RenderSystem.depthMask(depthMask);
            if(depth) RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
            if(cull) RenderSystem.enableCull();else RenderSystem.disableCull();
            if(blend) RenderSystem.enableBlend();else RenderSystem.disableBlend();
            if(prior!=null) RenderSystem.setShader(prior);else RenderSystem.clearShader();
        }
    }
}
