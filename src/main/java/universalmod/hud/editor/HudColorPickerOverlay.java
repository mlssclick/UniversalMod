package universalmod.hud.editor;

import net.minecraft.client.gui.DrawContext;
import universalmod.gui.ui.control.Bounds;
import universalmod.gui.ui.control.ColorPickerGeometry;
import universalmod.gui.ui.control.ColorPickerInteraction;
import universalmod.gui.ui.control.ColorPickerRenderer;
import universalmod.hud.ElementScreen;
import universalmod.render.Render2D;
import universalmod.render.animation.Easings;
import universalmod.render.animation.SmoothAnimation;
import universalmod.render.color.ColorUtil;
import universalmod.render.surface.GuiSurface;
import universalmod.setting.ColorSetting;
import universalmod.theme.ThemeColors;

import java.awt.Color;


final class HudColorPickerOverlay {
    private static final float SCREEN_MARGIN = 4.0F;
    private static final float WIDTH = 210.0F;
    private static final float HEIGHT = 196.0F;
    private static final float GAP = 18.2F;

    private final SmoothAnimation animation = new SmoothAnimation();
    private final ColorPickerInteraction interaction = new ColorPickerInteraction();
    private ColorSetting setting;
    private float x;
    private float y;

    HudColorPickerOverlay() {
        animation.set(0.0);
    }

    void reset() {
        setting = null;
        interaction.cancelDrag();
        animation.set(0.0);
    }

    void updateAnimation() {
        animation.update();
        if (setting != null && animation.get() <= 0.001F && !animation.isAlive()) {
            setting = null;
        }
    }

    boolean isOpen() {
        return setting != null;
    }

    boolean dragging() {
        return interaction.dragging();
    }

    boolean visibleForInput() {
        return setting != null && animation.get() > 0.2F;
    }

    void toggle(ColorSetting requested) {
        if (setting == requested && animation.get() > 0.001F) {
            close();
            return;
        }
        setting = requested;
        sync(new Color(requested.getValue(), true));
        interaction.resetVisual();
        animation.set(0.0);
        animation.run(1.0, 0.15, Easings.POW_17, true);
    }

    void close() {
        interaction.cancelDrag();
        if (setting != null) {
            animation.run(0.0, 0.15, Easings.POW_17, true);
        }
    }

    boolean mouseClicked(float mouseX, float mouseY, int button, float panelX, float panelY) {
        if (!visibleForInput()) {
            return false;
        }
        updateBounds(panelX, panelY);
        updateControls();
        if (!inside(mouseX, mouseY, x, y, u(WIDTH), u(HEIGHT))) {
            return false;
        }
        if (button == 0) {
            interaction.press(mouseX, mouseY, ColorPickerInteraction.HueAxis.HORIZONTAL, u(2.0F), this::applyColor);
        }
        return true;
    }

    void updateDrag(float mouseX, float mouseY, boolean pressed) {
        if (setting == null || !interaction.dragging()) {
            return;
        }
        if (!pressed) {
            interaction.cancelDrag();
            return;
        }
        interaction.drag(mouseX, mouseY, ColorPickerInteraction.HueAxis.HORIZONTAL, this::applyColor);
    }

    void cancelDrag() {
        interaction.cancelDrag();
    }

    void render(DrawContext graphics, float panelX, float panelY, float parentAlpha) {
        if (setting == null || animation.get() <= 0.001F) {
            return;
        }
        if (!interaction.dragging()) {
            sync(new Color(setting.getValue(), true));
        }
        updateBounds(panelX, panelY);
        updateControls();
        interaction.animate();
        float progress = clamp01(animation.get());
        float alpha = parentAlpha * progress;
        float scale = 0.92F + 0.08F * progress;
        float shift = (1.0F - progress) * -u(6.0F);
        float drawY = y + shift;
        float offset = drawY - y;
        Bounds drawPalette = interaction.palette().translated(0.0F, offset);
        Bounds drawHue = interaction.hue().translated(0.0F, offset);
        Bounds drawAlpha = interaction.alpha().translated(0.0F, offset);
        float centerX = x + u(WIDTH) * 0.5F;
        float centerY = drawY + u(HEIGHT) * 0.5F;
        graphics.getMatrices().push();
        try {
            graphics.getMatrices().translate(centerX, centerY, 0);
            graphics.getMatrices().scale(scale, scale, 1);
            graphics.getMatrices().translate(-centerX, -centerY, 0);
            Render2D.withCurrentMatrix(() -> {
            float radius = u(6.4F);
            Render2D.rect(x, drawY, u(WIDTH), u(HEIGHT), radius,
                    ColorUtil.rgba(8, 8, 11, Math.round(255.0F * alpha)));
            new GuiSurface(x, drawY, u(WIDTH), u(HEIGHT), radius,
                    alpha, 0.22F, ColorUtil.rgba(9, 9, 12, 255),
                    ThemeColors.clickGuiAccentColor(255, 1.0F)).render(graphics);
            Render2D.outline(x, drawY, u(WIDTH), u(HEIGHT), radius, u(2.1F),
                    ColorUtil.rgba(6, 6, 9, Math.round(238.0F * alpha)));
            ColorPickerRenderer.palette(drawPalette, interaction.visual().hue(), interaction.visual().saturation(),
                    interaction.visual().brightness(), alpha, 1.0F);
            ColorPickerRenderer.hue(drawHue, interaction.visual().hue(), alpha, 1.0F);
            ColorPickerRenderer.alpha(drawAlpha, interaction.visual().hue(), interaction.visual().saturation(),
                    interaction.visual().brightness(), interaction.visual().alpha(), alpha, 1.0F);
            });
        } finally {
            graphics.getMatrices().pop();
        }
    }

    private void updateBounds(float panelX, float panelY) {
        ElementScreen screen = ElementScreen.current();
        float right = panelX + HudEditorControls.panelWidth() + u(GAP);
        float left = panelX - u(WIDTH + GAP);
        float maxX = screen.width() - u(WIDTH + SCREEN_MARGIN);
        x = right <= maxX ? right : left >= u(SCREEN_MARGIN)
                ? left
                : clamp(right, u(SCREEN_MARGIN), Math.max(u(SCREEN_MARGIN), maxX));
        y = clamp(panelY + u(HudEditorControls.HEADER_HEIGHT), u(SCREEN_MARGIN),
                Math.max(u(SCREEN_MARGIN), screen.height() - u(HEIGHT + SCREEN_MARGIN)));
    }

    private void updateControls() {
        float paletteX = x + u(9.0F);
        float paletteY = y + u(9.0F);
        float paletteWidth = u(192.0F);
        float paletteHeight = u(124.0F);
        Bounds palette = new Bounds(paletteX, paletteY, paletteWidth, paletteHeight);
        Bounds hue = new Bounds(paletteX, palette.bottom() + u(9.0F), paletteWidth, u(11.0F));
        Bounds alpha = new Bounds(paletteX, hue.bottom() + u(8.0F), paletteWidth, u(11.0F));
        interaction.geometry(new ColorPickerGeometry(palette, hue, alpha));
    }

    private void sync(Color color) {
        interaction.sync(color);
    }

    private void applyColor(Color color) {
        if (setting != null && color != null) {
            setting.setValue(color.getRGB());
        }
    }

    private static float u(float value) {
        return HudEditorControls.u(value);
    }

    private static float clamp01(float value) {
        return HudEditorControls.clamp01(value);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean inside(float px, float py, float x, float y, float width, float height) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }
}
