package universalmod.gui.clickgui;

import net.minecraft.client.gui.GuiGraphics;
import universalmod.gui.clickgui.component.ModuleOption;
import universalmod.gui.clickgui.component.option.OptionStyle;
import universalmod.render.Render2D;
import universalmod.render.color.ColorUtil;
import universalmod.render.font.FontType;
import universalmod.render.surface.GuiSurface;
import universalmod.theme.ThemeColors;


final class ModuleBindBadge {
    private static final float HEIGHT = 13.0f;
    private static final float MIN_WIDTH = 25.0f;
    private static final float MAX_WIDTH = 48.0f;
    private static final float HORIZONTAL_PADDING = 9.0f;
    private static final float RIGHT_MARGIN = 6.0f;
    private static final float HIT_SLOP = 2.0f;

    private ModuleBindBadge() {
    }

    static String label(ModuleOption option) {
        if (option == null) {
            return "N/A";
        }
        String raw = option.bindLabel();
        if (raw == null || raw.isBlank() || "n/a".equalsIgnoreCase(raw)) {
            return "N/A";
        }
        if ("...".equals(raw)) {
            return "...";
        }
        return raw;
    }

    static boolean binding(ModuleOption option) {
        return "...".equals(label(option));
    }

    static float height(float scale) {
        return HEIGHT * scale;
    }

    static float textSize(ModuleOption option, float scale) {
        String label = label(option);
        return ("N/A".equals(label) ? 5.10f : 5.60f) * scale;
    }

    static float width(ModuleOption option, float scale) {
        String label = label(option);
        float size = textSize(option, scale);
        float textWidth = Render2D.textWidth(FontType.BOLD, label, size);
        return Math.max(MIN_WIDTH * scale,
                Math.min(MAX_WIDTH * scale, textWidth + HORIZONTAL_PADDING * scale));
    }

    static float x(float rowX, float rowWidth, ModuleOption option, float scale) {
        return rowX + rowWidth - width(option, scale) - RIGHT_MARGIN * scale;
    }

    static float y(float rowY, float rowHeight, float scale) {
        return rowY + (rowHeight - height(scale)) * 0.5f;
    }

    static float leftReservedWidth(ModuleOption option, float scale) {
        return width(option, scale) + (RIGHT_MARGIN + 6.0f) * scale;
    }

    static boolean hit(double mouseX, double mouseY,
                       float rowX, float rowY, float rowWidth, float rowHeight,
                       ModuleOption option, float scale) {
        float bx = x(rowX, rowWidth, option, scale);
        float by = y(rowY, rowHeight, scale);
        float bw = width(option, scale);
        float bh = height(scale);
        float slop = HIT_SLOP * scale;
        return mouseX >= bx - slop && mouseX <= bx + bw + slop
                && mouseY >= by - slop && mouseY <= by + bh + slop;
    }

    static void render(GuiGraphics graphics,
                       float rowX, float rowY, float rowWidth, float rowHeight,
                       ModuleOption option, float scale, float alpha) {
        String label = label(option);
        boolean binding = binding(option);
        boolean unbound = "N/A".equals(label);

        float bw = width(option, scale);
        float bh = height(scale);
        float bx = x(rowX, rowWidth, option, scale);
        float by = y(rowY, rowHeight, scale);
        float radius = bh * 0.5f;
        int fill = binding
                ? ColorUtil.rgba(88, 67, 142, 255)
                : ColorUtil.rgba(22, 23, 31, 255);
        int accent = binding
                ? ThemeColors.clickGuiAccentColor(255, 1.0f)
                : ColorUtil.rgba(112, 105, 128, 255);
        new GuiSurface(
                bx, by, bw, bh, radius,
                alpha * (binding ? 0.97f : unbound ? 0.86f : 0.92f),
                binding ? 0.46f : 0.12f,
                fill, accent
        ).render(graphics);

        float fontSize = textSize(option, scale);
        float textWidth = Render2D.textWidth(FontType.BOLD, label, fontSize);
        float textX = bx + (bw - textWidth) * 0.5f;
        float textY = OptionStyle.centeredTextY(FontType.BOLD, label, fontSize, by, bh);
        int textColor = binding
                ? ColorUtil.rgba(241, 232, 255, Math.round(255.0f * alpha))
                : ColorUtil.rgba(unbound ? 176 : 226, unbound ? 177 : 224, unbound ? 187 : 232,
                Math.round((unbound ? 228.0f : 252.0f) * alpha));

        float clipPad = 2.5f * scale;
        Render2D.pushScissor(graphics, bx + clipPad, by, Math.max(1.0f, bw - clipPad * 2.0f), bh);
        try {
            Render2D.text(FontType.BOLD, label, textX, textY, fontSize, textColor);
        } finally {
            Render2D.popScissor(graphics);
        }
    }
}
