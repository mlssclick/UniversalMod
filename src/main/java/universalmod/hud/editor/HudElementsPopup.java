package universalmod.hud.editor;

import net.minecraft.client.gui.DrawContext;
import universalmod.hud.HudMouseEvent;
import universalmod.hud.ElementScreen;
import universalmod.hud.registry.HudElementDescriptor;
import universalmod.gui.ui.control.Bounds;
import universalmod.gui.ui.control.SmoothSwitchRenderer;
import universalmod.hud.registry.HudElementId;
import universalmod.module.render.Hud;
import universalmod.render.Render2D;
import universalmod.render.animation.Easings;
import universalmod.render.animation.SmoothAnimation;
import universalmod.render.animation.ReversibleAnimationTimeline;
import universalmod.render.color.ColorUtil;
import universalmod.render.font.FontType;
import universalmod.service.language.LanguageManager;
import universalmod.theme.ThemeColors;

import java.util.HashMap;
import java.util.Map;

public final class HudElementsPopup {
    private static final HudElementsPopup INSTANCE = new HudElementsPopup();

    private static final float SCREEN_MARGIN = 4.0F;
    private static final float HEADER_HEIGHT = 56.0F;
    private static final float HEADER_TITLE_SIZE = 16.0F;
    private static final float ROW_HEIGHT = 34.0F;
    private static final float MAX_HEIGHT = 400.0F;
    private static final float BOTTOM_PADDING = 14.0F;
    private static final float SIDE_PADDING = 16.0F;

    private static final float CHECK_RIGHT = 15.0F;

    private final SmoothAnimation popupAnimation = new SmoothAnimation();
    private final SmoothAnimation scrollAnimation = new SmoothAnimation();
    private final Map<HudElementId, ReversibleAnimationTimeline> switchAnimations = new HashMap<>();
    private boolean open;
    private boolean closing;
    private float x;
    private float y;
    private float scrollTarget;

    private HudElementsPopup() {
        popupAnimation.set(0.0);
        scrollAnimation.set(0.0);
    }

    public static HudElementsPopup getInstance() {
        return INSTANCE;
    }

    public void open(float mouseX, float mouseY) {
        Hud hud = Hud.getInstance();
        if (hud == null || !hud.isEnabled()) {
            finishClose();
            return;
        }

        float width = HudEditorControls.panelWidth();
        float height = height(hud);
        ElementScreen screen = ElementScreen.current();
        float margin = u(SCREEN_MARGIN);

        x = clamp(mouseX, margin, Math.max(margin, screen.width() - width - margin));
        y = clamp(mouseY, margin, Math.max(margin, screen.height() - height - margin));

        open = true;
        closing = false;
        scrollTarget = 0.0F;
        scrollAnimation.set(0.0);
        popupAnimation.set(0.0);
        popupAnimation.run(1.0, 0.22, Easings.POW_17, true);
    }

    public void close() {
        if (!open || closing) {
            return;
        }
        closing = true;
        popupAnimation.run(0.0, 0.22, Easings.POW_17, false);
    }

    public boolean isOpen() {
        return open;
    }

