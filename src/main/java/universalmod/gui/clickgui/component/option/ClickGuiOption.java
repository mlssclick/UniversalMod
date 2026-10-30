package universalmod.gui.clickgui.component.option;

import net.minecraft.client.gui.DrawContext;
import universalmod.gui.clickgui.CharacterEvent;
import universalmod.gui.clickgui.KeyEvent;
import universalmod.gui.clickgui.MouseButtonEvent;
import universalmod.setting.Setting;
import universalmod.gui.clickgui.ClickGuiController;
import universalmod.service.language.LanguageManager;
import universalmod.render.animation.Easings;
import universalmod.render.animation.ReversibleAnimationTimeline;
import universalmod.render.animation.SmoothAnimation;
import universalmod.render.color.ColorUtil;
import universalmod.theme.ThemeColors;
import universalmod.theme.ThemeRender;
import universalmod.render.Render2D;
import universalmod.render.optimize.UiFrameContext;
import universalmod.render.font.FontType;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

public abstract class ClickGuiOption {
    protected static final float HEIGHT = 22.0f;

    private final String name;
    private Setting setting;
    protected float x;
    protected float y;
    protected float width;
    protected float height;
    protected float scale;
    protected float alpha;
    protected float animationAlpha;
    private final ReversibleAnimationTimeline visibilityAnimation = new ReversibleAnimationTimeline();
    private final Map<String, SmoothAnimation> textScrollAnimations = new HashMap<>();
    private BooleanSupplier visibleSupplier = () -> true;
    private float fadeMultiplier = 1.0f;
    private boolean interactiveThisFrame;
    private boolean popupAnchorVisibleThisFrame;

    protected ClickGuiOption(String name) {
        this.name = name;
        visibilityAnimation.set(1.0);
    }

    public float getHeight() {
        return HEIGHT;
    }

    public final void render(DrawContext graphics, float x, float y, float width, float scale, float alpha,
                             float clipX, float clipY, float clipWidth, float clipHeight) {
        float sw = width * scale;
        float sh = getHeight() * scale;
        this.x = x;
        this.y = y;
        this.width = sw;
        this.height = sh;
        this.scale = scale;
        this.animationAlpha = alpha;
        this.alpha = alpha * fadeMultiplier;
        this.popupAnchorVisibleThisFrame = intersects(x, y, sw, sh, clipX, clipY, clipWidth, clipHeight);
        this.interactiveThisFrame = popupAnchorVisibleThisFrame && this.alpha > 0.01f && sw > 0.0f && sh > 0.0f;
        if (!popupAnchorVisibleThisFrame) {
            closePopup();
            return;
        }
        renderControl(graphics, x, y, sw, sh, scale, this.alpha);
    }

    public void resetInteractionFrame() {
        interactiveThisFrame = false;
        popupAnchorVisibleThisFrame = false;
    }

    public void setSetting(Setting setting) {
        this.setting = setting;
        if (setting == null) {
            setVisibleSupplier(null);
            return;
        }
        setVisibleSupplier(setting::isVisible);
    }

    public void setVisibleSupplier(BooleanSupplier visibleSupplier) {
        this.visibleSupplier = visibleSupplier == null ? () -> true : visibleSupplier;
        visibilityAnimation.set(this.visibleSupplier.getAsBoolean() ? 1.0 : 0.0);
    }

    public void updateVisibility() {
        boolean visible = visibleSupplier.getAsBoolean();
        visibilityAnimation.run(visible ? 1.0 : 0.0, 0.20, Easings.CUBIC_OUT, false);
        visibilityAnimation.update();
        if (!visible) {
            closePopup();
        }
    }

    public float visibilityProgress() {
        return clamp(visibilityAnimation.get(), 0.0f, 1.0f);
    }

    public boolean isVisibleForRender() {
        return visibilityProgress() > 0.01f;
    }

    public boolean isVisibleForInteraction() {
        return interactiveThisFrame && visibleSupplier.getAsBoolean() && visibilityProgress() > 0.85f;
    }

