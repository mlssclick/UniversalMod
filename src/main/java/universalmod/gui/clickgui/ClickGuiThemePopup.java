package universalmod.gui.clickgui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import universalmod.gui.clickgui.component.option.ClickGuiOption;
import universalmod.gui.clickgui.component.option.ColorOption;
import universalmod.gui.clickgui.component.option.SliderOption;
import universalmod.gui.clickgui.component.option.OptionStyle;
import universalmod.lifecycle.Manager;
import universalmod.module.misc.CustomTheme;
import universalmod.render.Render2D;
import universalmod.render.animation.Easings;
import universalmod.render.animation.SmoothAnimation;
import universalmod.render.color.ColorUtil;
import universalmod.render.font.FontType;
import universalmod.render.surface.GuiSurface;
import universalmod.service.language.LanguageManager;
import universalmod.theme.ThemeColors;

import java.util.ArrayList;
import java.util.List;

import static universalmod.gui.clickgui.ClickGuiController.*;


final class ClickGuiThemePopup {
    private static final SmoothAnimation OPEN = new SmoothAnimation();
    private static final List<ClickGuiOption> options = new ArrayList<>();
    private static CustomTheme boundTheme;
    private static boolean open;
    private static float px, py, pw, ph;
    private static float languageX, languageY, languageW, languageH;

    private static final float WIDTH = 214.0f;
    private static final float PADDING = 9.0f;
    private static final float HEADER = 25.0f;
    private static final float LANGUAGE_ROW_HEIGHT = 22.0f;
    private static final float ROW_GAP = 2.0f;
    private static final float WINDOW_GAP = 8.0f;

    private ClickGuiThemePopup() {}

    static void reset() {
        open = false;
        OPEN.set(0.0);
        closeChildPopups();
    }

    private static void ensureOptions() {
        CustomTheme theme = CustomTheme.getInstance();
        if (theme == null || !themeAvailable(theme) || theme == boundTheme) return;
        boundTheme = theme;
        options.clear();
        options.add(new SliderOption(theme.getClickGuiSizeSetting(), "%"));
        options.add(new ColorOption(theme.getClickGuiTitleColorSetting()));
        options.add(new ColorOption(theme.getClickGuiAccentColorSetting()));
        options.add(new ColorOption(theme.getClickGuiModuleColorSetting()));
        options.add(new ColorOption(theme.getClickGuiEnabledModuleColorSetting()));
        options.add(new ColorOption(theme.getClickGuiPanelColorSetting()));
        options.add(new ColorOption(theme.getHudAccentColorSetting()));
         
         
        List<universalmod.setting.Setting<?>> themeSettings = List.of(
                theme.getClickGuiSizeSetting(),
                theme.getClickGuiTitleColorSetting(),
                theme.getClickGuiAccentColorSetting(),
                theme.getClickGuiModuleColorSetting(),
                theme.getClickGuiEnabledModuleColorSetting(),
                theme.getClickGuiPanelColorSetting(),
                theme.getHudAccentColorSetting());
        for (int i = 0; i < options.size(); i++) {
            options.get(i).setSetting(themeSettings.get(i));
        }
    }

    static void render(GuiGraphics graphics, float alpha, double mouseX, double mouseY) {
        if (!themeAvailable(CustomTheme.getInstance())) {
            reset();
            return;
        }
        ensureOptions();
        OPEN.update();
        float progress = clamp01(OPEN.get());
        if (progress <= 0.01f || options.isEmpty()) return;


        float guiScale = SESSION.lastGuiScale;
        float themeScale = Math.max(0.01f, ThemeColors.clickGuiScale());
        float s = guiScale / themeScale;
        float rows = 0.0f;
        for (ClickGuiOption option : options) rows += option.getHeight() + ROW_GAP;
        rows -= ROW_GAP;
        float designHeight = HEADER + PADDING + LANGUAGE_ROW_HEIGHT + ROW_GAP + rows + PADDING;
        pw = WIDTH * s;
        ph = designHeight * s;


        float targetX = SESSION.lastGuiX - pw - WINDOW_GAP * s;
        float targetY = SESSION.lastGuiY + GUI_HEIGHT * guiScale - ph;
        px = targetX;
        py = targetY;
        float a = alpha * progress;
        float popupScale = 0.92f + 0.08f * progress;
        float shiftY = (1.0f - progress) * -6.0f * s;
        float centerX = px + pw * 0.5f;
        float centerY = py + ph * 0.5f;

        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY + shiftY);
        graphics.pose().scale(popupScale);
        graphics.pose().translate(-centerX, -centerY);

        new GuiSurface(px, py, pw, ph, 6.5f * s, a, 0.24f,
                ThemeColors.clickGuiPanelColor(255, 1.0f),
                ThemeColors.clickGuiAccentColor(255, 1.0f)).render(graphics);
        Render2D.outline(px, py, pw, ph, 6.5f * s, 0.5f * s,
                ThemeColors.clickGuiAccentColor(72, a));

        String title = LanguageManager.translate("clickgui.theme.title");
        if (title == null || title.equals("clickgui.theme.title")) title = "Theme";
        float titleSize = 7.0f * s;
        float titleW = Render2D.textWidth(FontType.BOLD, title, titleSize);
        Render2D.text(FontType.BOLD, title,
                px + (pw - titleW) * 0.5f,
                py + 8.0f * s,
                titleSize,
                ThemeColors.clickGuiTextColor(248, a));

