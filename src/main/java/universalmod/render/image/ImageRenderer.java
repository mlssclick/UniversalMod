package universalmod.render.image;

import universalmod.render.GlUiState;

import universalmod.render.UiShaders;
import universalmod.render.ShaderDiagnostics;
import universalmod.render.UiRender;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;

public final class ImageRenderer {
    private ImageRenderer() {
    }

     
    public static void draw(ImageCommand image) {
        if (image == null || !image.visible()) return;
        Identifier id = resolveIdentifier(image.texture());
        if (id == null) return;
        AbstractTexture texture = MinecraftClient.getInstance().getTextureManager().getTexture(id);
        if (texture == null) return;
        ImageSizeResolver.Dimensions dimensions = ImageSizeResolver.dimensions(id, texture);
        float width = image.hasExplicitDimensions() ? image.explicitWidth() : dimensions.drawWidth(image.size());
        float height = image.hasExplicitDimensions() ? image.explicitHeight() : dimensions.drawHeight(image.size());
        if (width <= 0 || height <= 0) return;
        float radius = Math.max(0f, Math.min(image.radius(), Math.min(width, height) * .5f));
        float smoothness = Math.max(.001f, image.smoothness());
        float u0 = clamp01(image.u0()), v0 = clamp01(image.v0());
        float u1 = clamp01(image.u1()), v1 = clamp01(image.v1());
        float radians = (float) Math.toRadians(image.rotationDegrees());
        ImageQuad quad = new ImageQuad(image.x(), image.y(), width, height, radius, smoothness,
                image.rotationDegrees(), image.rotationOriginX(), image.rotationOriginY(),
                (float) Math.cos(radians), (float) Math.sin(radians),
                u0, v0, u1, v1,
                image.colorTopLeft(), image.colorTopRight(), image.colorBottomRight(), image.colorBottomLeft());
        drawQuad(id, quad, image.nearestFilter());
    }

    public static Identifier resolveIdentifier(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) return null;
        String path = rawPath.trim().replace('\\', '/');
        if (path.startsWith("/")) path = path.substring(1);
        if (path.startsWith("assets/")) {
            path = path.substring(7);
            int slash = path.indexOf('/');
            if (slash > 0) return new Identifier(path.substring(0, slash), path.substring(slash + 1));
        }
        if (path.contains(":")) return new Identifier(path);
        if (!path.startsWith("images/") && !path.startsWith("textures/")) path = "images/" + path;
        if (!path.substring(path.lastIndexOf('/') + 1).contains(".")) path += ".png";
        return new Identifier("universalmod", path);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    public static void releaseResources() {
        ImageSizeResolver.clearCache();
        ImageSamplers.release();
    }

    public static void draw(Identifier texture, float u0, float v0, float u1, float v1, float x, float y, float w, float h, float radius, float smoothness, int color) {
        draw(texture, u0, v0, u1, v1, x, y, w, h, radius, smoothness, color, color, color, color, false);
    }

    public static void draw(Identifier texture, float u0, float v0, float u1, float v1, float x, float y, float w, float h, float radius, float smoothness, int cTL, int cTR, int cBR, int cBL, boolean nearest) {
        if (texture == null || w <= 0f || h <= 0f) return;
        float normalizedRadius = Math.max(0f, Math.min(radius, Math.min(w, h) * .5f));
        ImageQuad quad = new ImageQuad(x, y, w, h, normalizedRadius, Math.max(.001f, smoothness),
                0f, 0f, 0f, 1f, 0f,
                clamp01(u0), clamp01(v0), clamp01(u1), clamp01(v1), cTL, cTR, cBR, cBL);
        drawQuad(texture, quad, nearest);
    }

    private static void drawQuad(Identifier texture, ImageQuad quad, boolean nearest) {
        GlUiState savedState = GlUiState.capture();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShaderTexture(0, texture);
        try (ImageSamplers.Binding binding = ImageSamplers.bind(nearest)) {
            ShaderProgram shader = UiShaders.bind(UiShaders.IMAGE, "ui_image");
            if (shader == null) {
                ShaderDiagnostics.missing("ui_image");
                return;
            }
            set2(shader, "Size", quad.width(), quad.height());
            set4(shader, "Radius", quad.radius(), quad.radius(), quad.radius(), quad.radius());
            set1(shader, "Smoothness", quad.smoothness());
            set4(shader, "UvRect", quad.u0(), quad.v0(), quad.u1(), quad.v1());
            set4(shader, "ColorModulator", 1f, 1f, 1f, 1f);
            BufferBuilder b = Tessellator.getInstance().getBuffer();
            b.begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            vertex(b, quad, quad.x(), quad.y(), 0f, 0f, quad.colorTopLeft());
            vertex(b, quad, quad.x(), quad.y() + quad.height(), 0f, 1f, quad.colorBottomLeft());
            vertex(b, quad, quad.x() + quad.width(), quad.y() + quad.height(), 1f, 1f, quad.colorBottomRight());
            vertex(b, quad, quad.x() + quad.width(), quad.y(), 1f, 0f, quad.colorTopRight());
            BufferRenderer.drawWithGlobalProgram(b.end());
        } finally {
            savedState.close();
        }
    }

    private static void vertex(BufferBuilder buffer, ImageQuad quad, float x, float y,
                               float u, float v, int color) {
        float ox = quad.rotationOriginX(), oy = quad.rotationOriginY();
        float dx = x - ox, dy = y - oy;
        float rotatedX = ox + dx * quad.rotationCos() - dy * quad.rotationSin();
        float rotatedY = oy + dx * quad.rotationSin() + dy * quad.rotationCos();
        buffer.vertex(UiRender.matrix(), rotatedX, rotatedY, 0f)
                .texture(u, v).color(color).next();
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