    public void renderOverlay(DrawContext graphics) {
    }

    public void setFadeMultiplier(float fadeMultiplier) {
        this.fadeMultiplier = clamp(fadeMultiplier, 0.0f, 1.0f);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        return false;
    }

    public boolean wantsMousePriority(MouseButtonEvent event) {
        return false;
    }

    public boolean hasOpenPopup() {
        return false;
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        return false;
    }

    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        return false;
    }

    public boolean keyPressed(KeyEvent event) {
        return false;
    }

    public boolean charTyped(CharacterEvent event) {
        return false;
    }

    public void closePopup() {
    }

    protected abstract void renderControl(DrawContext graphics, float x, float y, float width, float height, float scale, float alpha);

    protected boolean popupAnchorVisible() {
        return popupAnchorVisibleThisFrame;
    }

    protected float popupScreenWidth() {
        return Render2D.getFixedScaledWidth();
    }

    protected float popupScreenHeight() {
        return Render2D.getFixedScaledHeight();
    }

    protected String name() {
        return name;
    }

    protected String displayName() {
        return setting == null ? LanguageManager.translate(name) : setting.getDisplayName();
    }

    protected boolean hovered(double mouseX, double mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private boolean intersects(float x, float y, float width, float height, float clipX, float clipY, float clipWidth, float clipHeight) {
        return UiFrameContext.intersects(x, y, width, height, clipX, clipY, clipWidth, clipHeight);
    }

    protected float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    protected float textWidth(String text, float size) {
        return Render2D.textWidth(FontType.SEMIBOLD, text, size);
    }

    protected String fitText(String text, float size, float maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0.0f) {
            return text == null ? "" : text;
        }
        if (Render2D.textWidth(FontType.BOLD, text, size) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        float ellipsisWidth = Render2D.textWidth(FontType.BOLD, ellipsis, size);
        if (ellipsisWidth >= maxWidth) {
            return "";
        }
        int end = text.length();
        while (end > 0 && Render2D.textWidth(FontType.BOLD, text.substring(0, end), size) + ellipsisWidth > maxWidth) {
            end--;
        }
        return end <= 0 ? "" : text.substring(0, end) + ellipsis;
    }

    protected void renderPlainLabel(float x, float y, float size, float maxWidth) {
        Render2D.text(FontType.BOLD, displayName(), x, y, size, labelColor());
    }


    protected void renderBouncingCenteredText(
            DrawContext graphics, String text, float boxX, float boxY, float boxWidth, float boxHeight,
            float textSize, int textColor, float horizontalPadding
    ) {
        String safe = text == null ? "" : text;
        float innerX = boxX + horizontalPadding;
        float innerWidth = Math.max(1.0f, boxWidth - horizontalPadding * 2.0f);
        float fullWidth = Render2D.textWidth(FontType.BOLD, safe, textSize);
        float textY = OptionStyle.centeredTextY(FontType.BOLD, safe, textSize, boxY, boxHeight);
        float drawX;
        if (fullWidth <= innerWidth + 0.01f) {
            drawX = boxX + (boxWidth - fullWidth) * 0.5f;
        } else {
            float overflow = fullWidth - innerWidth;
            long travelMs = Math.max(1300L, 1500L + Math.round(overflow * 22.0f));
            long period = travelMs * 2L;
            long phase = Math.floorMod(UiFrameContext.nowMillis(), period);
            float t = phase <= travelMs
                    ? phase / (float) travelMs
                    : 2.0f - phase / (float) travelMs;

            float eased = t * t * (3.0f - 2.0f * t);
            drawX = innerX - overflow * eased;
        }

        Render2D.pushScissor(graphics, innerX, boxY, innerWidth, boxHeight);
        try {
            Render2D.text(FontType.BOLD, safe, drawX, textY, textSize, textColor);
        } finally {
            Render2D.popScissor(graphics);
        }
    }

    protected int labelColor() {
        float visibleAlpha = alpha >= 0.98f ? 1.0f : alpha;
        return ThemeColors.clickGuiTextColor(255, visibleAlpha);
    }

    protected int iconColor() {
        float visibleAlpha = alpha >= 0.98f ? 1.0f : alpha;
        return ThemeColors.clickGuiMutedColor(255, visibleAlpha);
    }

    protected void renderScrollingText(
            DrawContext graphics,
            String key,
            String text,
            float textX,
            float textY,
            float textSize,
            int textColor,
            float clipX,
            float clipY,
            float clipWidth,
            float clipHeight,
            float hoverX,
            float hoverY,
            float hoverWidth,
            float hoverHeight
    ) {
        if (text == null || text.isEmpty() || clipWidth <= 0.25f || clipHeight <= 0.25f) {
            return;
        }

        float textWidth = textWidth(text, textSize);
        float overflow = Math.max(0.0f, textWidth - clipWidth + 3.0f * scale);
        SmoothAnimation scrollAnimation = textScrollAnimations.computeIfAbsent(key, ignored -> {
            SmoothAnimation animation = new SmoothAnimation();
            animation.set(0.0);
            return animation;
        });

        boolean hover = overflow > 0.25f && hovered(ClickGuiController.mouseX(), ClickGuiController.mouseY(), hoverX, hoverY, hoverWidth, hoverHeight);
        float target = 0.0f;
        if (hover) {
            long segment = Math.max(950L, 1200L + Math.round(overflow * 18.0f));
            target = ((UiFrameContext.nowMillis() / segment) & 1L) == 0L ? -overflow : 0.0f;
        }
        scrollAnimation.run(target, hover ? 0.55 : 0.22, Easings.CUBIC_OUT, true);
        scrollAnimation.update();

        float scroll = scrollAnimation.get();
        float fadeWidth = Math.min(10.0f * scale, Math.max(2.0f, clipWidth * 0.35f));
        float leftFade = overflow > 0.25f && scroll < -0.4f ? 0.78f : 0.0f;
        float rightFade = overflow > 0.25f && scroll > -overflow + 0.4f ? 0.88f : 0.0f;

        Render2D.pushScissor(graphics, clipX, clipY, clipWidth, clipHeight);
        if (leftFade > 0.0f || rightFade > 0.0f) {
            Render2D.textFade(FontType.SEMIBOLD, text, textX + scroll, textY, textSize, textColor, clipX, clipX + clipWidth, fadeWidth, leftFade, rightFade);
        } else {
            Render2D.text(FontType.SEMIBOLD, text, textX + scroll, textY, textSize, textColor);
        }
        Render2D.popScissor(graphics);
    }

    protected void renderGlassControl(float x, float y, float width, float height, float radius, float alphaMultiplier) {
        float defaultAlpha = ThemeRender.defaultAlpha(alphaMultiplier);
        if (defaultAlpha > 0.001f) {
            Render2D.glass(x, y, width, height, radius, color(255, 255, 255, 72, defaultAlpha), 0.64f * defaultAlpha, 42.0f, color(255, 255, 255, 150, defaultAlpha), 0.16f * defaultAlpha, true, 0.22f, 0.003f, 1.0f, 0.0f);
            Render2D.glassOutline(x, y, width, height, radius, 0.5f, color(255, 255, 255, 176, defaultAlpha), 1.0f, 35.0f, color(168, 168, 174, 118, defaultAlpha), 0.42f * defaultAlpha, false, 0.45f, 0.0015f, 1.0f, 0.0f, 0.0f);
        }
    }

    protected float defaultThemeAlpha(float alpha) {
        return ThemeRender.defaultAlpha(alpha);
    }

    protected int color(int red, int green, int blue, int alpha, float alphaMultiplier) {
        return ColorUtil.rgba(red, green, blue, (int) (alpha * alphaMultiplier));
    }
}