        float contentX = px + PADDING * s;
        float contentY = py + HEADER * s;
        float contentWDesign = WIDTH - PADDING * 2.0f;
        float clipY = contentY;
        float clipH = ph - HEADER * s - PADDING * s;

        languageX = contentX;
        languageY = contentY;
        languageW = contentWDesign * s;
        languageH = LANGUAGE_ROW_HEIGHT * s;
        String languageLabel = LanguageManager.settingName("Custom Theme", "Language");
        String languageValue = boundTheme == null ? "" : boundTheme.getLanguageDisplayName();


        float languageTextSize = OptionStyle.TITLE_SIZE * s;
        float languageTextY = OptionStyle.centeredTextY(
                FontType.BOLD, languageLabel, languageTextSize, languageY, languageH);
        Render2D.text(FontType.BOLD, languageLabel,
                languageX, languageTextY, languageTextSize,
                ColorUtil.rgba(241, 239, 246, Math.round(255.0f * a)));

        float languageValueW = Render2D.textWidth(FontType.BOLD, languageValue, languageTextSize);
        boolean languageHovered = inside(mouseX, mouseY, languageX, languageY, languageW, languageH);
        Render2D.text(FontType.BOLD, languageValue,
                languageX + languageW - languageValueW,
                OptionStyle.centeredTextY(FontType.BOLD, languageValue, languageTextSize, languageY, languageH),
                languageTextSize,
                languageHovered
                        ? ThemeColors.clickGuiAccentColor(238, a)
                        : ColorUtil.rgba(157, 153, 165, Math.round(242.0f * a)));
        contentY += (LANGUAGE_ROW_HEIGHT + ROW_GAP) * s;

        for (ClickGuiOption option : options) {
            option.updateVisibility();
            float vp = option.visibilityProgress();
            if (vp <= 0.01f) continue;
            option.setFadeMultiplier(vp);
            option.render(graphics, contentX, contentY, contentWDesign, s, a,
                    contentX, clipY, contentWDesign * s, clipH);
            contentY += (option.getHeight() + ROW_GAP) * s * vp;
        }


        for (ClickGuiOption option : options) option.renderOverlay(graphics);
        graphics.pose().popMatrix();
    }

    static boolean mouseClicked(MouseButtonEvent event) {
        if (!themeAvailable(CustomTheme.getInstance())) {
            reset();
            return false;
        }
        ensureOptions();
        float s = SESSION.lastGuiScale;
        float cardX = SESSION.lastGuiX + 8.0f * s;
        float cardY = SESSION.lastGuiY + (GUI_HEIGHT - 43.0f) * s;
        float cardW = (SIDE_WIDTH - 16.0f) * s;
        float cardH = 39.0f * s;
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                && inside(event.x(), event.y(), cardX, cardY, cardW, cardH)) {
            open = !open;
            if (open) {
                OPEN.set(0.0);
                OPEN.run(1.0, 0.22, Easings.CUBIC_OUT);
            } else {
                OPEN.run(0.0, 0.22, Easings.CUBIC_OUT);
                closeChildPopups();
            }
            return true;
        }
        if (!open && OPEN.get() <= 0.01f) return false;

        if (open && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && inside(event.x(), event.y(), languageX, languageY, languageW, languageH)) {
            if (boundTheme != null) {
                boundTheme.cycleLanguage();
                closeChildPopups();
            }
            return true;
        }


        for (ClickGuiOption option : options) {
            if (option.wantsMousePriority(event) && option.mouseClicked(event, false)) {
                if (option.hasOpenPopup()) closeOtherChildPopups(option);
                return true;
            }
        }
        if (!inside(event.x(), event.y(), px, py, pw, ph)) return false;
        for (ClickGuiOption option : options) {
            if (option.isVisibleForInteraction() && option.mouseClicked(event, false)) {
                if (option.hasOpenPopup()) closeOtherChildPopups(option);
                return true;
            }
        }
        return true;
    }

    static boolean mouseReleased(MouseButtonEvent event) {
        if (!themeAvailable(CustomTheme.getInstance())) return false;
        boolean handled = false;
        for (ClickGuiOption option : options) handled |= option.mouseReleased(event);
        return handled;
    }

    static boolean mouseDragged(MouseButtonEvent event) {
        if (!themeAvailable(CustomTheme.getInstance())) return false;
        boolean handled = false;
        for (ClickGuiOption option : options) handled |= option.mouseDragged(event, 0.0, 0.0);
        return handled;
    }

    static boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!themeAvailable(CustomTheme.getInstance()) || !open) return false;
        for (ClickGuiOption option : options) {
            if (option.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        }
        return inside(mouseX, mouseY, px, py, pw, ph);
    }

    static boolean keyPressed(KeyEvent event) {
        if (!themeAvailable(CustomTheme.getInstance()) || !open) return false;
        for (ClickGuiOption option : options) if (option.keyPressed(event)) return true;
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            open = false;
            closeChildPopups();
            return true;
        }
        return false;
    }

    private static boolean themeAvailable(CustomTheme theme) {
        if (theme == null) return false;
        var modules = Manager.getModules();
        return modules == null || modules.isVisible(theme);
    }

    private static void closeChildPopups() {
        for (ClickGuiOption option : options) option.closePopup();
    }

    private static void closeOtherChildPopups(ClickGuiOption keepOpen) {
        for (ClickGuiOption option : options) {
            if (option != keepOpen) option.closePopup();
        }
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
