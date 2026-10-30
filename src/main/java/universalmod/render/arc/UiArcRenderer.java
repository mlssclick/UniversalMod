package universalmod.render.arc;

import net.minecraft.client.gui.DrawContext;
import universalmod.render.Render2DCoordinateSpace;
import universalmod.render.ScissorUtil;
import universalmod.render.gpu.*;
import org.lwjgl.BufferUtils;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class UiArcRenderer implements AutoCloseable {
    private static final int MAX_ARCS = 64;
    private static final int PARAMS_PER_ARC = 11;
    private static final UiArcRenderer INSTANCE = new UiArcRenderer();
    public static final RenderPipeline PIPELINE = new RenderPipeline("ui/arcs/arc_quad", "ui/arcs/arc",
            INSTANCE::beginGuiFrame, pass -> { INSTANCE.prepareBuffers(); INSTANCE.bindParams(pass); });
    private final List<UiArc> prepared = new ArrayList<>();
    private final GpuUniformBuffer paramsBuffer = new GpuUniformBuffer("UniversalMod UiArcParams", MAX_ARCS * PARAMS_PER_ARC * 16);
    private DrawContext graphics;
    private boolean dirty;

    private UiArcRenderer() { }
    public static UiArcRenderer getInstance() { return INSTANCE; }
    public static void closeInstance() { INSTANCE.close(); }
    public void beginFrame(DrawContext graphics) { this.graphics = graphics; }
    public void flush() { graphics = null; }
    public void beginGuiFrame() { prepared.clear(); dirty = false; }
    public void enqueue(UiArc arc) {
        if (arc == null || !arc.visible()) return;
        float size = Math.max(0, arc.size());
        UiArc normalized = new UiArc(arc.x(),arc.y(),size,Math.max(0,Math.min(arc.thickness(),size)),
                Math.max(0,Math.min(arc.degree(),360)),arc.rotation(),Math.min(arc.blurRadius(),size*.25f),arc.colors());
        GuiStateRenderer.getInstance().submitGuiElement(new UiArcState(Render2DCoordinateSpace.pose(graphics),normalized,ScissorUtil.current()));
    }
    int reserve(UiArc arc) {
        if (prepared.size() >= MAX_ARCS) return -1;
        int index=prepared.size(); prepared.add(arc); dirty=true; return index;
    }
    public boolean isPipeline(RenderPipeline pipeline) { return pipeline == PIPELINE; }
    public void bindParams(RenderPass pass) {
        if (!prepared.isEmpty() && !paramsBuffer.degraded()) pass.setUniform("UiArcParams",paramsBuffer.buffer());
    }
    public void prepareBuffers() {
        if (!dirty || prepared.isEmpty()) return;
        ByteBuffer data=BufferUtils.createByteBuffer(prepared.size()*PARAMS_PER_ARC*16);
        for(int i=0;i<prepared.size();i++) {
            UiArc arc=prepared.get(i); int base=i*PARAMS_PER_ARC*16;
            data.putFloat(base,arc.size()).putFloat(base+4,arc.thickness()).putFloat(base+8,arc.degree()).putFloat(base+12,arc.rotation());
            data.putFloat(base+16,arc.blurRadius());
            for(int c=0;c<9;c++) ColorPacker.putRgbaFloat(data,base+(c+2)*16,arc.colors()[c]);
        }
        dirty=!paramsBuffer.write(data);
        if(dirty) throw new IllegalStateException("UiArcParams upload failed");
    }
    @Override public void close() { prepared.clear(); graphics=null; paramsBuffer.close(); PIPELINE.close(); }
}
