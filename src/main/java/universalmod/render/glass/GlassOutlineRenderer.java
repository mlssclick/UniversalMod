package universalmod.render.glass;

import net.minecraft.client.gui.DrawContext;
import java.nio.ByteBuffer;
import universalmod.render.Render2DCoordinateSpace;
import universalmod.render.ScissorUtil;
import universalmod.render.blur.BlurFramebuffer;
import universalmod.render.gpu.*;

public final class GlassOutlineRenderer implements AutoCloseable {
    private static final IndexedGuiBatch<GlassOutline> BATCH = new IndexedGuiBatch<GlassOutline>("ui/outline_glass/outline_glass","GlassOutlineParamsArray",6,GlassOutlineRenderer::encode).withBindings(GlassRenderUniforms::bindRegion);
    public static final RenderPipeline GLASS_OUTLINE_PIPELINE = BATCH.pipeline();
    private static final GlassOutlineRenderer INSTANCE = new GlassOutlineRenderer();
    private DrawContext graphics;
    private GlassOutlineRenderer() { }
    public static GlassOutlineRenderer getInstance() { return INSTANCE; }
    public static void closeInstance() { INSTANCE.close(); }
    public void beginFrame(DrawContext graphics) { this.graphics=graphics; }
    public void flush() { graphics=null; }
    public void barrier() { }
    public void beginGuiFrame() { BATCH.begin(); }
    public int reserve(GlassOutline value) { return BATCH.reserve(value); }
    public void prepareBuffers() { BATCH.prepare(); }
    public void bindParams(RenderPass pass) { BATCH.bind(pass); }
    public boolean isGlassOutlinePipeline(RenderPipeline pipeline) { return pipeline == GLASS_OUTLINE_PIPELINE; }
    public void enqueue(GlassOutline value) { draw(graphics,value); }
    public void enqueue(BuiltGlassOutline value) { draw(graphics,value); }
    public void draw(DrawContext graphics,BuiltGlassOutline v) {
        if(v!=null) draw(graphics,new GlassOutline(v.x(), v.y(), v.width(), v.height(), v.radiusTopLeft(), v.radiusTopRight(), v.radiusBottomRight(), v.radiusBottomLeft(), v.thickness(), v.color(), v.globalAlpha(), v.fresnelPower(), v.fresnelColor(), v.baseAlpha(), v.fresnelInvert(), v.fresnelMix(), v.distortStrength(), v.squirt(), v.smoothness(), v.z(), v.shinePhase()));
    }
    public void draw(DrawContext graphics,GlassOutline value) {
        if(value==null || !value.visible()) return;
        if(BlurFramebuffer.getInstance().prepareGlassTexture(30,value.width(),value.height())==0) return;
        GuiStateRenderer.getInstance().submitGuiElement(new GlassOutlineState(Render2DCoordinateSpace.pose(graphics),value,ScissorUtil.current()));
    }
    private static void encode(ByteBuffer data,int base,GlassOutline v) {
        float w=v.width(),h=v.height();
        IndexedGuiBatch.vec4(data,base,IndexedGuiBatch.radius(v.radiusTopLeft(),w,h),IndexedGuiBatch.radius(v.radiusTopRight(),w,h),IndexedGuiBatch.radius(v.radiusBottomRight(),w,h),IndexedGuiBatch.radius(v.radiusBottomLeft(),w,h));
        IndexedGuiBatch.vec4(data,base+16,v.width(),v.height(),Math.max(0,v.thickness()),Math.max(.001f,v.smoothness()));
        IndexedGuiBatch.vec4(data,base+32,clamp(v.globalAlpha()),Math.max(.001f,v.fresnelPower()),clamp(v.baseAlpha()),clamp(v.fresnelMix()));
        IndexedGuiBatch.color(data,base+48,v.fresnelColor());
        IndexedGuiBatch.vec4(data,base+64,v.fresnelInvert()?1:0,v.distortStrength(),v.z(),v.shinePhase());
        IndexedGuiBatch.color(data,base+80,v.color());
    }
    private static float clamp(float value) { return Math.max(0,Math.min(1,value)); }
    @Override public void close() { graphics=null; BATCH.close(); GlassRenderUniforms.close(); }
}
