package universalmod.render.arc;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import universalmod.render.UiRender;
import universalmod.render.GlUiState;
import universalmod.render.UiShaders;
import java.util.ArrayList;
import java.util.List;

 
public final class UiArcRenderer {
    private static final UiArcRenderer INSTANCE = new UiArcRenderer();
    private final List<UiArc> queued = new ArrayList<>();
    private UiArcRenderer() {}
    public static UiArcRenderer getInstance() { return INSTANCE; }
    public void beginFrame(DrawContext graphics) { queued.clear(); }
    public void enqueue(UiArc arc) { if (arc != null && arc.visible()) queued.add(arc); }
    public void flush() {
        if (queued.isEmpty()) return;
        try (GlUiState ignored = GlUiState.capture()) {
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            ShaderProgram shader = UiShaders.bind(UiShaders.ARC, "ui_arc");
            if (shader == null) return;
            for (UiArc arc : queued) drawOne(shader, arc);
        } finally {
            queued.clear();
        }
    }
    private static void set(ShaderProgram shader, int index, float x, float y, float z, float w) {
        var u = shader.getUniform("ArcParams[" + index + "]");
        if (u != null) u.set(x, y, z, w);
    }
    private static void drawOne(ShaderProgram shader, UiArc arc) {
        set(shader,0,arc.size(),arc.thickness(),arc.degree(),arc.rotation());
        set(shader,1,arc.blurRadius(),0,0,0);
        int[] colors = arc.colors();
        for (int i=0;i<9;i++) {
            int c=colors[i];
            set(shader,i+2, ((c>>>16)&255)/255f,((c>>>8)&255)/255f,(c&255)/255f,((c>>>24)&255)/255f);
        }
        float halo=arc.blurRadius(), s=arc.size()+halo*2f;
        float x=arc.x()-halo, y=arc.y()-halo;
        BufferBuilder b=Tessellator.getInstance().getBuffer();
        b.begin(DrawMode.QUADS,VertexFormats.POSITION_TEXTURE_COLOR);
        b.vertex(UiRender.matrix(),x,y,0).texture(0.0f, 0.0f).color(0xFFFFFFFF).next();
        b.vertex(UiRender.matrix(),x,y+s,0).texture(0.0f, 1.0f).color(0xFFFFFFFF).next();
        b.vertex(UiRender.matrix(),x+s,y+s,0).texture(1.0f, 1.0f).color(0xFFFFFFFF).next();
        b.vertex(UiRender.matrix(),x+s,y,0).texture(1.0f, 0.0f).color(0xFFFFFFFF).next();
        BufferRenderer.drawWithGlobalProgram(b.end());
    }
}
