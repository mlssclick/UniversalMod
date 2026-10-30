package universalmod.hud.element;

import net.minecraft.client.gui.DrawContext;
import universalmod.hud.ElementScreen;
import universalmod.hud.editor.HudPanel;
import universalmod.hud.notification.HudNotificationManager;
import universalmod.service.language.LanguageManager;
import universalmod.render.Render2D;
import universalmod.render.font.FontType;
import universalmod.module.ModuleCategory;
import universalmod.gui.clickgui.ClickGuiMsdfIcons;
import java.util.List;
public final class NotificationsPanel extends HudPanel {
    private static final float HEIGHT = 14.0F;
    private static final float STRIDE = 16.0F;
    private static final float MAX_WIDTH = 136.0F;
    private static final float TEXT_SIZE = 4.65F;
    private static final float ICON_SIZE = 7.0F;
    private static final float ICON_TEXT_GAP = 3.2F;
    private static final float SIDE_PAD = 4.5F;
    private static final float SLIDE = 18.0F;
    private final HudNotificationManager notifications = HudNotificationManager.getInstance();

    public NotificationsPanel() {
        super("notifications", "Notifications", defaultX(), 24.0F, MAX_WIDTH, HEIGHT);
        sizeImmediate(MAX_WIDTH, HEIGHT);
    }

    @Override
    public void render() {
        List<HudNotificationManager.Entry> entries = notifications.snapshotAndUpdate(STRIDE);
        boolean preview = entries.isEmpty() && editPreview();
        if (entries.isEmpty() && !preview) {
            contentVisible(false);
            return;
        }
        contentVisible(true);

        float totalH = preview
                ? HEIGHT
                : Math.max(HEIGHT, entries.size() * STRIDE - (STRIDE - HEIGHT));


        sizeImmediate(MAX_WIDTH, totalH);
        float containerX = drag.x();
        float y = drag.y();

        if (preview) {
            float width = toastWidth("Atmosphere", true);
            float x = containerX + (MAX_WIDTH - width) * 0.5F;
            drawToast("Atmosphere", ModuleCategory.RENDER, true, x, y, width, 1.0F);
            return;
        }

        for (HudNotificationManager.Entry entry : entries) {
            float p = entry.visibility();
            if (p <= 0.001F) {
                continue;
            }
            float width = toastWidth(entry.module(), entry.enabled());
            float centeredX = containerX + (MAX_WIDTH - width) * 0.5F;
            float x = centeredX + (1.0F - p) * SLIDE;
            drawToast(entry.module(), entry.category(), entry.enabled(), x, y + entry.y(), width, p);
        }
    }

    private void drawToast(String module, ModuleCategory category, boolean enabled, float x, float y, float width, float alpha) {
        DrawContext graphics = Render2D.currentGraphics();
        if (graphics == null) {
            return;
        }

        float scale = 0.96F + 0.04F * alpha;
        float cx = x + width * 0.5F;
        float cy = y + HEIGHT * 0.5F;
        graphics.getMatrices().push();
        try {
            graphics.getMatrices().translate(cx, cy, 0.0F);
            graphics.getMatrices().scale(scale, scale, 1.0F);
            graphics.getMatrices().translate(-cx, -cy, 0.0F);

            HudVisualStyle.blackPanel(x, y, width, HEIGHT, 3.6F, alpha);
            int accent = enabled ? HudVisualStyle.accent(1.0F) : HudVisualStyle.RED;
            String prefix = LanguageManager.translate("hud.notification.prefix");
            String suffix = LanguageManager.translate(enabled ? "hud.notification.enabled" : "hud.notification.disabled");
            float prefixW = Render2D.textWidth(FontType.BOLD, prefix, TEXT_SIZE);
            float suffixW = Render2D.textWidth(FontType.BOLD, suffix, TEXT_SIZE);
            float maxModuleW = Math.max(8.0F,
                    width - SIDE_PAD * 2.0F - ICON_SIZE - ICON_TEXT_GAP - prefixW - suffixW);
            String clipped = trimToWidth(module, FontType.BOLD, TEXT_SIZE, maxModuleW);
            float moduleW = Render2D.textWidth(FontType.BOLD, clipped, TEXT_SIZE);
            float lineW = prefixW + moduleW + suffixW;
            float contentW = ICON_SIZE + ICON_TEXT_GAP + lineW;
            float contentX = x + Math.max(SIDE_PAD, (width - contentW) * 0.5F);
            float iconY = y + (HEIGHT - ICON_SIZE) * 0.5F;

            ClickGuiMsdfIcons.drawCategoryIcon(
                    category, contentX, iconY, ICON_SIZE,
                    HudVisualStyle.alpha(accent, alpha));

            float tx = contentX + ICON_SIZE + ICON_TEXT_GAP;
            float textH = Render2D.textHeight(FontType.BOLD, prefix, TEXT_SIZE);
            float ty = y + (HEIGHT - textH) * 0.5F;

            Render2D.text(FontType.BOLD, prefix, tx, ty, TEXT_SIZE,
                    HudVisualStyle.alpha(HudVisualStyle.TEXT, alpha));
            tx += prefixW;
            Render2D.text(FontType.BOLD, clipped, tx, ty, TEXT_SIZE,
                    HudVisualStyle.alpha(accent, alpha));
            tx += moduleW;
            Render2D.text(FontType.BOLD, suffix, tx, ty, TEXT_SIZE,
                    HudVisualStyle.alpha(HudVisualStyle.TEXT, alpha));
        } finally {
            graphics.getMatrices().pop();
        }
    }

    private static float toastWidth(String module, boolean enabled) {
        String safeModule = module == null ? "" : module;
        String prefix = LanguageManager.translate("hud.notification.prefix");
        String suffix = LanguageManager.translate(enabled
                ? "hud.notification.enabled"
                : "hud.notification.disabled");


        float textW = Render2D.textWidth(FontType.BOLD, prefix, TEXT_SIZE)
                + Render2D.textWidth(FontType.BOLD, safeModule, TEXT_SIZE)
                + Render2D.textWidth(FontType.BOLD, suffix, TEXT_SIZE);
        float required = SIDE_PAD * 2.0F + ICON_SIZE + ICON_TEXT_GAP + textW;
        return Math.min(MAX_WIDTH, required);
    }

    private static float defaultX() {
        ElementScreen s = ElementScreen.current();
        return Math.max(4.0F, s.width() - MAX_WIDTH - 10.0F);
    }
}
