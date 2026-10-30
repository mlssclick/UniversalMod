package universalmod.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.entry.RegistryEntry;
import org.joml.Matrix4f;
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
import universalmod.render.shape.RectangleShape;
import universalmod.render.shape.RectangleRenderer;
import universalmod.render.shape.OutlineShape;
import universalmod.render.shape.OutlineRenderer;

import java.util.ArrayDeque;
import java.util.Deque;

public final class Render2D {
    @FunctionalInterface
    public interface PointProjector {
        ProjectedPoint project(float x, float y);
    }

    public record ProjectedPoint(float x, float y) {
    }

    public record ProjectedRect(float x, float y, float width, float height) {
    }

    public record TextVisualBounds(float minX, float minY, float maxX, float maxY) {
        public static final TextVisualBounds EMPTY = new TextVisualBounds(0, 0, 0, 0);

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

    private static final PointProjector IDENTITY_PROJECTOR = ProjectedPoint::new;
    private static final Deque<Matrix4f> PROJECTION_OVERRIDES = new ArrayDeque<>();
    private static final Deque<PointProjector> POINT_PROJECTORS = new ArrayDeque<>();
    private static DrawContext currentGraphics;

    private Render2D() {
    }

    public static int getFixedScaledWidth() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc == null || mc.getWindow() == null ? 1 : Math.max(1, (int) Math.ceil(mc.getWindow().getWidth() / Render2DCoordinateSpace.designGuiScale()));
    }

