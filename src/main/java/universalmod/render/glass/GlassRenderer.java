package universalmod.render.glass;

import net.minecraft.client.gui.DrawContext;
import java.nio.ByteBuffer;
import universalmod.render.Render2DCoordinateSpace;
import universalmod.render.ScissorUtil;
import universalmod.render.blur.BlurFramebuffer;
import universalmod.render.gpu.*;

public final class GlassRenderer implements AutoCloseable {
    private static final IndexedGuiBatch<GlassQuad> BATCH = new IndexedGuiBatch<GlassQuad>("ui/glass/glass","GlassParamsArray",5,GlassRenderer::encode).withBindings(GlassRenderUniforms::bindRegion);
    public static final RenderPipeline GLASS_PIPELINE = BATCH.pipeline();
    private static final GlassRenderer INSTANCE = new GlassRenderer();
    private DrawContext graphics;
    private GlassRenderer() { }
    public static GlassRenderer getInstance() { return INSTANCE; }
    public static void closeInstance() { INSTANCE.close(); }
    public void beginFrame(DrawContext graphics) { this.graphics=graphics; }
    public void flush() { graphics=null; }
    public void barrier() { }
    public void beginGuiFrame() { BATCH.begin(); }
    public int reserve(GlassQuad value) { return BATCH.reserve(value); }
    public void prepareBuffers() { BATCH.prepare(); }
    public void bindParams(RenderPass pass) { BATCH.bind(pass); }
    public boolean isGlassPipeline(RenderPipeline pipeline) { return pipeline == GLASS_PIPELINE; }
    public void enqueue(GlassQuad value) { draw(graphics,value); }
    public void enqueue(BuiltGlass value) { draw(graphics,value); }
    public void draw(DrawContext graphics,BuiltGlass v) {
        if(v!=null) draw(graphics,new GlassQuad(v.x(), v.y(), v.width(), v.height(), v.radiusTopLeft(), v.radiusTopRight(), v.radiusBottomRight(), v.radiusBottomLeft(), v.color(), v.globalAlpha(), v.fresnelPower(), v.fresnelColor(), v.baseAlpha(), v.fresnelInvert(), v.fresnelMix(), v.distortStrength(), v.squirt(), v.z()));
    }
    public void draw(DrawContext graphics,GlassQuad value) {
        if(value==null || !value.visible()) return;
        if(BlurFramebuffer.getInstance().prepareGlassTexture(30,value.width(),value.height())==0) return;
        GuiStateRenderer.getInstance().submitGuiElement(new GlassState(Render2DCoordinateSpace.pose(graphics),value,ScissorUtil.current()));
    }
    private static void encode(ByteBuffer data,int base,GlassQuad v) {
        float w=v.width(),h=v.height();
        IndexedGuiBatch.vec4(data,base,IndexedGuiBatch.radius(v.radiusTopLeft(),w,h),IndexedGuiBatch.radius(v.radiusTopRight(),w,h),IndexedGuiBatch.radius(v.radiusBottomRight(),w,h),IndexedGuiBatch.radius(v.radiusBottomLeft(),w,h));
        IndexedGuiBatch.vec4(data,base+16,v.width(),v.height(),.5f,Math.max(.001f,v.squirt()));
        IndexedGuiBatch.vec4(data,base+32,clamp(v.globalAlpha()),Math.max(.001f,v.fresnelPower()),clamp(v.baseAlpha()),clamp(v.fresnelMix()));
        IndexedGuiBatch.color(data,base+48,v.fresnelColor());
        IndexedGuiBatch.vec4(data,base+64,v.fresnelInvert()?1:0,v.distortStrength(),v.z(),0);
    }
    private static float clamp(float value) { return Math.max(0,Math.min(1,value)); }
    @Override public void close() { graphics=null; BATCH.close(); GlassRenderUniforms.close(); }
}
