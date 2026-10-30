package universalmod.render;

import universalmod.render.blur.BlurFramebuffer;
import universalmod.render.blur.BlurPass;
import universalmod.render.glass.GlassQuad;
import universalmod.render.glass.GlassRenderer;
import universalmod.render.hud.HudSurface;
import universalmod.render.hud.HudSurfaceRenderer;
import universalmod.render.shape.OutlineShape;
import universalmod.render.shape.OutlineRenderer;
import universalmod.render.glass.GlassOutline;
import universalmod.render.glass.GlassOutlineRenderer;
import universalmod.render.shape.RectangleShape;
import universalmod.render.shape.RectangleRenderer;
import net.minecraft.client.gui.GuiGraphics;
import universalmod.render.color.ColorUtil;

final class RenderPrimitives {
    private RenderPrimitives() {
    }

    static void hudSurface(
                float x, float y, float width, float height, float radius,
                float alpha, float smoothness, float darkness
        ) {
            RenderImages.barrier();
            HudSurfaceRenderer.getInstance().enqueue(new HudSurface(x, y, width, height, radius, alpha, smoothness, darkness));
        }

    static void blur(float x, float y, float width, float height, float radius) {
            blur(x, y, width, height, radius, 16.0f, 1.0f, ColorUtil.WHITE);
        }

    static void blur(float x, float y, float width, float height, float radius, float blurRadius) {
            blur(x, y, width, height, radius, blurRadius, 1.0f, ColorUtil.WHITE);
        }

    static void blur(float x, float y, float width, float height, float radius, float blurRadius, float smoothness, int color) {
            RenderImages.barrier();
            BlurFramebuffer.getInstance().enqueue(new BlurPass(x, y, width, height, radius, smoothness, blurRadius).withColor(color));
        }

    static void blur(
                float x,
                float y,
                float width,
                float height,
                float radius,
                float blurRadius,
                float smoothness,
                int colorTopLeft,
                int colorTopRight,
                int colorBottomRight,
                int colorBottomLeft
        ) {
            RenderImages.barrier();
            BlurFramebuffer.getInstance().enqueue(new BlurPass(x, y, width, height, radius, smoothness, blurRadius)
                    .withColors(colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft));
        }

    static void blur(GuiGraphics graphics, float x, float y, float width, float height, float radius, float blurRadius) {
            Render2D.beginFrame(graphics);
            blur(x, y, width, height, radius, blurRadius);
            Render2D.flush();
        }

    static void blur(BlurPass blur) {
            RenderImages.barrier();
            BlurFramebuffer.getInstance().enqueue(blur);
        }

    static void glass(
                float x,
                float y,
                float width,
                float height,
                float[] radius,
                int color,
                float globalAlpha,
                float fresnelPower,
                int fresnelColor,
                float baseAlpha,
                boolean fresnelInvert,
                float fresnelMix,
                float distortStrength,
                float squirt,
                float z
        ) {
            RenderImages.barrier();
            GlassRenderer.getInstance().enqueue(new GlassQuad(
                    x,
                    y,
                    width,
                    height,
                    radius,
                    color,
                    globalAlpha,
                    fresnelPower,
                    fresnelColor,
                    baseAlpha,
                    fresnelInvert,
                    fresnelMix,
                    distortStrength,
                    squirt,
                    z
            ));
        }

    static void glass(
                float x,
                float y,
                float width,
                float height,
                float radiusTopLeft,
                float radiusTopRight,
                float radiusBottomRight,
                float radiusBottomLeft,
                int color,
                float globalAlpha,
                float fresnelPower,
                int fresnelColor,
                float baseAlpha,
                boolean fresnelInvert,
                float fresnelMix,
                float distortStrength,
                float squirt,
                float z
        ) {
            RenderImages.barrier();
            GlassRenderer.getInstance().enqueue(new GlassQuad(
                    x,
                    y,
                    width,
                    height,
                    radiusTopLeft,
                    radiusTopRight,
                    radiusBottomRight,
                    radiusBottomLeft,
                    color,
                    globalAlpha,
                    fresnelPower,
                    fresnelColor,
                    baseAlpha,
                    fresnelInvert,
                    fresnelMix,
                    distortStrength,
                    squirt,
                    z
            ));
        }

