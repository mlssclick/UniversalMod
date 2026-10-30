package universalmod.render.smooth;

import universalmod.render.GlUiState;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import universalmod.render.ShaderDiagnostics;
import universalmod.render.UiRender;
import universalmod.render.UiShaders;

import java.util.ArrayList;
import java.util.List;

 








public final class SmoothRoundedRenderer implements AutoCloseable {
    private static final SmoothRoundedRenderer INSTANCE = new SmoothRoundedRenderer();
    private static final int MAX_BATCH = 128;
    private final List<Submission> pending = new ArrayList<>(32);
    private int batching;

    private record Submission(SmoothRoundedShape shape, Matrix4f matrix) { }

    private SmoothRoundedRenderer() { }
    public static SmoothRoundedRenderer getInstance() { return INSTANCE; }
    public static void closeInstance() { INSTANCE.close(); }

    public void beginFrame(DrawContext context) {
        
        flush();
    }

    public void enqueue(SmoothRoundedShape shape) {
        if (shape == null || !shape.visible()) return;
        Submission item = new Submission(shape, new Matrix4f(UiRender.matrix()));
        if (batching == 0) {
            drawGroup(List.of(item), 0, 1);
        } else {
            pending.add(item);
            if (pending.size() >= MAX_BATCH) flush();
        }
    }

     
    public void batch(Runnable roundedOnly) {
        if (roundedOnly == null) return;
        batching++;
        try {
            roundedOnly.run();
        } finally {
            batching--;
            if (batching == 0) flush();
        }
    }

    public void flush() {
        if (pending.isEmpty()) return;
        try {
            int i = 0;
            while (i < pending.size()) {
                SmoothRoundedShape first = pending.get(i).shape();
                int j = i + 1;
                while (j < pending.size() && compatible(first, pending.get(j).shape())) j++;
                drawGroup(pending, i, j);
                i = j;
            }
        } finally {
            pending.clear();
        }
    }

    
    
    private static boolean compatible(SmoothRoundedShape a, SmoothRoundedShape b) {
        return Float.floatToIntBits(a.width()) == Float.floatToIntBits(b.width())
                && Float.floatToIntBits(a.height()) == Float.floatToIntBits(b.height())
                && Float.floatToIntBits(a.radius()) == Float.floatToIntBits(b.radius());
    }

    private static void drawGroup(List<Submission> items, int from, int to) {
        if (from >= to) return;
        GlUiState savedState = GlUiState.capture();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        try {
            ShaderProgram shader = UiShaders.bind(UiShaders.SMOOTH_ROUNDED, "ui_smooth_rounded");
            if (shader == null) {
                ShaderDiagnostics.missing("ui_smooth_rounded");
                return;
            }
            SmoothRoundedShape first = items.get(from).shape();
            float limit = Math.max(0f, Math.min(first.width(), first.height()) * .5f);
            float signed = Math.copySign(Math.min(Math.abs(first.radius()), limit), first.radius());
            var size = shader.getUniform("Size");
            if (size != null) size.set(first.width(), first.height());
            var radius = shader.getUniform("Radius");
            if (radius != null) radius.set(signed);

            BufferBuilder b = Tessellator.getInstance().getBuffer();
            b.begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            for (int k = from; k < to; k++) {
                Submission s = items.get(k);
                SmoothRoundedShape shape = s.shape();
                Matrix4f m = s.matrix();
                float x = shape.x(), y = shape.y(), w = shape.width(), h = shape.height();
                int color = shape.color();
                b.vertex(m, x, y, 0).texture(0f, 0f).color(color).next();
                b.vertex(m, x, y + h, 0).texture(0f, 1f).color(color).next();
                b.vertex(m, x + w, y + h, 0).texture(1f, 1f).color(color).next();
                b.vertex(m, x + w, y, 0).texture(1f, 0f).color(color).next();
            }
            BufferRenderer.drawWithGlobalProgram(b.end());
        } finally {
            savedState.close();
        }
    }

    @Override
    public void close() {
        flush();
        batching = 0;
    }
}
