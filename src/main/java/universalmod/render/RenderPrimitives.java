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
import java.util.ArrayDeque;
import java.util.Deque;

final class RenderPrimitives {
    private RenderPrimitives() {
    }

    static void hudSurface(float x, float y, float width, float height, float radius,
                           float alpha, float smoothness, float darkness) {
        RenderImages.barrier();
        universalmod.render.hud.HudSurfaceRenderer.getInstance().enqueue(
                new universalmod.render.hud.HudSurface(x, y, width, height, radius, alpha, smoothness, darkness));
    }

    static void hudChrome(float x, float y, float w, float h, float radius,
                          float alpha, float smoothness, float darkness) {

        hudSurface(x, y, w, h, radius, alpha, smoothness, darkness);
    }

    static void blur(float x, float y, float w, float h, float radius) {
            blur(x, y, w, h, radius, 16, 1, ColorUtil.WHITE);
        }

    static void blur(float x, float y, float w, float h, float radius, float blurRadius) {
            blur(x, y, w, h, radius, blurRadius, 1, ColorUtil.WHITE);
        }

    static void blur(float x, float y, float w, float h, float radius, float blurRadius, float smoothness, int color) {
            RenderImages.barrier();
            BlurFramebuffer.getInstance().enqueue(new BlurPass(x, y, w, h, radius, smoothness, blurRadius).withColor(color));
        }

    static void blur(float x, float y, float w, float h, float radius, float blurRadius, float smoothness, int tl, int tr, int br, int bl) {
            RenderImages.barrier();
            BlurFramebuffer.getInstance().enqueue(new BlurPass(x, y, w, h, radius, smoothness, blurRadius).withColors(tl, tr, br, bl));
        }

    static void blur(BlurPass b) {
            RenderImages.barrier();
            BlurFramebuffer.getInstance().enqueue(b);
        }

    static void blur(DrawContext g, float x, float y, float w, float h, float r, float blurRadius) {
            Render2D.beginFrame(g);
            blur(x, y, w, h, r, blurRadius);
            Render2D.flush();
        }

    static void glass(float x, float y, float w, float h, float[] r, int color, float ga, float fp, int fc, float ba, boolean fi, float fm, float ds, float squirt, float z) {
            RenderImages.barrier();
            GlassRenderer.getInstance().enqueue(new BuiltGlass(x, y, w, h, r, color, ga, fp, fc, ba, fi, fm, ds, squirt, z));
        }

    static void glass(float x, float y, float w, float h, float rtl, float rtr, float rbr, float rbl, int color, float ga, float fp, int fc, float ba, boolean fi, float fm, float ds, float squirt, float z) {
            RenderImages.barrier();
            GlassRenderer.getInstance().enqueue(new BuiltGlass(x, y, w, h, rtl, rtr, rbr, rbl, color, ga, fp, fc, ba, fi, fm, ds, squirt, z));
        }

    static void glass(float x, float y, float w, float h, float r, int color, float ga, float fp, int fc, float ba, boolean fi, float fm, float ds, float squirt, float z) {
            glass(x, y, w, h, r, r, r, r, color, ga, fp, fc, ba, fi, fm, ds, squirt, z);
        }

    static void glass(BuiltGlass g) {
            RenderImages.barrier();
            GlassRenderer.getInstance().enqueue(g);
        }

    static void glassOutline(float x, float y, float w, float h, float[] r, float thickness, int color, float ga, float fp, int fc, float ba, boolean fi, float fm, float ds, float squirt, float z) {
            RenderImages.barrier();
            GlassOutlineRenderer.getInstance().enqueue(new BuiltGlassOutline(x, y, w, h, r, thickness, color, ga, fp, fc, ba, fi, fm, ds, squirt, z));
        }

    static void glassOutline(float x, float y, float w, float h, float rtl, float rtr, float rbr, float rbl, float thickness, int color, float ga, float fp, int fc, float ba, boolean fi, float fm, float ds, float squirt, float z) {
            RenderImages.barrier();
            GlassOutlineRenderer.getInstance().enqueue(new BuiltGlassOutline(x, y, w, h, rtl, rtr, rbr, rbl, thickness, color, ga, fp, fc, ba, fi, fm, ds, squirt, BuiltGlassOutline.DEFAULT_SMOOTHNESS, z));
        }

