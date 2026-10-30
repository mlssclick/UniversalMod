package universalmod.render.atmosphere;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL33;
import universalmod.module.render.Atmosphere;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public final class AtmosphereRainRenderer {
    private static final String ROOT="/assets/universalmod/shaders/";
    private static final int UNIFORM_BYTES=432, DROP_COUNT=13*11*13*2;
    private static final RainHeightMap HEIGHT_MAP=new RainHeightMap();
    private static int compositeProgram,particleProgram,vao,ubo,sceneFbo,scene,depth;
    private static int width=-1,height=-1;
    private static long retryAfter,lastNanos;
    private static double animationTime;
    private AtmosphereRainRenderer() { }

    public static void render(Atmosphere module,Camera camera,Matrix4f view,Matrix4f projection,float tickDelta) {
        MinecraftClient client=MinecraftClient.getInstance();
        if(module==null || !module.isRainActive() || module.getRainAmount()<=0 || client.world==null || client.player==null
                || camera==null || !camera.isReady() || camera.getSubmersionType()!=CameraSubmersionType.NONE
                || view==null || projection==null
                || System.currentTimeMillis()<retryAfter) return;
        Framebuffer main=client.getFramebuffer();
        if(main==null || main.fbo<=0 || main.viewportWidth<1 || main.viewportHeight<1
                || main.getColorAttachment()<=0 || main.getDepthAttachment()<=0) return;

        AtmosphereMoodRenderer.GlState state=new AtmosphereMoodRenderer.GlState();
        try {
            initialize();ensure(main.viewportWidth,main.viewportHeight);
            Vec3d cam=camera.getPos();
            HEIGHT_MAP.update(client.world,cam);
            GL33.glBindFramebuffer(GL33.GL_READ_FRAMEBUFFER,main.fbo);
            GL33.glBindFramebuffer(GL33.GL_DRAW_FRAMEBUFFER,sceneFbo);
            GL33.glBlitFramebuffer(0,0,width,height,0,0,width,height,GL33.GL_COLOR_BUFFER_BIT,GL33.GL_NEAREST);
            GL33.glBindFramebuffer(GL33.GL_READ_FRAMEBUFFER,main.fbo);
            GL33.glActiveTexture(GL33.GL_TEXTURE0);
            GL33.glBindTexture(GL33.GL_TEXTURE_2D,depth);
            GL33.glCopyTexSubImage2D(GL33.GL_TEXTURE_2D,0,0,0,0,0,width,height);
            updateTime(client.isPaused());
            upload(module,camera,cam,view,projection,tickDelta);
            GL33.glBindFramebuffer(GL33.GL_DRAW_FRAMEBUFFER,main.fbo);
            GL33.glViewport(0,0,width,height);
            GL33.glDisable(GL33.GL_DEPTH_TEST);GL33.glDisable(GL33.GL_CULL_FACE);
            GL33.glDisable(GL33.GL_STENCIL_TEST);GL33.glDisable(GL33.GL_SCISSOR_TEST);
            GL33.glDepthMask(false);GL33.glColorMask(true,true,true,true);
            GL33.glBindVertexArray(vao);
            GL33.glBindBufferBase(GL33.GL_UNIFORM_BUFFER,0,ubo);
            GL33.glActiveTexture(GL33.GL_TEXTURE0);GL33.glBindTexture(GL33.GL_TEXTURE_2D,depth);
            GL33.glActiveTexture(GL33.GL_TEXTURE1);GL33.glBindTexture(GL33.GL_TEXTURE_2D,HEIGHT_MAP.texture());
            GL33.glDisable(GL33.GL_BLEND);
            GL33.glUseProgram(compositeProgram);
            GL33.glActiveTexture(GL33.GL_TEXTURE2);GL33.glBindTexture(GL33.GL_TEXTURE_2D,scene);
            GL33.glDrawArrays(GL33.GL_TRIANGLES,0,6);

            GL33.glEnable(GL33.GL_BLEND);
            GL33.glBlendFuncSeparate(GL33.GL_SRC_ALPHA,GL33.GL_ONE_MINUS_SRC_ALPHA,GL33.GL_ONE,GL33.GL_ONE_MINUS_SRC_ALPHA);
            GL33.glUseProgram(particleProgram);
            GL33.glDrawArrays(GL33.GL_TRIANGLES,0,DROP_COUNT*6);
        } catch (RuntimeException | LinkageError failure) {
            System.err.println("[UniversalMod] Rain render failed; retry in 5s: " + failure);
            release();retryAfter=System.currentTimeMillis()+5000;
        } finally { state.restore(); }
    }
    private static void updateTime(boolean paused) {
        long now=System.nanoTime();
        if(!paused && lastNanos!=0) animationTime+=Math.min(0.1,Math.max(0.0,(now-lastNanos)*1e-9));
        lastNanos=now;
    }
    private static String read(String path) {
        try(InputStream stream=AtmosphereRainRenderer.class.getResourceAsStream(ROOT+path)) {
            if(stream==null)throw new IllegalStateException("Rain GLSL missing: "+path);
            return new String(stream.readAllBytes(),StandardCharsets.UTF_8);
        } catch(IOException e){throw new IllegalStateException(e);}
    }
    private static int compile(int type,String src) {
        int sh=GL33.glCreateShader(type);
        GL33.glShaderSource(sh,src);GL33.glCompileShader(sh);
        if(GL33.glGetShaderi(sh,GL33.GL_COMPILE_STATUS)==GL33.GL_FALSE) {
            String log=GL33.glGetShaderInfoLog(sh);GL33.glDeleteShader(sh);
            throw new IllegalStateException("Rain GLSL: "+log);
        }
        return sh;
    }
    private static int program(String vertex,String fragment) {

        String common=read("include/rain_common.glsl");
        vertex=vertex.replace("#moj_import <universalmod:rain_common.glsl>",common);
        fragment=fragment.replace("#moj_import <universalmod:rain_common.glsl>",common);
        int v=compile(GL33.GL_VERTEX_SHADER,vertex),f=compile(GL33.GL_FRAGMENT_SHADER,fragment);
        int p=GL33.glCreateProgram();
        try {
            GL33.glAttachShader(p,v);GL33.glAttachShader(p,f);GL33.glLinkProgram(p);
            if(GL33.glGetProgrami(p,GL33.GL_LINK_STATUS)==GL33.GL_FALSE)
                throw new IllegalStateException("Rain link: "+GL33.glGetProgramInfoLog(p));
            GL33.glUseProgram(p);
            int index=GL33.glGetUniformBlockIndex(p,"RainData");
            if(index<0 || index==GL33.GL_INVALID_INDEX)throw new IllegalStateException("RainData UBO missing");
            GL33.glUniformBlockBinding(p,index,0);
            setSampler(p,"DepthSampler",0);setSampler(p,"HeightSampler",1);setSampler(p,"Sampler0",2);
            return p;
        } catch(RuntimeException e){GL33.glDeleteProgram(p);throw e;}
        finally{GL33.glDeleteShader(v);GL33.glDeleteShader(f);}
    }
    private static void setSampler(int program,String name,int unit) {
        int location=GL33.glGetUniformLocation(program,name);
        if(location>=0)GL33.glUniform1i(location,unit);
    }
    private static void initialize() {
        if(compositeProgram!=0)return;
        compositeProgram=program(read("post/winter/winter.vsh"),read("post/atmosphere/rain/composite.fsh"));
        particleProgram=program(read("post/atmosphere/rain/particles.vsh"),read("post/atmosphere/rain/particles.fsh"));
        vao=GL33.glGenVertexArrays();
        ubo=GL33.glGenBuffers();GL33.glBindBuffer(GL33.GL_UNIFORM_BUFFER,ubo);
        GL33.glBufferData(GL33.GL_UNIFORM_BUFFER,UNIFORM_BYTES,GL33.GL_DYNAMIC_DRAW);
    }
    private static void ensure(int w,int h) {
        if(w==width && h==height && sceneFbo!=0)return;
        releaseTargets();
        scene=GL33.glGenTextures();GL33.glBindTexture(GL33.GL_TEXTURE_2D,scene);
        GL33.glTexImage2D(GL33.GL_TEXTURE_2D,0,GL33.GL_RGBA8,w,h,0,GL33.GL_RGBA,GL33.GL_UNSIGNED_BYTE,(ByteBuffer)null);
        GL33.glTexParameteri(GL33.GL_TEXTURE_2D,GL33.GL_TEXTURE_MIN_FILTER,GL33.GL_LINEAR);
        GL33.glTexParameteri(GL33.GL_TEXTURE_2D,GL33.GL_TEXTURE_MAG_FILTER,GL33.GL_LINEAR);
        GL33.glTexParameteri(GL33.GL_TEXTURE_2D,GL33.GL_TEXTURE_WRAP_S,GL33.GL_CLAMP_TO_EDGE);
        GL33.glTexParameteri(GL33.GL_TEXTURE_2D,GL33.GL_TEXTURE_WRAP_T,GL33.GL_CLAMP_TO_EDGE);
        depth=GL33.glGenTextures();GL33.glBindTexture(GL33.GL_TEXTURE_2D,depth);
        GL33.glTexImage2D(GL33.GL_TEXTURE_2D,0,GL33.GL_DEPTH_COMPONENT24,w,h,0,GL33.GL_DEPTH_COMPONENT,GL33.GL_UNSIGNED_INT,(ByteBuffer)null);
        GL33.glTexParameteri(GL33.GL_TEXTURE_2D,GL33.GL_TEXTURE_MIN_FILTER,GL33.GL_NEAREST);
        GL33.glTexParameteri(GL33.GL_TEXTURE_2D,GL33.GL_TEXTURE_MAG_FILTER,GL33.GL_NEAREST);
        GL33.glTexParameteri(GL33.GL_TEXTURE_2D,GL33.GL_TEXTURE_WRAP_S,GL33.GL_CLAMP_TO_EDGE);
        GL33.glTexParameteri(GL33.GL_TEXTURE_2D,GL33.GL_TEXTURE_WRAP_T,GL33.GL_CLAMP_TO_EDGE);
        sceneFbo=GL33.glGenFramebuffers();GL33.glBindFramebuffer(GL33.GL_FRAMEBUFFER,sceneFbo);
        GL33.glFramebufferTexture2D(GL33.GL_FRAMEBUFFER,GL33.GL_COLOR_ATTACHMENT0,GL33.GL_TEXTURE_2D,scene,0);
        if(GL33.glCheckFramebufferStatus(GL33.GL_FRAMEBUFFER)!=GL33.GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("Rain scene FBO incomplete");
        width=w;height=h;
    }
    private static void upload(Atmosphere settings,Camera camera,Vec3d cam,Matrix4f view,Matrix4f projection,float partialTick) {
        AtmosphereSunProjection.Sun sun=AtmosphereSunProjection.sample(camera,partialTick,view,projection);
        int sky=MinecraftClient.getInstance().world.getSkyColor(cam,partialTick);
        float daylight=MathHelper.clamp((sun.y()+.15f)/.55f,0f,1f);
        daylight=daylight*daylight*(3f-2f*daylight);
        ByteBuffer b=BufferUtils.createByteBuffer(UNIFORM_BYTES).order(ByteOrder.nativeOrder());
        projection.get(0,b);new Matrix4f(projection).invert().get(64,b);
        view.get(128,b);new Matrix4f(view).invert().get(192,b);
        new Matrix4f(projection).mul(view).invert().get(256,b);
        vec4(b,320,(float)cam.x,(float)cam.y,(float)cam.z,0f);
        vec4(b,336,((sky>>16)&255)/255f,((sky>>8)&255)/255f,(sky&255)/255f,height*Math.abs(projection.m11())*.5f);
        vec4(b,352,sun.x(),sun.y(),sun.z(),daylight);
        vec4(b,368,(float)animationTime,settings.getRainAmount(),WeatherWorldRenderer.flash(partialTick),0f);
        vec4(b,384,settings.getRainPuddles(),settings.getRainRipples(),0f,0f);
        vec4(b,400,(float)(HEIGHT_MAP.originX()-cam.x),(float)(HEIGHT_MAP.originZ()-cam.z),RainHeightMap.SIZE,
                MinecraftClient.getInstance().world.getBottomY());
        vec4(b,416,width,height,sun.reversedDepth(),0f);
        b.position(0);GL33.glBindBuffer(GL33.GL_UNIFORM_BUFFER,ubo);
        GL33.glBufferSubData(GL33.GL_UNIFORM_BUFFER,0,b);
    }
    private static void vec4(ByteBuffer b,int offset,float x,float y,float z,float w) {
        b.putFloat(offset,x).putFloat(offset+4,y).putFloat(offset+8,z).putFloat(offset+12,w);
    }
    public static void reset(){release();animationTime=0;lastNanos=0;retryAfter=0;}
    private static void releaseTargets() {
        if(sceneFbo!=0)GL33.glDeleteFramebuffers(sceneFbo);
        if(scene!=0)GL33.glDeleteTextures(scene);
        if(depth!=0)GL33.glDeleteTextures(depth);
        sceneFbo=scene=depth=0;width=height=-1;
    }
    private static void release() {
        releaseTargets();HEIGHT_MAP.close();
        if(ubo!=0)GL33.glDeleteBuffers(ubo);
        if(vao!=0)GL33.glDeleteVertexArrays(vao);
        if(compositeProgram!=0)GL33.glDeleteProgram(compositeProgram);
        if(particleProgram!=0)GL33.glDeleteProgram(particleProgram);
        ubo=vao=compositeProgram=particleProgram=0;
    }
}
