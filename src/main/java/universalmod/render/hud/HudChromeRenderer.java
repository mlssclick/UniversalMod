package universalmod.render.hud;

import universalmod.render.GlUiState;

import universalmod.render.UiShaders;
import universalmod.render.UiRender;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;

public final class HudChromeRenderer {
    private HudChromeRenderer() {
    }

    public static void draw(float x, float y, float w, float h, float r1, float r2, float r3, float r4, float alpha, float smoothness, float darkness) {
        if (w <= 0f || h <= 0f || alpha <= 0.0001f) return;
        GlUiState savedState = GlUiState.capture();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        try {
            ShaderProgram shader = UiShaders.bind(UiShaders.HUD_CHROME, "hud_chrome");
            if (shader == null) return;
            set2(shader, "Size", w, h);
            set4(shader, "Radius", r1, r2, r3, r4);
            set1(shader, "GlobalAlpha", alpha);
            set1(shader, "Smoothness", smoothness);
            set1(shader, "Darkness", darkness);
            BufferBuilder b = Tessellator.getInstance().getBuffer();
            b.begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            var m = UiRender.matrix();
            b.vertex(m, x, y, 0).texture(0.0f, 0.0f).color(0xFFFFFFFF).next();
            b.vertex(m, x, y + h, 0).texture(0.0f, 1.0f).color(0xFFFFFFFF).next();
            b.vertex(m, x + w, y + h, 0).texture(1.0f, 1.0f).color(0xFFFFFFFF).next();
            b.vertex(m, x + w, y, 0).texture(1.0f, 0.0f).color(0xFFFFFFFF).next();
            BufferRenderer.drawWithGlobalProgram(b.end());
        } finally {
            savedState.close();
        }
    }

    private static void set1(ShaderProgram s, String n, float a) {
        var u = s.getUniform(n);
        if (u != null) u.set(a);
    }

    private static void set2(ShaderProgram s, String n, float a, float b) {
        var u = s.getUniform(n);
        if (u != null) u.set(a, b);
    }

    private static void set4(ShaderProgram s, String n, float a, float b, float c, float d) {
        var u = s.getUniform(n);
        if (u != null) u.set(a, b, c, d);
    }
}
