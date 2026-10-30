package universalmod.render.surface;

import universalmod.render.GlUiState;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import universalmod.render.UiRender;
import universalmod.render.UiShaders;
import universalmod.render.color.ColorUtil;


public final class GuiSurfaceRenderer implements AutoCloseable {
    private static final GuiSurfaceRenderer INSTANCE = new GuiSurfaceRenderer();
    private DrawContext activeGraphics;

    private GuiSurfaceRenderer() {}

    public static GuiSurfaceRenderer getInstance() { return INSTANCE; }
    public static void closeInstance() { INSTANCE.close(); }

    public void beginFrame(DrawContext graphics) { activeGraphics = graphics; }
    public void enqueue(GuiSurface surface) { draw(activeGraphics, surface); }

    public void draw(DrawContext graphics, GuiSurface surface) {
        if (surface == null || !surface.visible()) return;
        float maxRadius = Math.max(0.0f, Math.min(surface.width(), surface.height()) * 0.5f);
        float rtl = clamp(surface.radiusTopLeft(), 0.0f, maxRadius);
        float rtr = clamp(surface.radiusTopRight(), 0.0f, maxRadius);
        float rbr = clamp(surface.radiusBottomRight(), 0.0f, maxRadius);
        float rbl = clamp(surface.radiusBottomLeft(), 0.0f, maxRadius);
        float alpha = clamp(surface.alpha(), 0.0f, 1.0f);
        float smoothness = Math.max(0.45f, surface.smoothness());
        float accentStrength = clamp(surface.accentStrength(), 0.0f, 1.0f);

        GlUiState savedState = GlUiState.capture();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        try {
            ShaderProgram shader = UiShaders.bind(UiShaders.GUI_SURFACE, "ui_surface");
            if (shader == null) return;
            set2(shader, "Size", surface.width(), surface.height());
            set4(shader, "Radius", rtl, rtr, rbr, rbl);
            set1(shader, "GlobalAlpha", alpha);
            set1(shader, "Smoothness", smoothness);
            set1(shader, "AccentStrength", accentStrength);
            setColor(shader, "BaseColor", surface.baseColor());
            setColor(shader, "AccentColor", surface.accentColor());

            BufferBuilder b = Tessellator.getInstance().getBuffer();
            b.begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            b.vertex(UiRender.matrix(), surface.x(), surface.y(), 0.0f).texture(0.0f, 0.0f).color(0xFFFFFFFF).next();
            b.vertex(UiRender.matrix(), surface.x(), surface.y() + surface.height(), 0.0f).texture(0.0f, 1.0f).color(0xFFFFFFFF).next();
            b.vertex(UiRender.matrix(), surface.x() + surface.width(), surface.y() + surface.height(), 0.0f).texture(1.0f, 1.0f).color(0xFFFFFFFF).next();
            b.vertex(UiRender.matrix(), surface.x() + surface.width(), surface.y(), 0.0f).texture(1.0f, 0.0f).color(0xFFFFFFFF).next();
            BufferRenderer.drawWithGlobalProgram(b.end());
        } finally {
            savedState.close();
        }
    }

    @Override public void close() { activeGraphics = null; }

    private static void set1(ShaderProgram shader, String name, float value) {
        var uniform = shader.getUniform(name); if (uniform != null) uniform.set(value);
    }
    private static void set2(ShaderProgram shader, String name, float a, float b) {
        var uniform = shader.getUniform(name); if (uniform != null) uniform.set(a, b);
    }
    private static void set4(ShaderProgram shader, String name, float a, float b, float c, float d) {
        var uniform = shader.getUniform(name); if (uniform != null) uniform.set(a, b, c, d);
    }
    private static void setColor(ShaderProgram shader, String name, int color) {
        set4(shader, name,
                ColorUtil.getRed(color) / 255.0f,
                ColorUtil.getGreen(color) / 255.0f,
                ColorUtil.getBlue(color) / 255.0f,
                ColorUtil.getAlpha(color) / 255.0f);
    }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
}
