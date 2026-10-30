package universalmod.render.fireglow;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.joml.Matrix3x2f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import universalmod.render.gpu.*;

public final class FireGlowFramebuffer {
    private static SimpleFramebuffer mask;
    private static PostEffectGraph fireGraph,entityGraph;
    private static boolean maskWritten;
    private static final RenderPipeline COMPOSITE=new RenderPipeline("post/legacy_fullscreen","post/legacy_fire_composite",()->{},pass->pass.sampler("InSampler",0));
    private FireGlowFramebuffer() { }

    public static void beginFrame() {
        maskWritten=false;
        Framebuffer target=getFramebuffer();
        if(target!=null)clear(target);
        var world=MinecraftClient.getInstance().worldRenderer;
        if(world!=null && world.getEntityOutlinesFramebuffer()!=null)clear(world.getEntityOutlinesFramebuffer());
    }
    public static void markMaskWritten() { maskWritten=true; }
    public static Framebuffer getFramebuffer() {
        var client=MinecraftClient.getInstance();
        if(client.getWindow()==null)return null;
        int w=client.getWindow().getWidth(),h=client.getWindow().getHeight();
        if(w<=0 || h<=0)return null;
        mask=ensure(mask,w,h);return mask;
    }
    private static SimpleFramebuffer ensure(SimpleFramebuffer target,int w,int h) {
        if(target==null)return new SimpleFramebuffer(w,h,false);
        if(target.textureWidth!=w || target.textureHeight!=h)target.resize(w,h);
        return target;
    }
    public static void withMaskTarget(Runnable render) {
        GuiStateRenderer.getInstance().flush();
        Framebuffer target=getFramebuffer();
        if(target==null)return;
        SavedTarget saved=new SavedTarget();
        try { target.beginWrite(true);render.run(); } finally { saved.restore(); }
    }
    private static void clear(Framebuffer target) {
        SavedTarget saved=new SavedTarget();
        var color=BufferUtils.createFloatBuffer(4);GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE,color);
        try { target.setClearColor(0,0,0,0);target.clear(); }
        finally { GL11.glClearColor(color.get(0),color.get(1),color.get(2),color.get(3));saved.restore(); }
    }
    public static void composite() {
        var client=MinecraftClient.getInstance();
        Framebuffer main=client.getFramebuffer();
        if(client.player==null || client.world==null || main==null)return;
        GuiStateRenderer.getInstance().flush();
        int w=main.textureWidth,h=main.textureHeight;
        SavedTarget saved=new SavedTarget();boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        try {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            if(client.worldRenderer!=null) {
                Framebuffer entity=client.worldRenderer.getEntityOutlinesFramebuffer();
                if(entity!=null && entity.getColorAttachment()>0) {
                    if(entityGraph==null)entityGraph=PostEffectGraph.load("legacy_entity_outline");
                    pass(COMPOSITE,entityGraph.process(entity),main);
                }
            }
            if(maskWritten && mask!=null) {
                if(fireGraph==null)fireGraph=PostEffectGraph.load("legacy_fire_outline");
                pass(COMPOSITE,fireGraph.process(mask),main);
            }
        } finally { if(scissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);else GL11.glDisable(GL11.GL_SCISSOR_TEST);saved.restore();maskWritten=false; }
    }
    private static void pass(RenderPipeline pipeline,Framebuffer source,Framebuffer target) {
        target.beginWrite(true);
        GuiStateRenderer.getInstance().submitGuiElement(new PostState(pipeline,source.getColorAttachment()));
    }
    private record PostState(RenderPipeline pipeline,int texture) implements GuiElementRenderState {
        @Override public void buildVertices(GuiVertexConsumer vertices) {
            var pose=new Matrix3x2f();
            vertices.addVertexWith2DPose(pose,-1,-1).setUv(0,0);
            vertices.addVertexWith2DPose(pose,-1,1).setUv(0,1);
            vertices.addVertexWith2DPose(pose,1,1).setUv(1,1);
            vertices.addVertexWith2DPose(pose,1,-1).setUv(1,0);
        }
        @Override public TextureSetup textureSetup() { return TextureSetup.single(()->texture,false); }
        @Override public ScreenRectangle scissorArea() { return null; }
        @Override public ScreenRectangle bounds() { return new ScreenRectangle(0,0,1,1); }
    }
    private static final class SavedTarget {
        final int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        final java.nio.IntBuffer viewport=BufferUtils.createIntBuffer(4);
        SavedTarget() { GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport); }
        void restore() { GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GL11.glViewport(viewport.get(0),viewport.get(1),viewport.get(2),viewport.get(3)); }
    }
    public static void close() {
        if(mask!=null)mask.delete();mask=null;maskWritten=false;
        if(fireGraph!=null)fireGraph.close();if(entityGraph!=null)entityGraph.close();fireGraph=null;entityGraph=null;
        COMPOSITE.close();FireGlowRenderLayer.close();
    }
}
