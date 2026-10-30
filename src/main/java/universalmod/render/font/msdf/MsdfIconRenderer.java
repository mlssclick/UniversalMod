package universalmod.render.font.msdf;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import universalmod.render.ClientPipelines;
import universalmod.render.RenderSampler;
import universalmod.render.Render2DCoordinateSpace;
import universalmod.render.font.FontType;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;




public final class MsdfIconRenderer implements AutoCloseable {
    private static final MsdfIconRenderer INSTANCE = new MsdfIconRenderer();
    private final Map<String, Atlas> atlases = new HashMap<>();
    private DrawContext activeGraphics;

    private MsdfIconRenderer() {
    }

    public static MsdfIconRenderer getInstance() {
        return INSTANCE;
    }

    public static void closeInstance() {
        INSTANCE.close();
    }

    public void beginFrame(DrawContext g) {
        activeGraphics = g;
    }

    public void draw(FontType font, String text, float x, float y, float size, int color) {
        draw(font, text, x, y, size, color, color, color, color, 0, x, y, false, false, 0, 0, 0, 0, 0);
    }

    public void draw(FontType font, String text, float x, float y, float size, int tl, int tr, int br, int bl, float rotation, float ox, float oy, boolean fadeLeft, boolean fadeRight, float fadeLeftX, float fadeRightX, float fadeWidth, float leftStrength, float rightStrength) {
        if (font == null) font = FontType.DEFAULT;
        if (text == null || text.isEmpty() || size <= 0) return;
        Atlas a = atlas(font);
        if (!a.ready()) return;
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShaderTexture(0, a.texture().getGlId());
        try {
            ShaderProgram s = ClientPipelines.bind(ClientPipelines.MSDF_ICON, "ui_msdf_icon");
            if (s == null) return;
            var d = s.getUniform("DistanceRange");
            if (d != null) d.set(a.distanceRange());
            var mod = s.getUniform("ColorModulator");
            if (mod != null) mod.set(1.0F, 1.0F, 1.0F, 1.0F);
            float scale = size / a.fontSize(), cx = x;
            int prev = -1;
            boolean wrote = false;
            Matrix4f m = Render2DCoordinateSpace.matrix();
            if (Math.abs(rotation) > .001f)
                m = new Matrix4f(m).translate(ox, oy, 0).rotateZ((float) Math.toRadians(rotation)).translate(-ox, -oy, 0);
            BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            for (int idx = 0; idx < text.length(); ) {
                int cp = text.codePointAt(idx);
                idx += Character.charCount(cp);
                Glyph g = a.glyphs().get(cp);
                if (g == null) continue;
                cx += a.kernings().getOrDefault(key(prev, cp), 0f) * scale;
                if (g.w() > 0 && g.h() > 0) {
                    float gx = cx + g.x() * scale, gy = y + g.y() * scale, gw = g.w() * scale, gh = g.h() * scale;
                    int ctl = fadeColor(tl, gx, fadeLeftX, fadeRightX, fadeWidth, fadeLeft ? leftStrength : 0, fadeRight ? rightStrength : 0), cbl = fadeColor(bl, gx, fadeLeftX, fadeRightX, fadeWidth, fadeLeft ? leftStrength : 0, fadeRight ? rightStrength : 0), ctr = fadeColor(tr, gx + gw, fadeLeftX, fadeRightX, fadeWidth, fadeLeft ? leftStrength : 0, fadeRight ? rightStrength : 0), cbr = fadeColor(br, gx + gw, fadeLeftX, fadeRightX, fadeWidth, fadeLeft ? leftStrength : 0, fadeRight ? rightStrength : 0);
                    b.vertex(m, gx, gy, 0).texture(g.u0(), g.v0()).color(ctl);
                    b.vertex(m, gx, gy + gh, 0).texture(g.u0(), g.v1()).color(cbl);
                    b.vertex(m, gx + gw, gy + gh, 0).texture(g.u1(), g.v1()).color(cbr);
                    b.vertex(m, gx + gw, gy, 0).texture(g.u1(), g.v0()).color(ctr);
                    wrote = true;
                }
                cx += g.advance() * scale;
                prev = cp;
            }
            if (wrote) BufferRenderer.drawWithGlobalProgram(b.end());
        } finally {
            RenderSystem.setShaderTexture(0, 0);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
        }
    }

