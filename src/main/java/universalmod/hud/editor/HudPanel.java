package universalmod.hud.editor;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import universalmod.hud.ElementComponent;
import universalmod.hud.ElementManager;
import universalmod.hud.ElementScreen;
import universalmod.hud.HudElement;
import universalmod.render.animation.Easings;
import universalmod.render.animation.SmoothAnimation;
import universalmod.render.Render2D;
import universalmod.render.optimize.UiFrameContext;
import universalmod.render.font.FontType;
import universalmod.service.language.LanguageManager;
import universalmod.theme.ThemeColors;
import universalmod.hud.HudStyleOverrides;

public abstract class HudPanel implements HudElement {
    protected static final FontType TITLE_FONT = FontType.BOLD;
    protected static final FontType TEXT_FONT = FontType.BOLD;
    private static final float CONTENT_ANIM = 0.24F;
    protected final MinecraftClient mc = MinecraftClient.getInstance();
    protected final ElementComponent drag;
    private final String elementName;
    private float animatedWidth;
    private float animatedHeight;
    private float logicalWidth;
    private float logicalHeight;
    private float renderScale = 1.0F;
    private float logicalHitExpandLeft;
    private float logicalHitExpandTop;
    private float logicalHitExpandRight;
    private float logicalHitExpandBottom;
    private long lastFrameMs;
    private final SmoothAnimation contentAnimation = new SmoothAnimation();
    private boolean enabled = true;

    protected HudPanel(String id, String title, float defaultX, float defaultY, float width, float height) {
        this.elementName = title;
        this.drag = ElementManager.getInstance()
                .register("hud." + id, title, defaultX, defaultY)
                .minimumSize(4.0F, 4.0F);
        this.animatedWidth = width;
        this.animatedHeight = height;
        this.logicalWidth = Math.max(1.0F, (float) Math.ceil(width));
        this.logicalHeight = Math.max(1.0F, (float) Math.ceil(height));
        this.drag.size(this.logicalWidth, this.logicalHeight);
    }

    public String elementName() {
        return elementName;
    }


    public String elementDisplayName() {
        return LanguageManager.settingOption("HUD", "Elements", elementName);
    }

    public String elementId() {
        return drag.id();
    }

    public boolean hit(float mouseX, float mouseY) {
        return drag.containsDragBounds(mouseX, mouseY, 3.0F);
    }

    public boolean moving() {
        return drag.moving();
    }

    public float x() { return drag.x(); }
    public float y() { return drag.y(); }

    public float width() { return drag.width(); }

    public float height() { return drag.height(); }

    protected float logicalWidth() { return logicalWidth; }

    protected float logicalHeight() { return logicalHeight; }

    public float hudScale() {
        return renderScale;
    }

    public float dragTiltDegrees() {
        return drag.dragTiltDegrees();
    }

    public float dragScale() {
        return drag.dragScale();
    }

    public boolean supportsHudScale() {
        return true;
    }


    public boolean usesProjectedScissor() {
        return false;
    }


    protected float visualBaseScale() {
        return HudStyleOverrides.VISUAL_BASE_SCALE;
    }


    public void prepareFrame() {
    }

    public float configuredHudScale() {
        if (!supportsHudScale()) {
            return 1.0F;
        }
        float baseScale = Math.max(0.0001F, visualBaseScale());
        float percentScale = HudStyleOverrides.getInstance().getSizePercent(elementId()) / 100.0F;
        return clamp(
                percentScale * baseScale,
                HudStyleOverrides.SIZE_MIN_PERCENT / 100.0F * baseScale,
                HudStyleOverrides.SIZE_MAX_PERCENT / 100.0F * baseScale
        );
    }

    public void prepareHudScale(float scale) {
        if (!supportsHudScale()) {
            renderScale = 1.0F;
            return;
        }
        float baseScale = Math.max(0.0001F, visualBaseScale());
        float minScale = HudStyleOverrides.SIZE_MIN_PERCENT / 100.0F * baseScale;
        float maxScale = HudStyleOverrides.SIZE_MAX_PERCENT / 100.0F * baseScale;
        renderScale = clamp(scale, minScale, maxScale);
        applyScaledGeometry();
        drag.clamp(ElementScreen.current());
    }

    public void finishHudScale() {
        if (!supportsHudScale()) {
            renderScale = 1.0F;
            return;
        }
        applyScaledGeometry();
        drag.clamp(ElementScreen.current());
    }

    protected float localMouseX(float mouseX) {
        float scale = Math.max(0.0001F, renderScale);
        return drag.x() + (mouseX - drag.x()) / scale;
    }

    protected float localMouseY(float mouseY) {
        float scale = Math.max(0.0001F, renderScale);
        return drag.y() + (mouseY - drag.y()) / scale;
    }

