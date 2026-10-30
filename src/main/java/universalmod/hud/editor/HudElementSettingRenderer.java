package universalmod.hud.editor;

import universalmod.render.Render2D;
import universalmod.render.animation.Easings;
import universalmod.render.animation.SmoothAnimation;
import universalmod.render.color.ColorUtil;
import universalmod.render.font.FontType;
import universalmod.gui.ui.control.ColorSwatchRenderer;
import universalmod.setting.BooleanSetting;
import universalmod.setting.ColorSetting;
import universalmod.setting.ModeSetting;
import universalmod.setting.NumberSetting;
import universalmod.setting.Setting;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


final class HudElementSettingRenderer {
    private static final float BOOLEAN_HEIGHT = 16.0F;
    private static final float COLOR_HEIGHT = 32.0F;

    private final Map<BooleanSetting, SmoothAnimation> checkboxAnimations = new HashMap<>();
    private final Map<NumberSetting, SmoothAnimation> numberAnimations = new HashMap<>();
    private final Map<NumberSetting, SmoothAnimation> numberPressAnimations = new HashMap<>();

    void reset() {
        checkboxAnimations.clear();
        numberAnimations.clear();
        numberPressAnimations.clear();
    }

    void render(float panelX, float rowY, Setting setting, float alpha,
                boolean numberDragging, NumberSetting activeNumber) {
        if (setting instanceof BooleanSetting booleanSetting) {
            renderBoolean(panelX, rowY, booleanSetting, alpha);
        } else if (setting instanceof NumberSetting numberSetting) {
            renderNumber(panelX, rowY, numberSetting, alpha, numberDragging && activeNumber == numberSetting);
        } else if (setting instanceof ColorSetting colorSetting) {
            renderColor(panelX, rowY, colorSetting, alpha);
        } else if (setting instanceof ModeSetting modeSetting) {
            renderMode(panelX, rowY, modeSetting, alpha);
        }
    }

    private void renderBoolean(float panelX, float y, BooleanSetting setting, float alpha) {
        float x = panelX + u(16.0F);
        float width = HudEditorControls.panelWidth() - u(32.0F);
        String label = setting.getDisplayName();
        float textY = HudEditorControls.visualCenteredTextY(label, u(14.0F), y, u(BOOLEAN_HEIGHT));
        Render2D.text(FontType.BOLD, label, x, textY, u(14.0F), applyAlpha(0xFFE6EAF0, alpha));
        SmoothAnimation animation = checkboxAnimations.computeIfAbsent(setting, key -> {
            SmoothAnimation created = new SmoothAnimation();
            created.set(setting.getValue() ? 1.0 : 0.0);
            return created;
        });
        animation.update();
        animation.run(setting.getValue() ? 1.0 : 0.0, 0.50, Easings.EXPO_OUT, false);
        HudEditorControls.checkbox(x + width - 14.0F, y, alpha, animation.get());
    }

    private void renderNumber(float panelX, float y, NumberSetting setting, float alpha, boolean dragging) {
        double range = Math.max(0.000001, setting.getMax() - setting.getMin());
        float target = clamp01((float) ((setting.getValue() - setting.getMin()) / range));
        SmoothAnimation animation = numberAnimations.computeIfAbsent(setting, key -> {
            SmoothAnimation created = new SmoothAnimation();
            created.set(target);
            return created;
        });
        animation.update();
        if (dragging) {
            animation.set(target);
        } else {
            animation.run(target, 0.25, Easings.EXPO_OUT, true);
        }
        SmoothAnimation pressAnimation = numberPressAnimations.computeIfAbsent(setting, key -> {
            SmoothAnimation created = new SmoothAnimation();
            created.set(0.0);
            return created;
        });
        pressAnimation.update();
        pressAnimation.run(dragging ? 1.0 : 0.0, 0.15, Easings.QUAD_OUT, true);
        HudEditorControls.slider(setting.getDisplayName(), panelX + u(16.0F), y,
                HudEditorControls.panelWidth() - u(32.0F), alpha,
                clamp01(animation.get()), clamp01(pressAnimation.get()));
    }

    private void renderMode(float panelX, float y, ModeSetting setting, float alpha) {
        List<String> modes = setting.getModes();
        if (modes.size() < 2) {
            return;
        }
        String left = setting.getDisplayMode(modes.get(0));
        String right = setting.getDisplayMode(modes.get(1));
        boolean rightSelected = setting.is(modes.get(1));
        HudEditorControls.segmented(setting.getDisplayName(), left, right,
                panelX + u(16.0F), y, HudEditorControls.panelWidth() - u(32.0F), alpha,
                rightSelected ? 1.0F : 0.0F, rightSelected);
    }

    private void renderColor(float panelX, float y, ColorSetting setting, float alpha) {
        float x = panelX + u(16.0F);
        float width = HudEditorControls.panelWidth() - u(32.0F);
        String label = setting.getDisplayName();
        float textY = y + (u(COLOR_HEIGHT) - Render2D.textHeight(FontType.BOLD, label, u(14.0F))) * 0.5F;
        Render2D.text(FontType.BOLD, label, x, textY, u(14.0F), applyAlpha(0xFFE6EAF0, alpha));
        Color color = new Color(setting.getValue(), true);
        float swatchSize = u(24.0F);
        float swatchX = x + width - u(24.0F);
        float swatchY = y + u(4.0F);
        ColorSwatchRenderer.render(swatchX, swatchY, swatchSize, color, alpha);
    }

    private static float u(float value) {
        return HudEditorControls.u(value);
    }

    private static float clamp01(float value) {
        return HudEditorControls.clamp01(value);
    }

    private static int applyAlpha(int color, float multiplier) {
        int a = Math.round(((color >>> 24) & 255) * clamp01(multiplier));
        return (color & 0x00FFFFFF) | (a << 24);
    }
}