    static void glass(
                float x,
                float y,
                float width,
                float height,
                float radius,
                int color,
                float globalAlpha,
                float fresnelPower,
                int fresnelColor,
                float baseAlpha,
                boolean fresnelInvert,
                float fresnelMix,
                float distortStrength,
                float squirt,
                float z
        ) {
            RenderImages.barrier();
            GlassRenderer.getInstance().enqueue(new GlassQuad(
                    x,
                    y,
                    width,
                    height,
                    radius,
                    radius,
                    radius,
                    radius,
                    color,
                    globalAlpha,
                    fresnelPower,
                    fresnelColor,
                    baseAlpha,
                    fresnelInvert,
                    fresnelMix,
                    distortStrength,
                    squirt,
                    z
            ));
        }

    static void glass(GlassQuad glass) {
            RenderImages.barrier();
            GlassRenderer.getInstance().enqueue(glass);
        }

    static void glassOutline(
                float x,
                float y,
                float width,
                float height,
                float[] radius,
                float thickness,
                int color,
                float globalAlpha,
                float fresnelPower,
                int fresnelColor,
                float baseAlpha,
                boolean fresnelInvert,
                float fresnelMix,
                float distortStrength,
                float squirt,
                float z
        ) {
            RenderImages.barrier();
            GlassOutlineRenderer.getInstance().enqueue(new GlassOutline(
                    x,
                    y,
                    width,
                    height,
                    radius,
                    thickness,
                    color,
                    globalAlpha,
                    fresnelPower,
                    fresnelColor,
                    baseAlpha,
                    fresnelInvert,
                    fresnelMix,
                    distortStrength,
                    squirt,
                    z
            ));
        }

    static void glassOutline(
                float x,
                float y,
                float width,
                float height,
                float radiusTopLeft,
                float radiusTopRight,
                float radiusBottomRight,
                float radiusBottomLeft,
                float thickness,
                int color,
                float globalAlpha,
                float fresnelPower,
                int fresnelColor,
                float baseAlpha,
                boolean fresnelInvert,
                float fresnelMix,
                float distortStrength,
                float squirt,
                float z
        ) {
            RenderImages.barrier();
            GlassOutlineRenderer.getInstance().enqueue(new GlassOutline(
                    x,
                    y,
                    width,
                    height,
                    radiusTopLeft,
                    radiusTopRight,
                    radiusBottomRight,
                    radiusBottomLeft,
                    thickness,
                    color,
                    globalAlpha,
                    fresnelPower,
                    fresnelColor,
                    baseAlpha,
                    fresnelInvert,
                    fresnelMix,
                    distortStrength,
                    squirt,
                    GlassOutline.DEFAULT_SMOOTHNESS,
                    z
            ));
        }

    static void glassOutline(
                float x,
                float y,
                float width,
                float height,
                float radius,
                float thickness,
                int color,
                float globalAlpha,
                float fresnelPower,
                int fresnelColor,
                float baseAlpha,
                boolean fresnelInvert,
                float fresnelMix,
                float distortStrength,
                float squirt,
                float z
        ) {
            RenderImages.barrier();
            GlassOutlineRenderer.getInstance().enqueue(new GlassOutline(
                    x,
                    y,
                    width,
                    height,
                    radius,
                    radius,
                    radius,
                    radius,
                    thickness,
                    color,
                    globalAlpha,
                    fresnelPower,
                    fresnelColor,
                    baseAlpha,
                    fresnelInvert,
                    fresnelMix,
                    distortStrength,
                    squirt,
                    GlassOutline.DEFAULT_SMOOTHNESS,
                    z
            ));
        }

    static void glassOutline(GlassOutline outline) {
            RenderImages.barrier();
            GlassOutlineRenderer.getInstance().enqueue(outline);
        }

    static void rect(float x, float y, float width, float height, int color) {
            rect(x, y, width, height, 0.0f, color);
        }

    static void rect(float x, float y, float width, float height, float radius, int color) {
            RenderImages.barrier();
            RectangleRenderer.getInstance().enqueue(new RectangleShape(x, y, width, height, radius, color));
        }

