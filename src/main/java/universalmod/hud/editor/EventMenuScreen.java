package universalmod.hud.editor;


import universalmod.hud.element.CurrentEventsPanel;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import universalmod.module.render.Hud;
import universalmod.service.language.LanguageManager;
import universalmod.render.color.ColorUtil;
import universalmod.render.Render2D;
import universalmod.render.font.FontType;

public final class EventMenuScreen extends Screen {
    private static final float PANEL_WIDTH = 230.0F;
    private static final float ROW_HEIGHT = 14.0F;
    private static final float HEADER_HEIGHT = 18.0F;
    private static final float PADDING = 8.0F;
    private static final float BUTTON_WIDTH = 34.0F;
    private static final float BUTTON_HEIGHT = 10.0F;
    private static final int TITLE_COLOR = 0xFFF3F6FA;
    private static final int BUTTON_TEXT_ENABLED_COLOR = 0xFF84FF6A;
    private static final int BUTTON_TEXT_DISABLED_COLOR = 0xFFFF6A6A;
    private static final int EMPTY_COLOR = 0xFF96A0AA;

    private final Screen parent;
    private final CurrentEventsPanel panel = CurrentEventsPanel.getInstance();

    public EventMenuScreen(Screen parent) {
        super(Text.empty());
        this.parent = parent;
    }

    @Override
    public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {
        Render2D.beginFrame(graphics);
        try {
            float panelHeight = HEADER_HEIGHT + (Math.max(1, panel.filters().size()) * ROW_HEIGHT) + 8.0F;
            float panelX = (Render2D.getFixedScaledWidth() - PANEL_WIDTH) * 0.5F;
            float panelY = (Render2D.getFixedScaledHeight() - panelHeight) * 0.5F;

            Hud.renderHudBackground(panelX, panelY, PANEL_WIDTH, panelHeight, 4.0F, 4.0F, 0.55F, ColorUtil.rgba(10, 12, 16, 222));
            Render2D.text(FontType.BOLD, LanguageManager.ui("event.title"), panelX + PADDING, panelY + 5.0F, 8.0F, TITLE_COLOR);

            float rowY = panelY + HEADER_HEIGHT;
            if (panel.filters().isEmpty()) {
                Render2D.text(FontType.BOLD, LanguageManager.ui("current_events.empty"), panelX + PADDING, rowY + 2.0F, 6.8F, EMPTY_COLOR);
            }

            for (CurrentEventsPanel.FilterEntry entry : panel.filters()) {
                float buttonX = panelX + PANEL_WIDTH - PADDING - BUTTON_WIDTH;
                renderEntry(graphics, panelX, rowY, entry, buttonX);
                rowY += ROW_HEIGHT;
            }
        } finally {
            Render2D.flush();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        mouseX = Render2D.guiToFixed(mouseX);
        mouseY = Render2D.guiToFixed(mouseY);
        float panelHeight = HEADER_HEIGHT + (Math.max(1, panel.filters().size()) * ROW_HEIGHT) + 8.0F;
        float panelX = (Render2D.getFixedScaledWidth() - PANEL_WIDTH) * 0.5F;
        float panelY = (Render2D.getFixedScaledHeight() - panelHeight) * 0.5F;

        float fx = (float) mouseX;
        float fy = (float) mouseY;
        float buttonX = panelX + PANEL_WIDTH - PADDING - BUTTON_WIDTH;
        float rowY = panelY + HEADER_HEIGHT;

        for (CurrentEventsPanel.FilterEntry entry : panel.filters()) {
            if (inside(fx, fy, buttonX, rowY + 1.5F, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                panel.toggleEntry(entry);
                return true;
            }
            rowY += ROW_HEIGHT;
        }

        return inside(fx, fy, panelX, panelY, PANEL_WIDTH, panelHeight) || super.mouseClicked(Render2D.fixedToGui(mouseX), Render2D.fixedToGui(mouseY), button);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    private void renderEntry(DrawContext graphics, float panelX, float rowY, CurrentEventsPanel.FilterEntry entry, float buttonX) {
        Render2D.text(FontType.BOLD, entry.label(), panelX + PADDING, rowY + 1.7F, 6.8F, entry.color);

        String text = entry.enabled ? "ON" : "OFF";
        float textWidth = Render2D.textWidth(FontType.BOLD, text, 6.8F);
        float textX = buttonX + (BUTTON_WIDTH - textWidth) * 0.5F;
        Render2D.text(FontType.BOLD, text, textX, rowY + 1.4F, 6.8F, entry.enabled ? BUTTON_TEXT_ENABLED_COLOR : BUTTON_TEXT_DISABLED_COLOR);
    }

    private static boolean inside(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
