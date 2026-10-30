package universalmod.render.hud;

import net.minecraft.client.gui.DrawContext;
import java.nio.ByteBuffer;
import universalmod.render.Render2DCoordinateSpace;
import universalmod.render.ScissorUtil;
import universalmod.render.gpu.*;

public final class HudSurfaceRenderer implements AutoCloseable {

    private static final int MAX = 512;
    private static final IndexedGuiBatch<HudSurface> BATCH = new IndexedGuiBatch<>(
            "ui/hud_surface/hud_surface", "HudSurfaceParams", 3, MAX, HudSurfaceRenderer::encode);
    public static final RenderPipeline PIPELINE = BATCH.pipeline();
    private static final HudSurfaceRenderer INSTANCE = new HudSurfaceRenderer();
    private DrawContext graphics;
    private HudSurfaceRenderer() { }
    public static HudSurfaceRenderer getInstance() { return INSTANCE; }
    public static void closeInstance() { INSTANCE.close(); }
    public void beginFrame(DrawContext graphics) { this.graphics=graphics; }
    public void flush() { graphics=null; }
    public void beginGuiFrame() { BATCH.begin(); }
    public int reserve(HudSurface value) { return BATCH.reserve(value); }
    public void prepareBuffers() { BATCH.prepare(); }
    public void bindParams(RenderPass pass) { BATCH.bind(pass); }
    public boolean isPipeline(RenderPipeline pipeline) { return pipeline == PIPELINE; }
    public void enqueue(HudSurface value) { draw(graphics,value); }
    public void draw(DrawContext graphics,HudSurface value) {
        if(value==null || !value.visible()) return;
        GuiStateRenderer.getInstance().submitGuiElement(new HudSurfaceState(Render2DCoordinateSpace.pose(graphics),value,ScissorUtil.current()));
    }
    private static void encode(ByteBuffer data,int base,HudSurface v) {
        radii(data,base,v.width(),v.height(),v.radiusTopLeft(),v.radiusTopRight(),v.radiusBottomRight(),v.radiusBottomLeft());
        IndexedGuiBatch.vec4(data,base+16,v.width(),v.height(),clamp(v.alpha()),Math.max(.45f,v.smoothness()));
        data.putFloat(base+32,clamp(v.darkness()));
    }
    private static void radii(ByteBuffer data,int base,float w,float h,float tl,float tr,float br,float bl) {
        IndexedGuiBatch.vec4(data,base,IndexedGuiBatch.radius(tl,w,h),IndexedGuiBatch.radius(tr,w,h),IndexedGuiBatch.radius(br,w,h),IndexedGuiBatch.radius(bl,w,h));
    }
    private static float clamp(float value) { return Math.max(0,Math.min(1,value)); }
    @Override public void close() { graphics=null; BATCH.close(); }
}
