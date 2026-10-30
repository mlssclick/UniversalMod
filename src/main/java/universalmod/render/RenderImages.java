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

final class RenderImages {
    private RenderImages() {
    }

    static void image(String t, float x, float y, float size, float r) {
            ImageRenderer.getInstance().enqueue(new BuiltImage(t, x, y, size, r));
        }

    static void image(String t, float x, float y, float size, float r, int c) {
            ImageRenderer.getInstance().enqueue(new BuiltImage(t, x, y, size, r, c));
        }

    static void image(String t, float x, float y, float size, float r, int... c) {
            ImageRenderer.getInstance().enqueue(new BuiltImage(t, x, y, size, r).withColors(c));
        }

    static void image(String t, float x, float y, float w, float h, float r, int c) {
            ImageRenderer.getInstance().enqueue(new BuiltImage(t, x, y, w, h, r, c));
        }

    static void image(String t, float x, float y, float w, float h, float r, int... c) {
            ImageRenderer.getInstance().enqueue(new BuiltImage(t, x, y, w, h, r).withColors(c));
        }

    static void imageUv(String t, float x, float y, float w, float h, float r, float u0, float v0, float u1, float v1, int c) {
            ImageRenderer.getInstance().enqueue(new BuiltImage(t, x, y, w, h, r, c).withUv(u0, v0, u1, v1));
        }

    static void imageUv(String t, float x, float y, float w, float h, float r, float u0, float v0, float u1, float v1, int... c) {
            ImageRenderer.getInstance().enqueue(new BuiltImage(t, x, y, w, h, r).withColors(c).withUv(u0, v0, u1, v1));
        }

    static void imageUvNearest(String t, float x, float y, float w, float h, float r, float smooth, float u0, float v0, float u1, float v1, int c) {
            ImageRenderer.getInstance().enqueue(new BuiltImage(t, x, y, w, h, r, c).withUv(u0, v0, u1, v1).withSmoothness(smooth).withNearestFilter());
        }

    static void imageUvNearest(String t, float x, float y, float w, float h, float r, float smooth, float u0, float v0, float u1, float v1, int... c) {
            ImageRenderer.getInstance().enqueue(new BuiltImage(t, x, y, w, h, r).withColors(c).withUv(u0, v0, u1, v1).withSmoothness(smooth).withNearestFilter());
        }

    static void image(String t, float x, float y, float size, float r, float rotation, float ox, float oy, int c) {
            ImageRenderer.getInstance().enqueue(new BuiltImage(t, x, y, size, r, c).withRotation(rotation, ox, oy));
        }

    static void image(String t, float x, float y, float size, float r, float rotation, float ox, float oy, int... c) {
            ImageRenderer.getInstance().enqueue(new BuiltImage(t, x, y, size, r).withColors(c).withRotation(rotation, ox, oy));
        }

    static void effectIcon(RegistryEntry<StatusEffect> e, float x, float y, float size) {
            EffectIconRenderer.getInstance().enqueue(new BuiltEffectIcon(e, x, y, size));
        }

    static void effectIcon(RegistryEntry<StatusEffect> e, float x, float y, float size, int c) {
            EffectIconRenderer.getInstance().enqueue(new BuiltEffectIcon(e, x, y, size, c));
        }

    static void effectIcon(StatusEffectInstance i, float x, float y, float size) {
            if (i != null && i.shouldShowIcon()) EffectIconRenderer.getInstance().enqueue(new BuiltEffectIcon(i, x, y, size));
        }

    static void effectIcon(StatusEffectInstance i, float x, float y, float size, int c) {
            if (i != null && i.shouldShowIcon()) EffectIconRenderer.getInstance().enqueue(new BuiltEffectIcon(i, x, y, size, c));
        }

    static void barrier() {
            ImageRenderer.getInstance().barrier();
            EffectIconRenderer.getInstance().barrier();
        }
}