    public void setHudVisible(boolean visible) {
        enabled = visible;
        drag.visible(visible);
    }

    protected boolean selected() {
        return enabled;
    }

    protected void contentVisible(boolean visible) {
        drag.visible(enabled && visible);
    }

    protected float contentAlpha(boolean targetVisible) {
        contentAnimation.update();
        contentAnimation.run(targetVisible ? 1.0 : 0.0, CONTENT_ANIM, targetVisible ? Easings.EXPO_OUT : Easings.EXPO_IN, true);

        float alpha = contentAnimation.get();
        contentVisible(targetVisible || alpha > 0.01F || contentAnimation.isAlive());
        return alpha;
    }

    protected boolean editPreview() {
        return mc.currentScreen instanceof ChatScreen;
    }

    protected void size(float targetWidth, float targetHeight) {
        float delta = deltaSeconds();
        animatedWidth = smooth(animatedWidth, targetWidth, delta, 8.0F);
        animatedHeight = smooth(animatedHeight, targetHeight, delta, 8.0F);
        if (Math.abs(animatedWidth - targetWidth) < 0.2F) {
            animatedWidth = targetWidth;
        }
        if (Math.abs(animatedHeight - targetHeight) < 0.2F) {
            animatedHeight = targetHeight;
        }
        logicalWidth = Math.max(1.0F, (float) Math.ceil(animatedWidth));
        logicalHeight = Math.max(1.0F, (float) Math.ceil(animatedHeight));
        applyScaledGeometry();
    }

    protected void sizeImmediate(float targetWidth, float targetHeight) {
        if (Float.isFinite(targetWidth)) {
            animatedWidth = Math.max(1.0F, targetWidth);
            logicalWidth = Math.max(1.0F, (float) Math.ceil(targetWidth));
        }
        if (Float.isFinite(targetHeight)) {
            animatedHeight = Math.max(1.0F, targetHeight);
            logicalHeight = Math.max(1.0F, (float) Math.ceil(targetHeight));
        }
        applyScaledGeometry();
    }

    protected void hitExpansion(float left, float top, float right, float bottom) {
        logicalHitExpandLeft = Math.max(0.0F, left);
        logicalHitExpandTop = Math.max(0.0F, top);
        logicalHitExpandRight = Math.max(0.0F, right);
        logicalHitExpandBottom = Math.max(0.0F, bottom);
        applyScaledHitExpansion();
    }

    private void applyScaledGeometry() {
        drag.size(
                Math.max(1.0F, logicalWidth * renderScale),
                Math.max(1.0F, logicalHeight * renderScale)
        );
        applyScaledHitExpansion();
    }

    private void applyScaledHitExpansion() {
        drag.hitExpansion(
                logicalHitExpandLeft * renderScale,
                logicalHitExpandTop * renderScale,
                logicalHitExpandRight * renderScale,
                logicalHitExpandBottom * renderScale
        );
    }

    protected String trimToWidth(String text, FontType font, float size, float width) {
        if (text == null) {
            return "";
        }
        if (Render2D.textWidth(font, text, size) <= width) {
            return text;
        }
        String suffix = "..";
        if (Render2D.textWidth(font, suffix, size) > width) {
            return suffix;
        }

        int low = 0;
        int high = text.length();
        int best = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            String candidate = text.substring(0, mid) + suffix;
            if (Render2D.textWidth(font, candidate, size) <= width) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return best <= 0 ? suffix : text.substring(0, best) + suffix;
    }

    protected static float smooth(float current, float target, float deltaSeconds, float speed) {
        float factor = (float) (1.0D - Math.pow(0.001D, Math.max(0.0F, deltaSeconds) * speed));
        return current + (target - current) * factor;
    }

    protected static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    protected static String twoDigits(int value) {
        int safeValue = Math.max(0, Math.min(99, value));
        return safeValue < 10 ? "0" + safeValue : Integer.toString(safeValue);
    }

    protected static int hudTextColor(int alpha) {
        return ThemeColors.hudTextColor(alpha);
    }

    protected static int hudMutedColor(int alpha) {
        return ThemeColors.hudMutedColor(alpha);
    }

    protected static int hudAccentColor(int alpha) {
        return ThemeColors.hudAccentColor(alpha);
    }

    private float deltaSeconds() {
        if (UiFrameContext.active()) {
            return UiFrameContext.deltaSeconds();
        }
        long now = UiFrameContext.nowMillis();
        if (lastFrameMs == 0L) {
            lastFrameMs = now;
            return 1.0F / 60.0F;
        }
        float delta = Math.min(0.1F, Math.max(0.0F, (now - lastFrameMs) / 1000.0F));
        lastFrameMs = now;
        return delta;
    }
}