    static void rect(
                float x,
                float y,
                float width,
                float height,
                float radiusTopLeft,
                float radiusTopRight,
                float radiusBottomRight,
                float radiusBottomLeft,
                int color
        ) {
            RenderImages.barrier();
            RectangleRenderer.getInstance().enqueue(new RectangleShape(
                    x,
                    y,
                    width,
                    height,
                    radiusTopLeft,
                    radiusTopRight,
                    radiusBottomRight,
                    radiusBottomLeft,
                    color
            ));
        }

    static void rect(
                float x,
                float y,
                float width,
                float height,
                float radius,
                int colorTopLeft,
                int colorTopRight,
                int colorBottomRight,
                int colorBottomLeft
        ) {
            rect(x, y, width, height, radius, radius, radius, radius, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft);
        }

    static void rect(
                float x,
                float y,
                float width,
                float height,
                float radiusTopLeft,
                float radiusTopRight,
                float radiusBottomRight,
                float radiusBottomLeft,
                int colorTopLeft,
                int colorTopRight,
                int colorBottomRight,
                int colorBottomLeft
        ) {
            RenderImages.barrier();
            RectangleRenderer.getInstance().enqueue(new RectangleShape(
                    x,
                    y,
                    width,
                    height,
                    radiusTopLeft,
                    radiusTopRight,
                    radiusBottomRight,
                    radiusBottomLeft,
                    colorTopLeft,
                    colorTopRight,
                    colorBottomRight,
                    colorBottomLeft,
                    RectangleShape.DEFAULT_SMOOTHNESS
            ));
        }

    static void rect(GuiGraphics graphics, float x, float y, float width, float height, float radius, int color) {
            Render2D.beginFrame(graphics);
            rect(x, y, width, height, radius, color);
            Render2D.flush();
        }

    static void rect(RectangleShape rectangle) {
            RectangleRenderer.getInstance().enqueue(rectangle);
        }

    static void outline(float x, float y, float width, float height, float radius, float thickness, int color) {
            RenderImages.barrier();
            OutlineRenderer.getInstance().enqueue(new OutlineShape(x, y, width, height, radius, thickness, color));
        }

    static void outline(
                float x,
                float y,
                float width,
                float height,
                float radiusTopLeft,
                float radiusTopRight,
                float radiusBottomRight,
                float radiusBottomLeft,
                float thickness,
                int color
        ) {
            RenderImages.barrier();
            OutlineRenderer.getInstance().enqueue(new OutlineShape(
                    x,
                    y,
                    width,
                    height,
                    radiusTopLeft,
                    radiusTopRight,
                    radiusBottomRight,
                    radiusBottomLeft,
                    thickness,
                    color
            ));
        }

    static void outline(
                float x,
                float y,
                float width,
                float height,
                float radius,
                float thickness,
                int colorTopLeft,
                int colorTopRight,
                int colorBottomRight,
                int colorBottomLeft
        ) {
            outline(x, y, width, height, radius, radius, radius, radius, thickness, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft);
        }

    static void outline(
                float x,
                float y,
                float width,
                float height,
                float radiusTopLeft,
                float radiusTopRight,
                float radiusBottomRight,
                float radiusBottomLeft,
                float thickness,
                int colorTopLeft,
                int colorTopRight,
                int colorBottomRight,
                int colorBottomLeft
        ) {
            RenderImages.barrier();
            OutlineRenderer.getInstance().enqueue(new OutlineShape(
                    x,
                    y,
                    width,
                    height,
                    radiusTopLeft,
                    radiusTopRight,
                    radiusBottomRight,
                    radiusBottomLeft,
                    thickness,
                    colorTopLeft,
                    colorTopRight,
                    colorBottomRight,
                    colorBottomLeft,
                    OutlineShape.DEFAULT_SMOOTHNESS
            ));
        }

    static void outline(GuiGraphics graphics, float x, float y, float width, float height, float radius, float thickness, int color) {
            Render2D.beginFrame(graphics);
            outline(x, y, width, height, radius, thickness, color);
            Render2D.flush();
        }

    static void outline(OutlineShape outline) {
            RenderImages.barrier();
            OutlineRenderer.getInstance().enqueue(outline);
        }
}