    public void render(DrawContext graphics) {
        if (!open) {
            return;
        }

        Hud hud = Hud.getInstance();
        if (hud == null || !hud.isEnabled()) {
            finishClose();
            return;
        }

        popupAnimation.update();
        scrollAnimation.run(scrollTarget, 0.28, Easings.DECAY_OUT, true);
        scrollAnimation.update();

        float appear = clamp01(popupAnimation.get());
        if (closing && appear <= 0.001F && !popupAnimation.isAlive()) {
            finishClose();
            return;
        }
        if (appear <= 0.001F) {
            return;
        }

        float width = HudEditorControls.panelWidth();
        float height = height(hud);
        ElementScreen screen = ElementScreen.current();
        float margin = u(SCREEN_MARGIN);
        x = clamp(x, margin, Math.max(margin, screen.width() - width - margin));
        y = clamp(y, margin, Math.max(margin, screen.height() - height - margin));
        clampScroll(hud, height);

        float scale = 0.92F + 0.08F * appear;
        float shiftY = (1.0F - appear) * -u(6.0F);
        float cx = x + width * 0.5F;
        float cy = y + height * 0.5F;

        graphics.getMatrices().push();
        try {
            graphics.getMatrices().translate(cx, cy + shiftY, 0.0f);
            graphics.getMatrices().scale(scale, scale, 1.0f);
            graphics.getMatrices().translate(-cx, -cy, 0.0f);

            
            HudEditorControls.panel(x, y, height, appear);
            drawHeader(width, appear);

            Bounds content = contentBounds(width, height);
            Render2D.pushScissor(graphics, content.x(), content.y(), content.width(), content.height());
            try {
                int index = 0;
                for (HudElementDescriptor descriptor : hud.hudElementRegistry().ordered()) {
                    Bounds row = rowBounds(content, index++);
                    if (row.bottom() >= content.y() && row.y() <= content.bottom()) {
                        drawRow(hud, descriptor, row, appear);
                    }
                }
            } finally {
                Render2D.popScissor(graphics);
            }
        } finally {
            graphics.getMatrices().pop();
        }
    }

    public boolean mouseClicked(HudMouseEvent event) {
        Hud hudPolicy = Hud.getInstance();
        if (hudPolicy == null || !hudPolicy.isEnabled()) {
            finishClose();
            return false;
        }
        if (!open || event == null) {
            return false;
        }
        if (closing) {
            return true;
        }

        Hud hud = Hud.getInstance();
        if (hud == null) {
            close();
            return true;
        }

        float width = HudEditorControls.panelWidth();
        float height = height(hud);
        float mx = (float) event.x();
        float my = (float) event.y();

        
        float appear = clamp01(popupAnimation.get());
        float scale = 0.92F + 0.08F * appear;
        float shiftY = (1.0F - appear) * -u(6.0F);
        float cx = x + width * 0.5F;
        float cy = y + height * 0.5F;
        if (scale > 0.001F) {
            mx = cx + (mx - cx) / scale;
            my = cy + (my - cy - shiftY) / scale;
        }

        Bounds popup = popupBounds(width, height);
        if (!popup.contains(mx, my)) {
            if (event.button() == 0 || event.button() == 1) {
                close();
                return true;
            }
            return false;
        }

        
        if (event.button() == 1) {
            close();
            return true;
        }
        if (event.button() != 0) {
            return true;
        }

        Bounds content = contentBounds(width, height);
        if (!content.contains(mx, my)) {
            return true;
        }

        int index = 0;
        for (HudElementDescriptor descriptor : hud.hudElementRegistry().ordered()) {
            Bounds row = rowBounds(content, index++);
            if (row.contains(mx, my)) {
                boolean selected = hud.isElementSelected(descriptor.id());
                hud.setElementSelected(descriptor.id(), !selected);
                return true;
            }
        }
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!open || closing) {
            return false;
        }

        Hud hud = Hud.getInstance();
        if (hud == null || !hud.isEnabled()) {
            finishClose();
            return false;
        }

        float width = HudEditorControls.panelWidth();
        float height = height(hud);
        Bounds popup = popupBounds(width, height);
        if (!popup.contains((float) mouseX, (float) mouseY)) {
            return false;
        }

