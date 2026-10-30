package universalmod.render.image;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import java.nio.ByteBuffer;
import universalmod.render.Render2DCoordinateSpace;
import universalmod.render.ScissorUtil;
import universalmod.render.gpu.*;

public final class ImageRenderer implements AutoCloseable {
    private static final IndexedGuiBatch<ImageQuad> BATCH = new IndexedGuiBatch<>("ui/image/image","ImageParamsArray",3,ImageRenderer::encode);
    public static final RenderPipeline IMAGE_PIPELINE = BATCH.pipeline();
    private static final ImageRenderer INSTANCE = new ImageRenderer();
    private DrawContext graphics;
    private ImageRenderer() { }
    public static ImageRenderer getInstance() { return INSTANCE; }
    public static void closeInstance() { INSTANCE.close(); }
    public void beginFrame(DrawContext graphics) { this.graphics=graphics; }
    public void beginGuiFrame() { BATCH.begin(); }
    public void barrier() { GuiStateRenderer.getInstance().flush(); }
    public void flush() { barrier(); graphics=null; }
    public int reserve(ImageQuad value) { return BATCH.reserve(value); }
    public void prepareBuffers() { BATCH.prepare(); }
    public void bindParams(RenderPass pass) { BATCH.bind(pass); }
    public boolean isImagePipeline(RenderPipeline pipeline) { return pipeline == IMAGE_PIPELINE; }
    public void enqueue(BuiltImage value) {
        ImageState state=state(graphics,value);
        if(state!=null) GuiStateRenderer.getInstance().queueGuiElement(state);
    }
    public void draw(DrawContext graphics,BuiltImage value) {
        ImageState state=state(graphics,value);
        if(state!=null) GuiStateRenderer.getInstance().submitGuiElement(state);
    }
    private static ImageState state(DrawContext graphics,BuiltImage v) {
        if(v==null || !v.visible()) return null;
        ImageState state=new ImageState(Render2DCoordinateSpace.pose(graphics),new ImageTexture(resolve(v.texture()),v.nearestFilter()),ScissorUtil.current());
        state.add(new ImageQuad(v.x(),v.y(),v.width(),v.height(),v.u0(),v.v0(),v.u1(),v.v1(),
                v.colorTopLeft(),v.colorTopRight(),v.colorBottomRight(),v.colorBottomLeft(),
                v.radius(),v.radius(),v.radius(),v.radius(),v.smoothness(),v.rotationDegrees(),v.rotationOriginX(),v.rotationOriginY()));
        return state;
    }
    private static void encode(ByteBuffer data,int base,ImageQuad v) {
        float w=v.width(),h=v.height();
        IndexedGuiBatch.vec4(data,base,IndexedGuiBatch.radius(v.radiusTopLeft(),w,h),IndexedGuiBatch.radius(v.radiusTopRight(),w,h),IndexedGuiBatch.radius(v.radiusBottomRight(),w,h),IndexedGuiBatch.radius(v.radiusBottomLeft(),w,h));
        IndexedGuiBatch.vec4(data,base+16,w,h,Math.max(.001f,v.smoothness()),0);
        IndexedGuiBatch.vec4(data,base+32,v.u0(),v.v0(),v.u1(),v.v1());
    }
    private static Identifier resolve(String texture) {
        if(texture==null || texture.isBlank()) return Identifier.of("universalmod","images/clickgui/avatar.png");
        if(texture.contains(":")) return Identifier.of(texture);
        return Identifier.of("universalmod",texture.startsWith("images/") || texture.startsWith("textures/") ? texture : "images/"+texture);
    }
    @Override public void close() { barrier(); graphics=null; BATCH.close(); }
}
