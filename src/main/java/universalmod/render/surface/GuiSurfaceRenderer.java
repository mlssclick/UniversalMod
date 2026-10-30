package universalmod.render.surface;

import net.minecraft.client.gui.DrawContext;
import java.nio.ByteBuffer;
import universalmod.render.Render2DCoordinateSpace;
import universalmod.render.ScissorUtil;
import universalmod.render.gpu.*;

public final class GuiSurfaceRenderer implements AutoCloseable {
    private static final IndexedGuiBatch<GuiSurface> BATCH = new IndexedGuiBatch<>("ui/gui_surface/gui_surface","GuiSurfaceParams",4,GuiSurfaceRenderer::encode);
    public static final RenderPipeline PIPELINE = BATCH.pipeline();
    private static final GuiSurfaceRenderer INSTANCE = new GuiSurfaceRenderer();
    private DrawContext graphics;
    private GuiSurfaceRenderer() { }
    public static GuiSurfaceRenderer getInstance() { return INSTANCE; }
    public static void closeInstance() { INSTANCE.close(); }
    public void beginFrame(DrawContext graphics) { this.graphics=graphics; }
    public void flush() { graphics=null; }
    public void beginGuiFrame() { BATCH.begin(); }
    public int reserve(GuiSurface value) { return BATCH.reserve(value); }
    public void prepareBuffers() { BATCH.prepare(); }
    public void bindParams(RenderPass pass) { BATCH.bind(pass); }
    public boolean isPipeline(RenderPipeline pipeline) { return pipeline == PIPELINE; }
    public void enqueue(GuiSurface value) { draw(graphics,value); }
    public void draw(DrawContext graphics,GuiSurface value) {
        if(value==null || !value.visible()) return;
        GuiStateRenderer.getInstance().submitGuiElement(new GuiSurfaceState(Render2DCoordinateSpace.pose(graphics),value,ScissorUtil.current()));
    }
    private static void encode(ByteBuffer data,int base,GuiSurface v) {
        radii(data,base,v.width(),v.height(),v.radiusTopLeft(),v.radiusTopRight(),v.radiusBottomRight(),v.radiusBottomLeft());
        IndexedGuiBatch.vec4(data,base+16,v.width(),v.height(),clamp(v.alpha()),Math.max(.45f,v.smoothness()));
        IndexedGuiBatch.color(data,base+32,v.baseColor()); data.putFloat(base+44,clamp(v.accentStrength()));
        IndexedGuiBatch.color(data,base+48,v.accentColor());
    }
    private static void radii(ByteBuffer data,int base,float w,float h,float tl,float tr,float br,float bl) {
        IndexedGuiBatch.vec4(data,base,IndexedGuiBatch.radius(tl,w,h),IndexedGuiBatch.radius(tr,w,h),IndexedGuiBatch.radius(br,w,h),IndexedGuiBatch.radius(bl,w,h));
    }
    private static float clamp(float value) { return Math.max(0,Math.min(1,value)); }
    @Override public void close() { graphics=null; BATCH.close(); }
}
