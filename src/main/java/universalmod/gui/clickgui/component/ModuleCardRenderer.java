package universalmod.gui.clickgui.component;

import net.minecraft.client.gui.DrawContext;
import universalmod.render.Render2D;
import universalmod.render.color.ColorUtil;
import universalmod.render.font.FontType;
import universalmod.render.optimize.UiFrameContext;
import universalmod.theme.ThemeColors;
import universalmod.gui.ui.control.SmoothSwitchRenderer;

final class ModuleCardRenderer {
    private ModuleCardRenderer() {
    }

    static float bindBadge(DrawContext graphics, String bind, boolean unbound,
                           float x, float y, float scale, float alpha, float bindProgress) {
        String safeBind = bind == null || bind.isBlank() ? "n/a" : bind;
        float badgeHeight = 13.0F * scale;
        float textSize = (unbound ? 5.1F : 5.6F) * scale;
        float textWidth = Render2D.textWidth(FontType.BOLD, safeBind, textSize);
        float badgeWidth = Math.max(22.0F * scale, Math.min(44.0F * scale, textWidth + 9.0F * scale));
        float radius = 4.5F * scale;
        float pulse = clamp01(bindProgress);

        int fill = ColorUtil.lerpColor(
                ColorUtil.rgba(31, 34, 50, Math.round((unbound ? 190.0F : 230.0F) * alpha)),
                ColorUtil.rgba(91, 73, 139, Math.round(242.0F * alpha)),
                pulse
        );
        int outline = ColorUtil.lerpColor(
                ColorUtil.rgba(151, 137, 216, Math.round((unbound ? 24.0F : 46.0F) * alpha)),
                ThemeColors.clickGuiAccentColor(Math.round(108.0F * alpha), 1.0F),
                pulse
        );
        Render2D.rect(x, y, badgeWidth, badgeHeight, radius, fill);
        Render2D.outline(x, y, badgeWidth, badgeHeight, radius, 0.35F * scale, outline);

        float clipPad = 3.0F * scale;
        float clipWidth = badgeWidth - clipPad * 2.0F;
        float textX = x + (badgeWidth - textWidth) * 0.5F;
        float textY = y + (badgeHeight - textSize) * 0.5F - 0.75F * scale;
        Render2D.pushScissor(graphics, x + clipPad, y, clipWidth, badgeHeight);
        try {
            Render2D.text(FontType.BOLD, safeBind, textX, textY, textSize,
                    ColorUtil.rgba(unbound ? 170 : 215, unbound ? 172 : 215, unbound ? 192 : 231,
                            Math.round((unbound ? 218.0F : 248.0F) * alpha)));
        } finally {
            Render2D.popScissor(graphics);
        }
        return badgeWidth;
    }

    static void toggle(float x, float y, float scale, float alpha, float progress) {
        SmoothSwitchRenderer.render(x, y, scale, alpha, progress);
    }

    static void scrollingTitle(DrawContext graphics, String text, float textX, float textY, float textSize,
                               int textColor, float clipWidth, float clipY, float clipHeight) {
        if (text == null || text.isEmpty() || clipWidth <= 0.25F) {
            return;
        }
        float offset = ModuleCardMotion.titleOffset(
                Render2D.textWidth(FontType.BOLD, text, textSize),
                clipWidth,
                UiFrameContext.nowMillis()
        );
        Render2D.pushScissor(graphics, textX, clipY, clipWidth, clipHeight);
        try {
            Render2D.text(FontType.BOLD, text, textX + offset, textY, textSize, textColor);
        } finally {
            Render2D.popScissor(graphics);
        }
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