    public static int getFixedScaledHeight() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc == null || mc.getWindow() == null ? 1 : Math.max(1, (int) Math.ceil(mc.getWindow().getHeight() / Render2DCoordinateSpace.designGuiScale()));
    }

    public static float guiToFixed(float v) {
        float s = Render2DCoordinateSpace.guiIndependentScale();
        return Math.abs(s) <= .0001f ? v : v / s;
    }

    public static double guiToFixed(double v) {
        float s = Render2DCoordinateSpace.guiIndependentScale();
        return Math.abs(s) <= .0001f ? v : v / s;
    }

    public static float fixedToGui(float v) {
        return v * Render2DCoordinateSpace.guiIndependentScale();
    }

    public static double fixedToGui(double v) {
        return v * Render2DCoordinateSpace.guiIndependentScale();
    }

    public static void withProjection(Matrix4f projection, PointProjector projector, Runnable block) {
        Matrix4f p = projection == null ? new Matrix4f() : new Matrix4f(projection);
        PROJECTION_OVERRIDES.addLast(p);
        POINT_PROJECTORS.addLast(projector == null ? IDENTITY_PROJECTOR : projector);
        Render2DCoordinateSpace.push(p);
        try {
            block.run();
        } finally {
            Render2DCoordinateSpace.pop();
            PROJECTION_OVERRIDES.removeLast();
            POINT_PROJECTORS.removeLast();
        }
    }

    public static void withCurrentMatrix(Runnable block) {
        if (currentGraphics == null) {
            block.run();
            return;
        }
        Matrix4f m = new Matrix4f(currentGraphics.getMatrices().peek().getPositionMatrix());
        float s = Render2DCoordinateSpace.guiIndependentScale();
        m.scale(s, s, 1);
        Render2DCoordinateSpace.push(m);
        try {
            block.run();
        } finally {
            Render2DCoordinateSpace.pop();
        }
    }

    public static void pushProjection(PointProjector projector) {
        POINT_PROJECTORS.addLast(projector == null ? IDENTITY_PROJECTOR : projector);
    }

    public static void popProjection() {
        if (!POINT_PROJECTORS.isEmpty()) POINT_PROJECTORS.removeLast();
    }

    public static PointProjector getCurrentProjector() {
        return POINT_PROJECTORS.peekLast();
    }

    public static boolean hasProjectionOverride() {
        return !POINT_PROJECTORS.isEmpty();
    }

    public static ProjectedRect projectRect(float x, float y, float w, float h) {
        PointProjector p = getCurrentProjector();
        if (p == null) return new ProjectedRect(x, y, w, h);
        ProjectedPoint[] pts = {p.project(x, y), p.project(x + w, y), p.project(x + w, y + h), p.project(x, y + h)};
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        boolean ok = false;
        for (ProjectedPoint pt : pts) {
            if (pt == null) continue;
            ok = true;
            minX = Math.min(minX, pt.x());
            minY = Math.min(minY, pt.y());
            maxX = Math.max(maxX, pt.x());
            maxY = Math.max(maxY, pt.y());
        }
        return ok ? new ProjectedRect(minX, minY, Math.max(0, maxX - minX), Math.max(0, maxY - minY)) : null;
    }

    public static void beginFrame(DrawContext graphics) {
        currentGraphics = graphics;
        Render2DCoordinateSpace.beginFrame(graphics);
        ClientPipelines.ensurePreloaded();
        Render2DBackends.beginFrame(graphics);
    }

    public static void flush() {
        Render2DBackends.flush();
        Render2DCoordinateSpace.endFrame();
        currentGraphics = null;
    }

    public static void renderBarrier() { RenderImages.barrier(); }

    public static DrawContext currentGraphics() {
        return currentGraphics;
    }
    public static void hudSurface(float x, float y, float width, float height, float radius,
                                  float alpha, float smoothness, float darkness) {
        RenderPrimitives.hudSurface(x, y, width, height, radius, alpha, smoothness, darkness);
    }

    public static void hudChrome(float x, float y, float w, float h, float radius, float alpha, float smoothness, float darkness) {
        RenderPrimitives.hudChrome(x, y, w, h, radius, alpha, smoothness, darkness);
    }
    public static void blur(float x, float y, float w, float h, float radius) {
        RenderPrimitives.blur(x, y, w, h, radius);
    }
    public static void blur(float x, float y, float w, float h, float radius, float blurRadius) {
        RenderPrimitives.blur(x, y, w, h, radius, blurRadius);
    }
    public static void blur(float x, float y, float w, float h, float radius, float blurRadius, float smoothness, int color) {
        RenderPrimitives.blur(x, y, w, h, radius, blurRadius, smoothness, color);
    }
    public static void blur(float x, float y, float w, float h, float radius, float blurRadius, float smoothness, int tl, int tr, int br, int bl) {
        RenderPrimitives.blur(x, y, w, h, radius, blurRadius, smoothness, tl, tr, br, bl);
    }

    public static void blurBatch(java.util.List<BlurPass> rectangles) {
        RenderImages.barrier();
        BlurFramebuffer.getInstance().drawBatch(currentGraphics, rectangles);
    }

    public static void rectangleBatch(java.util.List<RectangleShape> shapes) {
        RenderImages.barrier();
        RectangleRenderer.getInstance().beginFrame(currentGraphics);
        RectangleRenderer.getInstance().drawBatch(shapes);
    }

    public static void outlineBatch(java.util.List<OutlineShape> shapes) {
        RenderImages.barrier();
        OutlineRenderer.getInstance().beginFrame(currentGraphics);
        OutlineRenderer.getInstance().drawBatch(shapes);
    }

    public static void blur(BlurPass b) {
        RenderPrimitives.blur(b);
    }
    public static void blur(DrawContext g, float x, float y, float w, float h, float r, float blurRadius) {
        RenderPrimitives.blur(g, x, y, w, h, r, blurRadius);
    }
    public static void glass(float x, float y, float w, float h, float[] r, int color, float ga, float fp, int fc, float ba, boolean fi, float fm, float ds, float squirt, float z) {
        RenderPrimitives.glass(x, y, w, h, r, color, ga, fp, fc, ba, fi, fm, ds, squirt, z);
    }
    public static void glass(float x, float y, float w, float h, float rtl, float rtr, float rbr, float rbl, int color, float ga, float fp, int fc, float ba, boolean fi, float fm, float ds, float squirt, float z) {
        RenderPrimitives.glass(x, y, w, h, rtl, rtr, rbr, rbl, color, ga, fp, fc, ba, fi, fm, ds, squirt, z);
    }
    public static void glass(float x, float y, float w, float h, float r, int color, float ga, float fp, int fc, float ba, boolean fi, float fm, float ds, float squirt, float z) {
        RenderPrimitives.glass(x, y, w, h, r, color, ga, fp, fc, ba, fi, fm, ds, squirt, z);
    }
    public static void glass(BuiltGlass g) {
        RenderPrimitives.glass(g);
    }
    public static void glassOutline(float x, float y, float w, float h, float[] r, float thickness, int color, float ga, float fp, int fc, float ba, boolean fi, float fm, float ds, float squirt, float z) {
        RenderPrimitives.glassOutline(x, y, w, h, r, thickness, color, ga, fp, fc, ba, fi, fm, ds, squirt, z);
    }
    public static void glassOutline(float x, float y, float w, float h, float rtl, float rtr, float rbr, float rbl, float thickness, int color, float ga, float fp, int fc, float ba, boolean fi, float fm, float ds, float squirt, float z) {
        RenderPrimitives.glassOutline(x, y, w, h, rtl, rtr, rbr, rbl, thickness, color, ga, fp, fc, ba, fi, fm, ds, squirt, z);
    }
    public static void glassOutline(float x, float y, float w, float h, float r, float thickness, int color, float ga, float fp, int fc, float ba, boolean fi, float fm, float ds, float squirt, float z) {
        RenderPrimitives.glassOutline(x, y, w, h, r, thickness, color, ga, fp, fc, ba, fi, fm, ds, squirt, z);
    }
    public static void glassOutline(float x, float y, float w, float h, float r, float thickness, int color, float ga, float fp, int fc, float ba, boolean fi, float fm, float ds, float squirt, float z, float shinePhase) {
        RenderPrimitives.glassOutline(x, y, w, h, r, thickness, color, ga, fp, fc, ba, fi, fm, ds, squirt, z, shinePhase);
    }
    public static void glass(universalmod.render.glass.GlassQuad glass) {
        RenderPrimitives.glass(glass);
    }

    public static void glassOutline(universalmod.render.glass.GlassOutline outline) {
        RenderPrimitives.glassOutline(outline);
    }

    public static void glassOutline(BuiltGlassOutline o) {
        RenderPrimitives.glassOutline(o);
    }
    public static void rect(float x, float y, float w, float h, int c) {
        RenderPrimitives.rect(x, y, w, h, c);
    }
    public static void rect(float x, float y, float w, float h, float r, int c) {
        RenderPrimitives.rect(x, y, w, h, r, c);
    }
    public static void rect(float x, float y, float w, float h, float rtl, float rtr, float rbr, float rbl, int c) {
        RenderPrimitives.rect(x, y, w, h, rtl, rtr, rbr, rbl, c);
    }
    public static void rect(float x, float y, float w, float h, float r, int tl, int tr, int br, int bl) {
        RenderPrimitives.rect(x, y, w, h, r, tl, tr, br, bl);
    }
    public static void rect(float x, float y, float w, float h, float rtl, float rtr, float rbr, float rbl, int tl, int tr, int br, int bl) {
        RenderPrimitives.rect(x, y, w, h, rtl, rtr, rbr, rbl, tl, tr, br, bl);
    }
    public static void rect(DrawContext g, float x, float y, float w, float h, float r, int c) {
        RenderPrimitives.rect(g, x, y, w, h, r, c);
    }
    public static void rect(BuiltRectangle r) {
        RenderPrimitives.rect(r);
    }
    private static final String SMOOTH_CIRCLE_TEXTURE = "ui/smooth_circle.png";

    public static void circle(float centerX, float centerY, float diameter, int color) {
        float size = Math.max(0.0F, diameter);
        if (size <= 0.0F || ((color >>> 24) & 0xFF) == 0) {
            return;
        }
        image(SMOOTH_CIRCLE_TEXTURE, centerX - size * 0.5F, centerY - size * 0.5F, size, size, 0.0F, color);
    }

    public static void capsule(float x, float y, float width, float height, int color) {
        float w = Math.max(0.0F, width);
        float h = Math.max(0.0F, height);
        if (w <= 0.0F || h <= 0.0F || ((color >>> 24) & 0xFF) == 0) {
            return;
        }
        if (Math.abs(w - h) <= 0.0001F) {
            circle(x + w * 0.5F, y + h * 0.5F, w, color);
            return;
        }

        final float centerU0 = 0.4921875F;
        final float centerU1 = 0.5078125F;
        if (w > h) {
            float r = h * 0.5F;
            imageUv(SMOOTH_CIRCLE_TEXTURE, x, y, r, h, 0.0F, 0.0F, 0.0F, 0.5F, 1.0F, color);
            float middle = Math.max(0.0F, w - h);
            if (middle > 0.0F) {
                imageUv(SMOOTH_CIRCLE_TEXTURE, x + r, y, middle, h, 0.0F, centerU0, 0.0F, centerU1, 1.0F, color);
            }
            imageUv(SMOOTH_CIRCLE_TEXTURE, x + w - r, y, r, h, 0.0F, 0.5F, 0.0F, 1.0F, 1.0F, color);
        } else {
            float r = w * 0.5F;
            imageUv(SMOOTH_CIRCLE_TEXTURE, x, y, w, r, 0.0F, 0.0F, 0.0F, 1.0F, 0.5F, color);
            float middle = Math.max(0.0F, h - w);
            if (middle > 0.0F) {
                imageUv(SMOOTH_CIRCLE_TEXTURE, x, y + r, w, middle, 0.0F, 0.0F, centerU0, 1.0F, centerU1, color);
            }
            imageUv(SMOOTH_CIRCLE_TEXTURE, x, y + h - r, w, r, 0.0F, 0.0F, 0.5F, 1.0F, 1.0F, color);
        }
    }

    public static void circleOutline(float centerX, float centerY, float diameter, float thickness, int color) {
        float size = Math.max(0.0F, diameter);
        outline(centerX - size * 0.5F, centerY - size * 0.5F, size, size, size * 0.5F, thickness, color);
    }
    public static void outline(float x, float y, float w, float h, float r, float t, int c) {
        RenderPrimitives.outline(x, y, w, h, r, t, c);
    }
    public static void outline(float x, float y, float w, float h, float rtl, float rtr, float rbr, float rbl, float t, int c) {
        RenderPrimitives.outline(x, y, w, h, rtl, rtr, rbr, rbl, t, c);
    }
    public static void outline(float x, float y, float w, float h, float r, float t, int tl, int tr, int br, int bl) {
        RenderPrimitives.outline(x, y, w, h, r, t, tl, tr, br, bl);
    }
    public static void outline(float x, float y, float w, float h, float rtl, float rtr, float rbr, float rbl, float t, int tl, int tr, int br, int bl) {
        RenderPrimitives.outline(x, y, w, h, rtl, rtr, rbr, rbl, t, tl, tr, br, bl);
    }
    public static void outline(DrawContext g, float x, float y, float w, float h, float r, float t, int c) {
        RenderPrimitives.outline(g, x, y, w, h, r, t, c);
    }
    public static void outline(BuiltOutline o) {
        RenderPrimitives.outline(o);
    }

    public static void outline(OutlineShape shape) {
        OutlineRenderer.getInstance().enqueue(shape);
    }
    public static void text(String name, String text, float x, float y, float size, int color) {
        RenderText.text(name, text, x, y, size, color);
    }
    public static void text(FontType font, String text, float x, float y, float size, int color) {
        RenderText.text(font, text, x, y, size, color);
    }
    public static void text(String name, String text, float x, float y, float size, int color, float rotation, float ox, float oy) {
        RenderText.text(name, text, x, y, size, color, rotation, ox, oy);
    }
    public static void text(String name, String text, float x, float y, float size, int tl, int tr, int br, int bl) {
        RenderText.text(name, text, x, y, size, tl, tr, br, bl);
    }
    public static void text(String name, String text, float x, float y, float size, int tl, int tr, int br, int bl, float rotation, float ox, float oy) {
        RenderText.text(name, text, x, y, size, tl, tr, br, bl, rotation, ox, oy);
    }
    public static void text(FontType font, String text, float x, float y, float size, int tl, int tr, int br, int bl) {
        RenderText.text(font, text, x, y, size, tl, tr, br, bl);
    }
    public static void text(FontType font, String text, float x, float y, float size, int color, float rotation, float ox, float oy) {
        RenderText.text(font, text, x, y, size, color, rotation, ox, oy);
    }
    public static void text(FontType font, String text, float x, float y, float size, int tl, int tr, int br, int bl, float rotation, float ox, float oy) {
        RenderText.text(font, text, x, y, size, tl, tr, br, bl, rotation, ox, oy);
    }
    public static void text(DrawContext g, String name, String text, float x, float y, float size, int color) {
        RenderText.text(g, name, text, x, y, size, color);
    }
    public static void text(DrawContext g, FontType font, String text, float x, float y, float size, int color) {
        RenderText.text(g, font, text, x, y, size, color);
    }
    public static void animatedGlyphText(FontType font, String text, float x, float y, float size, int color, int first, String prevFirst, float firstProgress, int second, String prevSecond, float secondProgress, float verticalOffset) {
        RenderText.animatedGlyphText(font, text, x, y, size, color, first, prevFirst, firstProgress, second, prevSecond, secondProgress, verticalOffset);
    }
    public static void textFade(String n, String t, float x, float y, float size, int c, float l, float r, float w, boolean fl, boolean fr) {
        RenderText.textFade(n, t, x, y, size, c, l, r, w, fl, fr);
    }
    public static void textFade(FontType f, String t, float x, float y, float size, int c, float l, float r, float w, boolean fl, boolean fr) {
        RenderText.textFade(f, t, x, y, size, c, l, r, w, fl, fr);
    }
    public static void textFade(String n, String t, float x, float y, float size, int c, float l, float r, float w, float ls, float rs) {
        RenderText.textFade(n, t, x, y, size, c, l, r, w, ls, rs);
    }
    public static void textFade(FontType f, String t, float x, float y, float size, int c, float l, float r, float w, float ls, float rs) {
        RenderText.textFade(f, t, x, y, size, c, l, r, w, ls, rs);
    }
    public static float textWidth(String n, String t, float s) {
        return RenderText.textWidth(n, t, s);
    }
    public static float textWidth(FontType f, String t, float s) {
        return RenderText.textWidth(f, t, s);
    }
    public static float textHeight(FontType f, String t, float s) {
        return RenderText.textHeight(f, t, s);
    }
    public static TextVisualBounds textVisualBounds(FontType f, String t, float s) {
        return RenderText.textVisualBounds(f, t, s);
    }
    public static float textGlyphPositionX(FontType f, String t, int idx, float s) {
        return RenderText.textGlyphPositionX(f, t, idx, s);
    }
    public static void warmupText(String n, String t, float s) {
        RenderText.warmupText(n, t, s);
    }
    public static void warmupText(FontType f, String t, float s) {
        RenderText.warmupText(f, t, s);
    }
    public static void image(String t, float x, float y, float size, float r) {
        RenderImages.image(t, x, y, size, r);
    }
    public static void image(String t, float x, float y, float size, float r, int c) {
        RenderImages.image(t, x, y, size, r, c);
    }
    public static void image(String t, float x, float y, float size, float r, int... c) {
        RenderImages.image(t, x, y, size, r, c);
    }
    public static void image(String t, float x, float y, float w, float h, float r, int c) {
        RenderImages.image(t, x, y, w, h, r, c);
    }
    public static void image(String t, float x, float y, float w, float h, float r, int... c) {
        RenderImages.image(t, x, y, w, h, r, c);
    }
    public static void imageUv(String t, float x, float y, float w, float h, float r, float u0, float v0, float u1, float v1, int c) {
        RenderImages.imageUv(t, x, y, w, h, r, u0, v0, u1, v1, c);
    }
    public static void imageUv(String t, float x, float y, float w, float h, float r, float u0, float v0, float u1, float v1, int... c) {
        RenderImages.imageUv(t, x, y, w, h, r, u0, v0, u1, v1, c);
    }
    public static void imageUvNearest(String t, float x, float y, float w, float h, float r, float smooth, float u0, float v0, float u1, float v1, int c) {
        RenderImages.imageUvNearest(t, x, y, w, h, r, smooth, u0, v0, u1, v1, c);
    }
    public static void imageUvNearest(String t, float x, float y, float w, float h, float r, float smooth, float u0, float v0, float u1, float v1, int... c) {
        RenderImages.imageUvNearest(t, x, y, w, h, r, smooth, u0, v0, u1, v1, c);
    }
    public static void image(String t, float x, float y, float size, float r, float rotation, float ox, float oy, int c) {
        RenderImages.image(t, x, y, size, r, rotation, ox, oy, c);
    }
    public static void image(String t, float x, float y, float size, float r, float rotation, float ox, float oy, int... c) {
        RenderImages.image(t, x, y, size, r, rotation, ox, oy, c);
    }
    public static void effectIcon(RegistryEntry<StatusEffect> e, float x, float y, float size) {
        RenderImages.effectIcon(e, x, y, size);
    }
    public static void effectIcon(RegistryEntry<StatusEffect> e, float x, float y, float size, int c) {
        RenderImages.effectIcon(e, x, y, size, c);
    }
    public static void effectIcon(StatusEffectInstance i, float x, float y, float size) {
        RenderImages.effectIcon(i, x, y, size);
    }
    public static void effectIcon(StatusEffectInstance i, float x, float y, float size, int c) {
        RenderImages.effectIcon(i, x, y, size, c);
    }

    public static void pushScissor(DrawContext graphics, float x, float y, float width, float height) {
        if (graphics == null) return;
        ProjectedRect area = hasProjectionOverride()
                ? projectRect(x, y, width, height)
                : new ProjectedRect(x, y, width, height);
        if (area == null) area = new ProjectedRect(0, 0, 0, 0);
        RenderScissorStack.push(graphics, area.x(), area.y(), area.width(), area.height());
    }

    public static void popScissor(DrawContext graphics) {
        RenderScissorStack.pop(graphics);
    }

    public static void close() {
        currentGraphics = null;
        PROJECTION_OVERRIDES.clear();
        POINT_PROJECTORS.clear();
        Render2DCoordinateSpace.endFrame();
        ScissorUtil.clear();
        Render2DBackends.closeAll();
        closeSafely("item renderer", CustomItemRenderer::closeInstance);
    }

    private static void closeSafely(String n, Runnable a) {
        try {
            a.run();
        } catch (Throwable t) {

        }
    }

    private static BlurFramebuffer blur() {
        return BlurFramebuffer.getInstance();
    }

    private static GlassRenderer glass() {
        return GlassRenderer.getInstance();
    }

    private static HudChromeRenderer hudChrome() {
        return HudChromeRenderer.getInstance();
    }

    private static GlassOutlineRenderer glassOutline() {
        return GlassOutlineRenderer.getInstance();
    }

    private static DefaultRectangleRenderer rectangle() {
        return DefaultRectangleRenderer.getInstance();
    }

    private static DefaultOutlineRenderer outline() {
        return DefaultOutlineRenderer.getInstance();
    }

    private static MsdfIconRenderer msdfIcon() {
        return MsdfIconRenderer.getInstance();
    }

    private static ImageRenderer image() {
        return ImageRenderer.getInstance();
    }

    private static EffectIconRenderer effectIcon() {
        return EffectIconRenderer.getInstance();
    }
}