    static void glassOutline(float x, float y, float w, float h, float r, float thickness, int color, float ga, float fp, int fc, float ba, boolean fi, float fm, float ds, float squirt, float z) {
            glassOutline(x, y, w, h, r, r, r, r, thickness, color, ga, fp, fc, ba, fi, fm, ds, squirt, z);
        }

    static void glassOutline(float x, float y, float w, float h, float r, float thickness, int color, float ga, float fp, int fc, float ba, boolean fi, float fm, float ds, float squirt, float z, float shinePhase) {
        RenderImages.barrier();
        GlassOutlineRenderer.getInstance().enqueue(new BuiltGlassOutline(x, y, w, h, r, r, r, r,
                thickness, color, ga, fp, fc, ba, fi, fm, ds, squirt,
                BuiltGlassOutline.DEFAULT_SMOOTHNESS, z, shinePhase));
    }

    static void glass(universalmod.render.glass.GlassQuad glass) {
        RenderImages.barrier();
        GlassRenderer.getInstance().enqueue(glass);
    }

    static void glassOutline(universalmod.render.glass.GlassOutline outline) {
        RenderImages.barrier();
        GlassOutlineRenderer.getInstance().enqueue(outline);
    }

    static void glassOutline(BuiltGlassOutline o) {
            RenderImages.barrier();
            GlassOutlineRenderer.getInstance().enqueue(o);
        }

    static void rect(float x, float y, float w, float h, int c) {
            rect(x, y, w, h, 0, c);
        }

    static void rect(float x, float y, float w, float h, float r, int c) {
            RenderImages.barrier();
            DefaultRectangleRenderer.getInstance().enqueue(new BuiltRectangle(x, y, w, h, r, c));
        }

    static void rect(float x, float y, float w, float h, float rtl, float rtr, float rbr, float rbl, int c) {
            RenderImages.barrier();
            DefaultRectangleRenderer.getInstance().enqueue(new BuiltRectangle(x, y, w, h, rtl, rtr, rbr, rbl, c));
        }

    static void rect(float x, float y, float w, float h, float r, int tl, int tr, int br, int bl) {
            rect(x, y, w, h, r, r, r, r, tl, tr, br, bl);
        }

    static void rect(float x, float y, float w, float h, float rtl, float rtr, float rbr, float rbl, int tl, int tr, int br, int bl) {
            RenderImages.barrier();
            DefaultRectangleRenderer.getInstance().enqueue(new BuiltRectangle(x, y, w, h, rtl, rtr, rbr, rbl, tl, tr, br, bl, BuiltRectangle.DEFAULT_SMOOTHNESS));
        }

    static void rect(DrawContext g, float x, float y, float w, float h, float r, int c) {
            Render2D.beginFrame(g);
            rect(x, y, w, h, r, c);
            Render2D.flush();
        }

    static void rect(BuiltRectangle r) {
            DefaultRectangleRenderer.getInstance().enqueue(r);
        }

    static void outline(float x, float y, float w, float h, float r, float t, int c) {
            RenderImages.barrier();
            DefaultOutlineRenderer.getInstance().enqueue(new BuiltOutline(x, y, w, h, r, t, c));
        }

    static void outline(float x, float y, float w, float h, float rtl, float rtr, float rbr, float rbl, float t, int c) {
            RenderImages.barrier();
            DefaultOutlineRenderer.getInstance().enqueue(new BuiltOutline(x, y, w, h, rtl, rtr, rbr, rbl, t, c));
        }

    static void outline(float x, float y, float w, float h, float r, float t, int tl, int tr, int br, int bl) {
            outline(x, y, w, h, r, r, r, r, t, tl, tr, br, bl);
        }

    static void outline(float x, float y, float w, float h, float rtl, float rtr, float rbr, float rbl, float t, int tl, int tr, int br, int bl) {
            RenderImages.barrier();
            DefaultOutlineRenderer.getInstance().enqueue(new BuiltOutline(x, y, w, h, rtl, rtr, rbr, rbl, t, tl, tr, br, bl, BuiltOutline.DEFAULT_SMOOTHNESS));
        }

    static void outline(DrawContext g, float x, float y, float w, float h, float r, float t, int c) {
            Render2D.beginFrame(g);
            outline(x, y, w, h, r, t, c);
            Render2D.flush();
        }

    static void outline(BuiltOutline o) {
            RenderImages.barrier();
            DefaultOutlineRenderer.getInstance().enqueue(o);
        }
}
