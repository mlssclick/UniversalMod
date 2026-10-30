package universalmod.gui.clickgui;


import net.minecraft.client.gui.DrawContext;
import universalmod.gui.clickgui.component.ModuleOption;
import universalmod.render.color.ColorUtil;
import universalmod.render.font.FontType;







final class ModuleSettingsIcon {

    private static final String GLYPH = "%";
    private static final float BOX_SIZE = 12.0f;
    private static final float GAP_TO_BIND = 4.0f;
    private static final float HIT_SLOP = 2.0f;

    private ModuleSettingsIcon() {
    }

    static float reservedWidth(ModuleOption option, float scale) {
        return option != null && option.hasSettings() ? (BOX_SIZE + GAP_TO_BIND) * scale : 0.0f;
    }

    static float x(float rowX, float rowWidth, ModuleOption option, float scale) {
        float bindX = ModuleBindBadge.x(rowX, rowWidth, option, scale);
        return bindX - GAP_TO_BIND * scale - BOX_SIZE * scale;
    }

    static float y(float rowY, float rowHeight, float scale) {
        return rowY + (rowHeight - BOX_SIZE * scale) * 0.5f;
    }

    static boolean hit(double mouseX, double mouseY,
                       float rowX, float rowY, float rowWidth, float rowHeight,
                       ModuleOption option, float scale) {
        if (option == null || !option.hasSettings()) {
            return false;
        }
        float x = x(rowX, rowWidth, option, scale);
        float y = y(rowY, rowHeight, scale);
        float size = BOX_SIZE * scale;
        float slop = HIT_SLOP * scale;
        return mouseX >= x - slop && mouseX <= x + size + slop
                && mouseY >= y - slop && mouseY <= y + size + slop;
    }

    static void render(DrawContext graphics,
                       float rowX, float rowY, float rowWidth, float rowHeight,
                       ModuleOption option, float scale, float alpha) {
        if (option == null || !option.hasSettings()) {
            return;
        }
        float x = x(rowX, rowWidth, option, scale);
        float y = y(rowY, rowHeight, scale);
        float size = BOX_SIZE * scale;

        ClickGuiMsdfIcons.drawCentered(
                FontType.DELTA_ICONS, GLYPH,
                x, y, size, size, 0.70f,
                ColorUtil.rgba(196, 192, 204, Math.round(238.0f * alpha))
        );
    }
}
