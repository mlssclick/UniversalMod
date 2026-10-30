package universalmod.gui.clickgui.component;

import net.minecraft.client.gui.GuiGraphics;
import universalmod.gui.clickgui.component.option.ClickGuiOption;
import universalmod.render.Render2D;
import universalmod.render.color.ColorUtil;
import universalmod.render.surface.GuiSurface;
import universalmod.theme.ThemeColors;

import java.util.List;


final class DetachedSettingsRenderer {
    private DetachedSettingsRenderer() {
    }

    static void render(
            GuiGraphics graphics,
            List<ClickGuiOption> settings,
            DetachedOptionLayout layout,
            List<ClickGuiOption> interactiveSettings,
            float reveal,
            float x,
            float y,
            float width,
            float height,
            float scale,
            float alpha,
            float scroll
    ) {
        final float factor = 1.0F;
        layout.prepare(settings, factor);
        float optionScale = scale * factor;
        float baseY = y - scroll * scale;
        float pad = 6.0F * scale;

        Render2D.pushScissor(graphics, x, y, width, height);
        try {
            for (int index = 0; index < settings.size(); index++) {
                ClickGuiOption option = settings.get(index);
                float visibility = layout.visibility(index);
                if (visibility <= 0.01F) {
                    continue;
                }

                float optionHeight = option.getHeight() * optionScale;
                float rowHeight = layout.rowHeight(index) * scale;
                float animatedY = baseY + layout.offset(index) * scale
                        - (1.0F - visibility) * 4.0F * scale
                        + (1.0F - reveal) * 7.0F * scale;

                if (animatedY + rowHeight >= y - 1.0F && animatedY <= y + height + 1.0F) {
                    new GuiSurface(
                            x, animatedY, width, rowHeight, 2.7F * scale,
                            alpha * visibility * reveal, 0.09F,
                            ColorUtil.rgba(21, 19, 23, 255),
                            ThemeColors.clickGuiAccentColor(255, 1.0F)
                    ).render(graphics);
                    Render2D.outline(
                            x, animatedY, width, rowHeight, 2.7F * scale, 0.28F * scale,
                            ColorUtil.rgba(103, 97, 109, Math.round(42.0F * alpha * visibility * reveal))
                    );

                    option.setFadeMultiplier(visibility * reveal);
                    float optionWidth = Math.max(1.0F, (width - pad * 2.0F) / Math.max(0.0001F, optionScale));
                    option.render(
                            graphics,
                            x + pad,
                            animatedY + (rowHeight - optionHeight) * 0.5F,
                            optionWidth,
                            optionScale,
                            alpha,
                            x, y, width, height
                    );
                    if (reveal > 0.88F && option.isVisibleForInteraction()) {
                        interactiveSettings.add(option);
                    }
                } else {
                    option.resetInteractionFrame();
                }
            }
        } finally {
            Render2D.popScissor(graphics);
        }
    }

    static void renderOverlay(GuiGraphics graphics, List<ClickGuiOption> settings) {
        for (ClickGuiOption option : settings) {
            if (option.isVisibleForRender()) {
                option.renderOverlay(graphics);
            }
        }
    }
}
