package universalmod.render.font.msdf;

import universalmod.render.GlUiState;

import universalmod.render.UiShaders;
import universalmod.render.ShaderDiagnostics;
import universalmod.render.UiRender;
import com.mojang.blaze3d.systems.RenderSystem;

import java.io.InputStream;
import java.util.Map;
import java.util.LinkedHashMap;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

public final class MsdfFont {
    private final String name;
    private final Identifier textureId;
    private final float distanceRange;
    private final float fontSize;
    private final float baselineHeight;
    private final float lineHeight;
    private final Map<Integer, MsdfGlyph> glyphs;
    private final MsdfGlyph[] asciiGlyphs;
    private final Map<Long, Float> kernings;

    
    private final Map<TextSize, Float> widthCache = boundedCache(512);
    private final Map<TextSize, VisualBounds> boundsCache = boundedCache(512);
    private final Map<TextSize, GlyphLayout> worldLayoutCache = boundedCache(256);

    private static <V> Map<TextSize, V> boundedCache(int limit) {
        return new LinkedHashMap<TextSize, V>(128, .75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<TextSize, V> eldest) {
                return size() > limit;
            }
        };
    }

    private record TextSize(String text, int sizeBits) {
        static TextSize of(String text, float size) { return new TextSize(text, Float.floatToIntBits(size)); }
    }

    private MsdfFont(String name, MsdfAtlas atlas) {
        this.name = name;
        this.textureId = atlas.textureId();
        this.distanceRange = atlas.distanceRange();
        this.fontSize = atlas.fontSize();
        this.baselineHeight = atlas.baselineHeight();
        this.lineHeight = atlas.lineHeight();
        this.glyphs = atlas.glyphs();
        this.asciiGlyphs = atlas.asciiGlyphs();
        this.kernings = atlas.kernings();
    }

     

    public static MsdfFont load(String name) {
        var manager = MinecraftClient.getInstance().getResourceManager();
        MsdfAtlas atlas = MsdfAtlasLoader.load(name, manager);
        Identifier pngId = atlas.textureId();
        try (InputStream stream = manager.getResource(pngId).orElseThrow().getInputStream()) {
            NativeImage image = NativeImage.read(stream);
            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
            texture.setFilter(true, false);
            MinecraftClient.getInstance().getTextureManager().registerTexture(pngId, texture);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to register MSDF atlas " + pngId, exception);
        }
        return new MsdfFont(name, atlas);
    }

    private MsdfGlyph glyph(int codePoint) {
        return codePoint >= 0 && codePoint < asciiGlyphs.length
                ? asciiGlyphs[codePoint] : glyphs.get(codePoint);
    }

    public void draw(String text, float x, float y, float size, int color) {
        drawGradient(text, x, y, size, color, color, color, color);
    }

     
    public void drawGradient(String text, float x, float y, float size,
                             int colorTopLeft, int colorTopRight,
                             int colorBottomRight, int colorBottomLeft) {
        if (text == null || text.isEmpty() || size <= 0) return;
        if (MsdfTextBatch.enqueue(this, text, x, y, size,
                colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft,
                UiRender.matrix())) return;
        
        drawBatch(java.util.List.of(new MsdfTextBatch.Run(this, text, x, y, size,
                colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft,
                new org.joml.Matrix4f(UiRender.matrix()))), 0, 1);
    }

     

    void drawBatch(java.util.List<MsdfTextBatch.Run> runs, int start, int end) {
        if (start >= end) return;
        GlUiState savedState = GlUiState.capture();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShaderTexture(0, textureId);
        try {
            ShaderProgram shader = UiShaders.bind(UiShaders.MSDF, "msdf");
            if (shader == null) {
                ShaderDiagnostics.missing("msdf");
                return;
            }
            var distance = shader.getUniform("DistanceRange");
            if (distance != null) distance.set(distanceRange);
            var modulator = shader.getUniform("ColorModulator");
            if (modulator != null) modulator.set(1f, 1f, 1f, 1f);
            BufferBuilder b = Tessellator.getInstance().getBuffer();
            b.begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            for (int i = start; i < end; i++) appendRun(b, runs.get(i));
            BufferBuilder.BuiltBuffer built = b.endNullable();
            if (built != null) BufferRenderer.drawWithGlobalProgram(built);
        } finally {
            savedState.close();
        }
    }

    private void appendRun(BufferBuilder b, MsdfTextBatch.Run run) {
        float scale = run.size() / fontSize;
        float cx = run.x();
        int prev = -1;
        String text = run.text();
        var matrix = run.matrix();
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            MsdfGlyph g = glyph(cp);
            if (g == null) continue;
            cx += kernings.getOrDefault(key(prev, cp), 0f) * scale;
            if (g.width() > 0 && g.height() > 0) {
                float gx = cx + g.xOffset() * scale;
                float gy = run.y() + g.yOffset() * scale;
                float gw = g.width() * scale;
                float gh = g.height() * scale;
                b.vertex(matrix, gx, gy, 0).texture(g.u0(), g.v0()).color(run.topLeft()).next();
                b.vertex(matrix, gx, gy + gh, 0).texture(g.u0(), g.v1()).color(run.bottomLeft()).next();
                b.vertex(matrix, gx + gw, gy + gh, 0).texture(g.u1(), g.v1()).color(run.bottomRight()).next();
                b.vertex(matrix, gx + gw, gy, 0).texture(g.u1(), g.v0()).color(run.topRight()).next();
            }
            cx += g.advance() * scale;
            prev = cp;
        }
    }

    public void drawFade(String text, float x, float y, float size, int color, float fadeLeftX, float fadeRightX, float fadeWidth, float leftStrength, float rightStrength) {
        if (text == null || text.isEmpty() || size <= 0) return;
        
        MsdfTextBatch.flushPending();
        GlUiState savedState = GlUiState.capture();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShaderTexture(0, textureId);
        try {
            ShaderProgram shader = UiShaders.bind(UiShaders.MSDF, "msdf");
            if (shader == null) return;
            var distance = shader.getUniform("DistanceRange");
            if (distance != null) distance.set(distanceRange);
            var modulator = shader.getUniform("ColorModulator");
            if (modulator != null) modulator.set(1f, 1f, 1f, 1f);
            float scale = size / fontSize, cx = x;
            int prev = -1;
            var matrix = UiRender.matrix();
            BufferBuilder b = Tessellator.getInstance().getBuffer();
            b.begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            for (int i = 0; i < text.length(); ) {
                int cp = text.codePointAt(i);
                i += Character.charCount(cp);
                MsdfGlyph g = glyph(cp);
                if (g == null) continue;
                cx += kernings.getOrDefault(key(prev, cp), 0f) * scale;
                if (g.width() > 0 && g.height() > 0) {
                    float gx = cx + g.xOffset() * scale, gy = y + g.yOffset() * scale, gw = g.width() * scale, gh = g.height() * scale;
                    int cl = fadeColor(color, gx, fadeLeftX, fadeRightX, fadeWidth, leftStrength, rightStrength);
                    int cr = fadeColor(color, gx + gw, fadeLeftX, fadeRightX, fadeWidth, leftStrength, rightStrength);
                    b.vertex(matrix, gx, gy, 0).texture(g.u0(), g.v0()).color(cl).next();
                    b.vertex(matrix, gx, gy + gh, 0).texture(g.u0(), g.v1()).color(cl).next();
                    b.vertex(matrix, gx + gw, gy + gh, 0).texture(g.u1(), g.v1()).color(cr).next();
                    b.vertex(matrix, gx + gw, gy, 0).texture(g.u1(), g.v0()).color(cr).next();
                }
                cx += g.advance() * scale;
                prev = cp;
            }
            BufferBuilder.BuiltBuffer built = b.endNullable();
            if (built != null) BufferRenderer.drawWithGlobalProgram(built);
        } finally {
            savedState.close();
        }
    }

    private static int fadeColor(int color, float px, float left, float right, float width, float ls, float rs) {
        float a = 1f;
        float fw = Math.max(.001f, width);
        if (ls > 0f) {
            float t = clamp01((px - left) / fw);
            t = t * t * (3f - 2f * t);
            a = Math.min(a, 1f - clamp01(ls) * (1f - t));
        }
        if (rs > 0f) {
            float t = clamp01((right - px) / fw);
            t = t * t * (3f - 2f * t);
            a = Math.min(a, 1f - clamp01(rs) * (1f - t));
        }
        int ca = (color >>> 24) & 255;
        int na = Math.round(ca * a);
        return (color & 0x00FFFFFF) | (na << 24);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    public synchronized float width(String text, float size) {
        if (text == null || text.isEmpty()) return 0.0f;
        return widthCache.computeIfAbsent(TextSize.of(text, size), key -> measureWidth(text, size));
    }

    public synchronized VisualBounds visualBounds(String text, float size) {
        if (text == null || text.isEmpty()) return VisualBounds.EMPTY;
        return boundsCache.computeIfAbsent(TextSize.of(text, size), key -> measureVisualBounds(text, size));
    }

    private float measureWidth(String text, float size) {
        if (text == null || text.isEmpty()) return 0;
        float scale = size / fontSize, w = 0;
        int prev = -1;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            MsdfGlyph g = glyph(cp);
            if (g == null) continue;
            w += (kernings.getOrDefault(key(prev, cp), 0f) + g.advance()) * scale;
            prev = cp;
        }
        return w;
    }

    private VisualBounds measureVisualBounds(String text, float size) {
        if (text == null || text.isEmpty()) return VisualBounds.EMPTY;
        float scale = size / fontSize, cx = 0, minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        int prev = -1;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            MsdfGlyph g = glyph(cp);
            if (g == null) continue;
            cx += kernings.getOrDefault(key(prev, cp), 0f) * scale;
            if (g.width() > 0 && g.height() > 0) {
                float x0 = cx + g.xOffset() * scale, y0 = g.yOffset() * scale, x1 = x0 + g.width() * scale, y1 = y0 + g.height() * scale;
                minX = Math.min(minX, x0);
                minY = Math.min(minY, y0);
                maxX = Math.max(maxX, x1);
                maxY = Math.max(maxY, y1);
            }
            cx += g.advance() * scale;
            prev = cp;
        }
        return Float.isFinite(minX) ? new VisualBounds(minX, minY, maxX, maxY) : VisualBounds.EMPTY;
    }

    public float lineHeight(float size) {
        return lineHeight * size;
    }

     
    public synchronized GlyphLayout worldGlyphLayout(String text, float size) {
        if (text == null || text.isEmpty() || size <= 0f)
            return new GlyphLayout(java.util.List.of(), 0f, lineHeight(size), textureId);
        return worldLayoutCache.computeIfAbsent(TextSize.of(text, size), key -> buildWorldGlyphLayout(text, size));
    }

    private GlyphLayout buildWorldGlyphLayout(String text, float size) {
        if (text == null || text.isEmpty() || size <= 0.0F) {
            return new GlyphLayout(java.util.List.of(), 0.0F, lineHeight(size), textureId);
        }
        java.util.ArrayList<LayoutGlyph> positioned = new java.util.ArrayList<>();
        float scale = size / fontSize;
        float pen = 0.0F;
        int prev = -1;
        for (int offset = 0; offset < text.length();) {
            int cp = text.codePointAt(offset);
            offset += Character.charCount(cp);
            MsdfGlyph glyph = glyph(cp);
            if (glyph == null) continue;
            pen += kernings.getOrDefault(key(prev, cp), 0.0F) * scale;
            if (glyph.width() > 0.0F && glyph.height() > 0.0F) {
                float x0 = pen + glyph.xOffset() * scale;
                float y0 = glyph.yOffset() * scale;
                positioned.add(new LayoutGlyph(x0, y0, x0 + glyph.width() * scale, y0 + glyph.height() * scale,
                        glyph.u0(), glyph.v0(), glyph.u1(), glyph.v1()));
            }
            pen += glyph.advance() * scale;
            prev = cp;
        }
        return new GlyphLayout(java.util.List.copyOf(positioned), pen, lineHeight(size), textureId);
    }

    public record LayoutGlyph(float x0, float y0, float x1, float y1,
                              float u0, float v0, float u1, float v1) {}

    public record GlyphLayout(java.util.List<LayoutGlyph> glyphs, float width,
                              float lineHeight, Identifier texture) {
        public boolean empty() { return glyphs.isEmpty(); }
    }

    public String name() {
        return name;
    }

    private static long key(int l, int r) {
        return ((long) l << 32) ^ (r & 0xffffffffL);
    }

    public record VisualBounds(float minX, float minY, float maxX, float maxY) {
        public static final VisualBounds EMPTY = new VisualBounds(0, 0, 0, 0);

        public float width() {
            return Math.max(0, maxX - minX);
        }

        public float height() {
            return Math.max(0, maxY - minY);
        }

        public float centerY() {
            return (minY + maxY) * .5f;
        }

        public boolean empty() {
            return width() <= 0 || height() <= 0;
        }
    }
}
