package universalmod.module.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import universalmod.hud.ElementManager;
import universalmod.hud.ElementScreen;
import universalmod.hud.HudElement;
import universalmod.hud.editor.HudPanel;
import universalmod.hud.registry.HudElementDescriptor;
import universalmod.hud.registry.HudElementRegistry;
import universalmod.hud.registry.HudElementsSetting;
import universalmod.render.Render2D;
import universalmod.render.animation.Easings;
import universalmod.render.animation.SmoothAnimation;
import universalmod.render.color.ColorUtil;
import universalmod.render.font.FontType;
import universalmod.service.language.LanguageManager;


final class HudHoverHint {
    private final SmoothAnimation animation = new SmoothAnimation();
    private HudPanel element;

    void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            boolean hudEnabled,
            HudElementRegistry registry,
            HudElementsSetting elements
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!hudEnabled || minecraft.player == null || minecraft.level == null || !(minecraft.screen instanceof ChatScreen)) {
            fadeOut();
            return;
        }

        ElementScreen screen = ElementScreen.current();
        ElementManager.getInstance().frame(screen);
        float coordinateScale = Math.max(0.0001F, screen.coordinateScale());
        float designMouseX = mouseX / coordinateScale;
        float designMouseY = mouseY / coordinateScale;
        HudPanel hovered = hoveredPanelAt(designMouseX, designMouseY, registry, elements);
        if (hovered == null && Math.abs(coordinateScale - 1.0F) > 0.0001F) {
            hovered = hoveredPanelAt(mouseX, mouseY, registry, elements);
        }
        if (hovered != null && hovered.moving()) {
            hovered = null;
        }

        if (hovered != null && hovered != element) {
            element = hovered;
            animation.set(0.0);
        }
        animation.run(hovered != null ? 1.0 : 0.0, 0.16, Easings.CUBIC_OUT, true);
        animation.update();

        float progress = Math.clamp(animation.get(), 0.0F, 1.0F);
        if (progress <= 0.01F || element == null) {
            if (progress <= 0.01F) {
                element = null;
            }
            return;
        }

        Render2D.beginFrame(graphics);
        try {
            renderHint(screen, progress);
        } finally {
            Render2D.flush();
            graphics.nextStratum();
        }
    }

    private void renderHint(ElementScreen screen, float progress) {
        String text = LanguageManager.ui("hud.edit_hint");
        float textSize = 7.4F;
        float gap = 4.0F;
        float textWidth = Render2D.textWidth(FontType.SEMIBOLD, text, textSize);
        float centerX = element.x() + element.width() * 0.5F;
        boolean below = element.y() + element.height() + gap + textSize <= screen.height();
        float baseY = below ? element.y() + element.height() + gap : element.y() - textSize - gap;
        float offset = (1.0F - progress) * (below ? 3.0F : -3.0F);
        float drawX = clamp(centerX - textWidth * 0.5F, 2.0F, screen.width() - textWidth - 2.0F);
        float drawY = clamp(baseY + offset, 2.0F, screen.height() - textSize - 2.0F);
        Render2D.text(FontType.SEMIBOLD, text, drawX, drawY, textSize,
                ColorUtil.rgba(255, 255, 255, Math.round(255.0F * progress)));
    }

    private void fadeOut() {
        animation.run(0.0, 0.16, Easings.CUBIC_OUT, true);
        animation.update();
        if (animation.get() <= 0.01F) {
            element = null;
        }
    }

    private HudPanel hoveredPanelAt(
            float mouseX,
            float mouseY,
            HudElementRegistry registry,
            HudElementsSetting elements
    ) {
        var descriptors = registry.ordered();
        for (int i = descriptors.size() - 1; i >= 0; i--) {
            HudElementDescriptor descriptor = descriptors.get(i);
            HudElement candidate = descriptor.element();
            if (!elements.isSelected(descriptor.id()) || !(candidate instanceof HudPanel panel)) {
                continue;
            }
            if (panel.hit(mouseX, mouseY)) {
                return panel;
            }
        }
        return null;
    }

    private static float clamp(float value, float min, float max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
