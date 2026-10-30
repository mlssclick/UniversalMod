package universalmod.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.entry.RegistryEntry;
import org.joml.Matrix4f;
import universalmod.module.utility.NameProtect;
import universalmod.render.ScissorUtil;
import universalmod.render.color.ColorUtil;
import universalmod.render.item.CustomItemRenderer;
import universalmod.render.ClientPipelines;
import universalmod.render.blur.BlurFramebuffer;
import universalmod.render.blur.BlurPass;
import universalmod.render.effect.BuiltEffectIcon;
import universalmod.render.effect.EffectIconRenderer;
import universalmod.render.font.FontType;
import universalmod.render.glass.BuiltGlass;
import universalmod.render.glass.GlassRenderer;
import universalmod.render.hud.BuiltHudChrome;
import universalmod.render.hud.HudChromeRenderer;
import universalmod.render.image.BuiltImage;
import universalmod.render.image.ImageRenderer;
import universalmod.render.font.msdf.MsdfIconRenderer;
import universalmod.render.shape.BuiltOutline;
import universalmod.render.shape.DefaultOutlineRenderer;
import universalmod.render.glass.BuiltGlassOutline;
import universalmod.render.glass.GlassOutlineRenderer;
import universalmod.render.shape.BuiltRectangle;
import universalmod.render.shape.DefaultRectangleRenderer;
import java.util.ArrayDeque;
import java.util.Deque;

final class RenderText {
    private RenderText() {
    }

    static void text(String name, String text, float x, float y, float size, int color) {
            text(FontType.resolve(name), NameProtect.replace(text), x, y, size, color);
        }

    static void text(FontType font, String text, float x, float y, float size, int color) {
            RenderImages.barrier();
            MsdfIconRenderer.getInstance().draw(font == null ? FontType.DEFAULT : font, NameProtect.replace(text), x, y, size, color);
        }

    static void text(String name, String text, float x, float y, float size, int color, float rotation, float ox, float oy) {
            text(FontType.resolve(name), text, x, y, size, color, rotation, ox, oy);
        }

    static void text(String name, String text, float x, float y, float size, int tl, int tr, int br, int bl) {
            text(FontType.resolve(name), text, x, y, size, tl, tr, br, bl);
        }

    static void text(String name, String text, float x, float y, float size, int tl, int tr, int br, int bl, float rotation, float ox, float oy) {
            text(FontType.resolve(name), NameProtect.replace(text), x, y, size, tl, tr, br, bl, rotation, ox, oy);
        }

    static void text(FontType font, String text, float x, float y, float size, int tl, int tr, int br, int bl) {
            RenderImages.barrier();
            MsdfIconRenderer.getInstance().draw(font == null ? FontType.DEFAULT : font, NameProtect.replace(text), x, y, size, tl, tr, br, bl, 0, x, y, false, false, 0, 0, 0, 0, 0);
        }

    static void text(FontType font, String text, float x, float y, float size, int color, float rotation, float ox, float oy) {
            RenderImages.barrier();
            MsdfIconRenderer.getInstance().draw(font == null ? FontType.DEFAULT : font, NameProtect.replace(text), x, y, size, color, color, color, color, rotation, ox, oy, false, false, 0, 0, 0, 0, 0);
        }

    static void text(FontType font, String text, float x, float y, float size, int tl, int tr, int br, int bl, float rotation, float ox, float oy) {
            RenderImages.barrier();
            MsdfIconRenderer.getInstance().draw(font == null ? FontType.DEFAULT : font, NameProtect.replace(text), x, y, size, tl, tr, br, bl, rotation, ox, oy, false, false, 0, 0, 0, 0, 0);
        }

    static void text(DrawContext g, String name, String text, float x, float y, float size, int color) {
            Render2D.beginFrame(g);
            text(name, NameProtect.replace(text), x, y, size, color);
            Render2D.flush();
        }

    static void text(DrawContext g, FontType font, String text, float x, float y, float size, int color) {
            Render2D.beginFrame(g);
            text(font, NameProtect.replace(text), x, y, size, color);
            Render2D.flush();
        }

