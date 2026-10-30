package universalmod.hud.element;

import universalmod.module.render.Hud;
import universalmod.render.Render2D;
import universalmod.render.blur.BlurPass;

final class HudRenderCompat {
    private HudRenderCompat() {
    }

    static void background(float x, float y, float width, float height, float radius, float blurRadius, float smoothness, int color) {
        Hud.renderHudBackground(x, y, width, height, radius, blurRadius, smoothness, color);
    }

    static void header(float x, float y, float width, float height, float radius, float blurRadius, float smoothness, int color) {
        Hud.renderHudHeader(x, y, width, height, radius, blurRadius, smoothness, color);
    }

    static void background(BlurPass blur) {
        if (blur == null) {
            return;
        }
        Hud.renderHudBackground(blur);
    }

    static void glow(String texture, float x, float y, float width, float height, float radius, int color) {
        Hud.renderHudGlow(texture, x, y, width, height, radius, color);
    }
}
