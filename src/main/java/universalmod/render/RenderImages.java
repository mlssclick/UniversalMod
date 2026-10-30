package universalmod.render;

import universalmod.render.effect.EffectIcon;
import universalmod.render.effect.EffectIconRenderer;
import universalmod.render.image.ImageCommand;
import universalmod.render.image.ImageRenderer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

final class RenderImages {
    private RenderImages() {
    }

    static void image(String texture, float x, float y, float size, float radius) {
            ImageRenderer.getInstance().enqueue(new ImageCommand(texture, x, y, size, radius));
        }

    static void image(String texture, float x, float y, float size, float radius, int color) {
            ImageRenderer.getInstance().enqueue(new ImageCommand(texture, x, y, size, radius, color));
        }

    static void image(String texture, float x, float y, float size, float radius, int... colors) {
            ImageRenderer.getInstance().enqueue(new ImageCommand(texture, x, y, size, radius).withColors(colors));
        }

    static void image(String texture, float x, float y, float width, float height, float radius, int color) {
            ImageRenderer.getInstance().enqueue(new ImageCommand(texture, x, y, width, height, radius, color));
        }

    static void image(String texture, float x, float y, float width, float height, float radius, int... colors) {
            ImageRenderer.getInstance().enqueue(new ImageCommand(texture, x, y, width, height, radius).withColors(colors));
        }

    static void imageUv(String texture, float x, float y, float width, float height, float radius, float u0, float v0, float u1, float v1, int color) {
            ImageRenderer.getInstance().enqueue(new ImageCommand(texture, x, y, width, height, radius, color).withUv(u0, v0, u1, v1));
        }

    static void imageUv(String texture, float x, float y, float width, float height, float radius, float u0, float v0, float u1, float v1, int... colors) {
            ImageRenderer.getInstance().enqueue(new ImageCommand(texture, x, y, width, height, radius).withUv(u0, v0, u1, v1).withColors(colors));
        }

    static void imageUvNearest(String texture, float x, float y, float width, float height, float radius, float smoothness, float u0, float v0, float u1, float v1, int color) {
            ImageRenderer.getInstance().enqueue(new ImageCommand(texture, x, y, width, height, radius, color)
                    .withSmoothness(smoothness)
                    .withNearestFilter()
                    .withUv(u0, v0, u1, v1));
        }

    static void imageUvNearest(String texture, float x, float y, float width, float height, float radius, float smoothness, float u0, float v0, float u1, float v1, int... colors) {
            ImageRenderer.getInstance().enqueue(new ImageCommand(texture, x, y, width, height, radius)
                    .withSmoothness(smoothness)
                    .withNearestFilter()
                    .withUv(u0, v0, u1, v1)
                    .withColors(colors));
        }

    static void image(String texture, float x, float y, float size, float radius, float rotationDegrees, float originX, float originY, int color) {
            ImageRenderer.getInstance().enqueue(new ImageCommand(texture, x, y, size, radius, color).withRotation(rotationDegrees, originX, originY));
        }

    static void image(String texture, float x, float y, float size, float radius, float rotationDegrees, float originX, float originY, int... colors) {
            ImageRenderer.getInstance().enqueue(new ImageCommand(texture, x, y, size, radius).withColors(colors).withRotation(rotationDegrees, originX, originY));
        }

    static void effectIcon(Holder<MobEffect> effect, float x, float y, float size) {
            EffectIconRenderer.getInstance().enqueue(new EffectIcon(effect, x, y, size));
        }

    static void effectIcon(Holder<MobEffect> effect, float x, float y, float size, int color) {
            EffectIconRenderer.getInstance().enqueue(new EffectIcon(effect, x, y, size, color));
        }

    static void effectIcon(MobEffectInstance effect, float x, float y, float size) {
            if (effect != null && effect.showIcon()) {
                EffectIconRenderer.getInstance().enqueue(new EffectIcon(effect, x, y, size));
            }
        }

    static void effectIcon(MobEffectInstance effect, float x, float y, float size, int color) {
            if (effect != null && effect.showIcon()) {
                EffectIconRenderer.getInstance().enqueue(new EffectIcon(effect, x, y, size, color));
            }
        }
static void barrier() {
            ImageRenderer.getInstance().barrier();
            EffectIconRenderer.getInstance().barrier();
        }
}