    static void animatedGlyphText(FontType font, String text, float x, float y, float size, int color, int first, String prevFirst, float firstProgress, int second, String prevSecond, float secondProgress, float verticalOffset) {
            if (font == null || text == null || text.isEmpty()) return;
            text = NameProtect.replace(text);
            for (int i = 0; i < text.length(); ) {
                int start = i;
                int cp = text.codePointAt(i);
                i += Character.charCount(cp);
                String glyph = new String(Character.toChars(cp));
                float gx = x + textWidth(font, text.substring(0, i), size) - textWidth(font, glyph, size);
                if (start == first)
                    drawAnimatedGlyph(font, glyph, prevFirst, gx, y, size, color, firstProgress, verticalOffset);
                else if (start == second)
                    drawAnimatedGlyph(font, glyph, prevSecond, gx, y, size, color, secondProgress, verticalOffset);
                else text(font, glyph, gx, y, size, color);
            }
        }

    private static void drawAnimatedGlyph(FontType f, String current, String previous, float x, float y, float size, int color, float p, float off) {
            p = Math.max(0, Math.min(1, p));
            if (previous != null && !previous.isEmpty() && p < .999f)
                text(f, previous, x, y + off * p, size, scaleAlpha(color, 1 - p));
            text(f, current, x, y - off + off * p, size, scaleAlpha(color, p));
        }

    static void textFade(String n, String t, float x, float y, float size, int c, float l, float r, float w, boolean fl, boolean fr) {
            textFade(FontType.resolve(n), t, x, y, size, c, l, r, w, fl, fr);
        }

    static void textFade(FontType f, String t, float x, float y, float size, int c, float l, float r, float w, boolean fl, boolean fr) {
            textFade(f, t, x, y, size, c, l, r, w, fl ? 1 : 0, fr ? 1 : 0);
        }

    static void textFade(String n, String t, float x, float y, float size, int c, float l, float r, float w, float ls, float rs) {
            textFade(FontType.resolve(n), t, x, y, size, c, l, r, w, ls, rs);
        }

    static void textFade(FontType f, String t, float x, float y, float size, int c, float l, float r, float w, float ls, float rs) {
            RenderImages.barrier();
            MsdfIconRenderer.getInstance().draw(f == null ? FontType.DEFAULT : f, NameProtect.replace(t), x, y, size, c, c, c, c, 0, x, y, ls > 0.001f, rs > 0.001f, l, r, w, ls, rs);
        }

    static float textWidth(String n, String t, float s) {
            return textWidth(FontType.resolve(n), t, s);
        }

    static float textWidth(FontType f, String t, float s) {
            return MsdfIconRenderer.getInstance().width(f == null ? FontType.DEFAULT : f, NameProtect.replace(t), s);
        }

    static float textHeight(FontType f, String t, float s) {
            return f == null || t == null || t.isEmpty() || s <= 0 ? 0 : s;
        }

    static Render2D.TextVisualBounds textVisualBounds(FontType f, String t, float s) {
            var b = MsdfIconRenderer.getInstance().visualBounds(f == null ? FontType.DEFAULT : f, NameProtect.replace(t), s);
            return b.empty() ? Render2D.TextVisualBounds.EMPTY : new Render2D.TextVisualBounds(b.minX(), b.minY(), b.maxX(), b.maxY());
        }

    static float textGlyphPositionX(FontType f, String t, int idx, float s) {
            if (t == null || idx <= 0) return 0;
            return textWidth(f, t.substring(0, Math.min(idx, t.length())), s);
        }

    static void warmupText(String n, String t, float s) {
            warmupText(FontType.resolve(n), t, s);
        }

    static void warmupText(FontType f, String t, float s) {
            MsdfIconRenderer.getInstance().glyphLayout(f == null ? FontType.DEFAULT : f, NameProtect.replace(t), s);
        }

    private static int scaleAlpha(int c, float m) {
            int a = (c >>> 24) & 255;
            return (c & 0x00FFFFFF) | (Math.round(a * Math.max(0, Math.min(1, m))) << 24);
        }
}
