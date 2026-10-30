package universalmod.render.effect;

import universalmod.render.UiShaders;
import universalmod.render.UiRender;
import universalmod.render.GlUiState;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.Sprite;

public final class EffectIconRenderer {
    private EffectIconRenderer() {
    }

    public static void draw(Sprite sprite, float x, float y, float size, int color) {
        if (sprite == null || size <= 0f) return;
        GlUiState savedState = GlUiState.capture();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShaderTexture(0, sprite.getAtlasId());
        try {
            ShaderProgram shader = UiShaders.bind(UiShaders.EFFECT_ICON, "effect_icon");
            if (shader == null) return;
            var mod = shader.getUniform("ColorModulator");
            if (mod != null) mod.set(1f, 1f, 1f, 1f);
            float u0 = sprite.getMinU(), u1 = sprite.getMaxU(), v0 = sprite.getMinV(), v1 = sprite.getMaxV();
            BufferBuilder b = Tessellator.getInstance().getBuffer();
            b.begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            var m = UiRender.matrix();
            b.vertex(m, x, y, 0).texture(u0, v0).color(color).next();
            b.vertex(m, x, y + size, 0).texture(u0, v1).color(color).next();
            b.vertex(m, x + size, y + size, 0).texture(u1, v1).color(color).next();
            b.vertex(m, x + size, y, 0).texture(u1, v0).color(color).next();
            BufferRenderer.drawWithGlobalProgram(b.end());
        } finally {
            savedState.close();
        }
    }
}