    public boolean canRender(FontType font, String text) {
        if (font == null || text == null || text.isEmpty()) return false;
        Atlas a = atlas(font);
        if (!a.ready()) return false;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isISOControl(cp) || !a.glyphs().containsKey(cp)) return false;
        }
        return true;
    }

    public float width(FontType font, String text, float size) {
        if (font == null || text == null || text.isEmpty() || size <= 0) return 0;
        Atlas a = atlas(font);
        if (!a.ready()) return 0;
        float scale = size / a.fontSize(), w = 0;
        int prev = -1;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            Glyph g = a.glyphs().get(cp);
            if (g == null) continue;
            w += (a.kernings().getOrDefault(key(prev, cp), 0f) + g.advance()) * scale;
            prev = cp;
        }
        return w;
    }

    public float lineHeight(FontType font, float size) {
        Atlas a = atlas(font == null ? FontType.DEFAULT : font);
        return a.ready() ? a.lineHeight() * size : 0;
    }

    public VisualBounds visualBounds(FontType font, String text, float size) {
        GlyphLayout l = glyphLayout(font, text, size);
        if (l.empty()) return VisualBounds.EMPTY;
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (LayoutGlyph g : l.glyphs()) {
            minX = Math.min(minX, g.x0());
            minY = Math.min(minY, g.y0());
            maxX = Math.max(maxX, g.x1());
            maxY = Math.max(maxY, g.y1());
        }
        return Float.isFinite(minX) ? new VisualBounds(minX, minY, maxX, maxY) : VisualBounds.EMPTY;
    }

    public GlyphLayout glyphLayout(FontType font, String text, float size) {
        if (font == null || text == null || text.isEmpty() || size <= 0) return GlyphLayout.EMPTY;
        Atlas a = atlas(font);
        if (!a.ready()) return GlyphLayout.EMPTY;
        float scale = size / a.fontSize(), cx = 0;
        int prev = -1;
        List<LayoutGlyph> glyphs = new ArrayList<>();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            Glyph g = a.glyphs().get(cp);
            if (g == null) continue;
            cx += a.kernings().getOrDefault(key(prev, cp), 0f) * scale;
            if (g.w() > 0 && g.h() > 0) {
                float x0 = cx + g.x() * scale, y0 = g.y() * scale;
                glyphs.add(new LayoutGlyph(x0, y0, x0 + g.w() * scale, y0 + g.h() * scale, g.u0(), g.v0(), g.u1(), g.v1()));
            }
            cx += g.advance() * scale;
            prev = cp;
        }
        return new GlyphLayout(a.textureId(), a.distanceRange(), List.copyOf(glyphs), cx, a.lineHeight() * size);
    }

    public void flush() {
        activeGraphics = null;
    }

    @Override
    public void close() {
        activeGraphics = null;
        atlases.clear();
    }

    private Atlas atlas(FontType font) {
        return atlases.computeIfAbsent(font.path(), this::load);
    }

    private Atlas load(String name) {
        Identifier json = Identifier.of("universalmod", "ui/msdf/" + name + ".json"), png = Identifier.of("universalmod", "ui/msdf/" + name + ".png");
        try {
            JsonObject root;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(MinecraftClient.getInstance().getResourceManager().open(json), StandardCharsets.UTF_8))) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            }
            JsonObject at = root.getAsJsonObject("atlas");
            float aw = f(at, "width", 1), ah = f(at, "height", 1), fontSize = f(at, "size", 64), range = Math.max(1, f(at, "distanceRange", 8));
            boolean originBottom = at.has("yOrigin") && "bottom".equalsIgnoreCase(at.get("yOrigin").getAsString());
            JsonObject metrics = root.getAsJsonObject("metrics");
            float line = Math.max(.01f, f(metrics, "lineHeight", 1)), desc = f(metrics, "descender", -.05f), baseline = line + desc;
            Map<Integer, Glyph> gs = new HashMap<>();
            for (JsonElement el : root.getAsJsonArray("glyphs")) {
                JsonObject g = el.getAsJsonObject();
                int cp = g.get("unicode").getAsInt();
                float adv = f(g, "advance", 1) * fontSize;
                if (g.has("atlasBounds") && g.has("planeBounds")) {
                    JsonObject ab = g.getAsJsonObject("atlasBounds"), pb = g.getAsJsonObject("planeBounds");
                    float left = f(ab, "left", 0), bottom = f(ab, "bottom", 0), right = f(ab, "right", 0), top = f(ab, "top", 0), ty = originBottom ? ah - top : bottom, pl = f(pb, "left", 0), pbot = f(pb, "bottom", 0), pr = f(pb, "right", 0), pt = f(pb, "top", 0);
                    gs.put(cp, new Glyph(pl * fontSize, (baseline - pt) * fontSize, (pr - pl) * fontSize, (pt - pbot) * fontSize, adv, left / aw, ty / ah, right / aw, (ty + top - bottom) / ah));
                } else gs.put(cp, new Glyph(0, 0, 0, 0, adv, 0, 0, 0, 0));
            }
            Map<Long, Float> ks = new HashMap<>();
            if (root.has("kerning")) for (JsonElement el : root.getAsJsonArray("kerning")) {
                JsonObject k = el.getAsJsonObject();
                ks.put(key(k.get("unicode1").getAsInt(), k.get("unicode2").getAsInt()), f(k, "advance", 0) * fontSize);
            }
            AbstractTexture tex = MinecraftClient.getInstance().getTextureManager().getTexture(png);
            RenderSystem.recordRenderCall(() -> RenderSampler.linear(tex));
            return new Atlas(png, tex, range, fontSize, line, gs, ks, true);
        } catch (Throwable t) {

            return Atlas.EMPTY;
        }
    }

    private static int fadeColor(int color, float px, float left, float right, float width, float ls, float rs) {
        float a = 1, fw = Math.max(.001f, width);
        if (ls > 0) {
            float t = clamp((px - left) / fw);
            t = t * t * (3 - 2 * t);
            a = Math.min(a, 1 - clamp(ls) * (1 - t));
        }
        if (rs > 0) {
            float t = clamp((right - px) / fw);
            t = t * t * (3 - 2 * t);
            a = Math.min(a, 1 - clamp(rs) * (1 - t));
        }
        int ca = (color >>> 24) & 255;
        return (color & 0x00FFFFFF) | (Math.round(ca * a) << 24);
    }

    private static float clamp(float v) {
        return Math.max(0, Math.min(1, v));
    }

    private static long key(int l, int r) {
        return ((long) l << 32) ^ (r & 0xffffffffL);
    }

    private static float f(JsonObject o, String k, float d) {
        return o != null && o.has(k) ? o.get(k).getAsFloat() : d;
    }

    private record Glyph(float x, float y, float w, float h, float advance, float u0, float v0, float u1, float v1) {
    }

    private record Atlas(Identifier textureId, AbstractTexture texture, float distanceRange, float fontSize,
                         float lineHeight, Map<Integer, Glyph> glyphs, Map<Long, Float> kernings, boolean ready) {
        private static final Atlas EMPTY = new Atlas(null, null, 1, 64, 1, Map.of(), Map.of(), false);
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

    public record LayoutGlyph(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1) {
    }

    public record GlyphLayout(Identifier texture, float distanceRange, List<LayoutGlyph> glyphs, float width,
                              float lineHeight) {
        public static final GlyphLayout EMPTY = new GlyphLayout(null, 0, List.of(), 0, 0);

        public boolean empty() {
            return glyphs.isEmpty();
        }
    }
}