        float viewport = contentBounds(width, height).height();
        float content = hud.hudElementRegistry().ordered().size() * u(ROW_HEIGHT);
        float maxScroll = Math.max(0.0F, content - viewport);
        scrollTarget = clamp(scrollTarget - (float) scrollY * u(40.0F), 0.0F, maxScroll);
        return true;
    }

    private void drawHeader(float width, float alpha) {
        String title = LanguageManager.ui("hud_editor.elements");
        float titleSize = u(HEADER_TITLE_SIZE);
        float headerHeight = u(HEADER_HEIGHT);
        float titleY = HudEditorControls.visualCenteredTextY(title, titleSize, y, headerHeight - u(2.0F));

        
        Render2D.text(FontType.BOLD, title, x + u(SIDE_PADDING), titleY, titleSize,
                ThemeColors.hudTextColor(Math.round(250.0F * clamp01(alpha))));

        
        Render2D.rect(x + u(SIDE_PADDING), y + headerHeight - u(1.0F),
                width - u(SIDE_PADDING * 2.0F), u(1.0F), u(0.5F),
                ColorUtil.rgba(255, 255, 255, Math.round(18.0F * clamp01(alpha))));
    }

    private void drawRow(Hud hud, HudElementDescriptor descriptor, Bounds row, float alpha) {
        boolean selected = hud.isElementSelected(descriptor.id());
        float a = clamp01(alpha);

        String label = LanguageManager.settingOption("HUD", "Elements", descriptor.optionName());
        float textSize = u(14.0F);
        float textX = row.x();
        float textY = HudEditorControls.visualCenteredTextY(label, textSize, row.y(), row.height());
        int textColor = selected
                ? ThemeColors.hudTextColor(Math.round(250.0F * a))
                : ColorUtil.rgba(198, 203, 212, Math.round(235.0F * a));
        Render2D.text(FontType.BOLD, label, textX, textY, textSize, textColor);

        ReversibleAnimationTimeline switchAnimation = switchAnimations.computeIfAbsent(descriptor.id(), id -> {
            ReversibleAnimationTimeline animation = new ReversibleAnimationTimeline();
            animation.set(selected ? 1.0D : 0.0D);
            return animation;
        });
        switchAnimation.update();
        switchAnimation.run(selected ? 1.0D : 0.0D, 0.50D, Easings.EXPO_OUT, false);
        float switchX = x + HudEditorControls.panelWidth() - u(CHECK_RIGHT) - SmoothSwitchRenderer.WIDTH;
        float switchY = row.y() + (row.height() - SmoothSwitchRenderer.HEIGHT) * 0.5F;
        SmoothSwitchRenderer.renderSized(switchX, switchY, SmoothSwitchRenderer.WIDTH, SmoothSwitchRenderer.HEIGHT,
                a, switchAnimation.get());
    }

    private void clampScroll(Hud hud, float height) {
        float viewport = contentBounds(HudEditorControls.panelWidth(), height).height();
        float content = hud.hudElementRegistry().ordered().size() * u(ROW_HEIGHT);
        scrollTarget = clamp(scrollTarget, 0.0F, Math.max(0.0F, content - viewport));
    }

    private static float height(Hud hud) {
        int rows = hud == null ? 0 : hud.hudElementRegistry().ordered().size();
        float virtual = HEADER_HEIGHT + rows * ROW_HEIGHT + BOTTOM_PADDING;
        return u(Math.min(MAX_HEIGHT, virtual));
    }

    private Bounds popupBounds(float width, float height) {
        return new Bounds(x, y, width, height);
    }

    private Bounds contentBounds(float width, float height) {
        float contentTop = y + u(HEADER_HEIGHT);
        float contentBottom = y + height - u(BOTTOM_PADDING);
        return new Bounds(
                x + u(SIDE_PADDING),
                contentTop,
                Math.max(0.0F, width - u(SIDE_PADDING * 2.0F)),
                Math.max(0.0F, contentBottom - contentTop)
        );
    }

    private Bounds rowBounds(Bounds content, int index) {
        return new Bounds(
                content.x(),
                content.y() - scrollAnimation.get() + index * u(ROW_HEIGHT),
                content.width(),
                u(ROW_HEIGHT)
        );
    }

    private void finishClose() {
        open = false;
        closing = false;
        popupAnimation.set(0.0);
        scrollAnimation.set(0.0);
        scrollTarget = 0.0F;
    }

    private static float u(float value) {
        return HudEditorControls.u(value);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp01(float value) {
        return HudEditorControls.clamp01(value);
    }


}
