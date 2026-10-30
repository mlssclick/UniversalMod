package universalmod.gui.clickgui.component;


import universalmod.gui.ui.UiMetrics;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import universalmod.module.Module;
import universalmod.setting.Setting;
import universalmod.setting.bind.KeyBind;
import universalmod.setting.BindSetting;
import universalmod.gui.clickgui.component.option.ClickGuiOption;
import universalmod.gui.ui.control.SmoothSwitchRenderer;
import universalmod.gui.ui.control.SwitchControl;
import universalmod.render.animation.Easings;
import universalmod.render.animation.ReversibleAnimationTimeline;
import universalmod.render.color.ColorUtil;
import universalmod.render.Render2D;
import universalmod.render.optimize.UiFrameContext;
import universalmod.render.font.FontType;

import java.util.ArrayList;
import java.util.List;

public class ModuleOption {
    private static final float OPTION_GAP = UiMetrics.OPTION_GAP;
    private static final float SETTINGS_PADDING_X = UiMetrics.SETTINGS_PADDING_X;
    private static final float SETTINGS_PADDING_Y = UiMetrics.SETTINGS_PADDING_Y;
    private static final float OPTION_SCALE = UiMetrics.OPTION_SCALE;

    private final Module module;
    private final ModuleBindingCoordinator bindingCoordinator;
    private List<ClickGuiOption> settings = List.of();
    private List<Setting<?>> settingRefs = List.of();
    private final List<ClickGuiOption> interactiveSettings = new ArrayList<>(16);
    private final DetachedOptionLayout detachedLayout = new DetachedOptionLayout();
    private final boolean hasSupportedSettings;
    private boolean settingsInitialized;
    private final BindSetting bindSetting;
    private final ReversibleAnimationTimeline expandedAnimation = new ReversibleAnimationTimeline();
    private final SwitchControl enabledControl = new SwitchControl();
    private final ReversibleAnimationTimeline bindAnimation = new ReversibleAnimationTimeline();

    private boolean expanded;
    private boolean binding;
    private long lastAnimationFrame = Long.MIN_VALUE;
    private float cardX;
    private float cardY;
    private float cardWidth;
    private float cardHeight;
    private float cardScale = 1.0f;
    private boolean cardInteractive;

    public ModuleOption(Module module, ModuleBindingCoordinator bindingCoordinator) {
        this.module = module;
        this.bindingCoordinator = java.util.Objects.requireNonNull(bindingCoordinator, "bindingCoordinator");
        this.hasSupportedSettings = ModuleOptionFactory.hasSupportedSettings(module);
        this.bindSetting = module.getBindSetting();
        expandedAnimation.set(0.0);
        enabledControl.reset(module.isEnabled());
        bindAnimation.set(0.0);
    }


    public void updateAnimation() {
        updateAnimation(true);
    }

    public void updateAnimation(boolean updateSettings) {
        long frame = UiFrameContext.active() ? UiFrameContext.frameId() : Long.MIN_VALUE;
        boolean resumed = frame != Long.MIN_VALUE
                && lastAnimationFrame != Long.MIN_VALUE
                && frame - lastAnimationFrame > 1L;
        if (resumed) {


            enabledControl.reset(module.isEnabled());
            bindAnimation.set(binding ? 1.0 : 0.0);
        } else {
            enabledControl.update(module.isEnabled(), 0.5, Easings.QUART_OUT);
            bindAnimation.run(binding ? 1.0 : 0.0, 0.18, Easings.CUBIC_OUT, true);
            bindAnimation.update();
        }
        expandedAnimation.run(expanded ? 1.0 : 0.0, 0.24, Easings.CUBIC_OUT, true);
        expandedAnimation.update();
        if (updateSettings && settingsInitialized) {
            for (ClickGuiOption option : settings) {
                option.updateVisibility();
            }
        }
        if (frame != Long.MIN_VALUE) lastAnimationFrame = frame;
    }

    public void renderCard(
            GuiGraphics graphics,
            float x,
            float y,
            float width,
            float height,
            float scale,
            float alpha,
            float clipX,
            float clipY,
            float clipWidth,
            float clipHeight
    ) {
        beginInteractionFrame();
        cardX = x;
        cardY = y;
        cardWidth = width * scale;
        cardHeight = height * scale;
        cardScale = scale;
        cardInteractive = alpha > 0.01f && intersects(x, y, cardWidth, cardHeight, clipX, clipY, clipWidth, clipHeight);

        float enabled = enabledControl.progress();
        float bind = bindAnimation.get();
        if (hasSettings() && (expanded || expandedAnimation.get() > 0.01f)) {
            ensureSettings();
        }
        float settingsProgress = hasSettings() ? Math.max(0.0f, Math.min(1.0f, expandedAnimation.get())) : 0.0f;
        float settingsVisibleHeight = hasSettings() ? computeSettingsHeight() * scale * settingsProgress : 0.0f;
        float shellHeight = cardHeight + settingsVisibleHeight;
        float radius = 6.0f * scale;


        float cardBottomRadius = settingsProgress > 0.01f ? 0.0f : radius;
        int cardFill = ColorUtil.rgba(7, 10, 21, Math.round((54.0f + 26.0f * enabled) * alpha));
        Render2D.rect(x, y, cardWidth, cardHeight,
                radius, radius, cardBottomRadius, cardBottomRadius, cardFill);

        if (settingsVisibleHeight > 0.25f) {
            int settingsFill = ColorUtil.rgba(7, 10, 21,
                    Math.round((38.0f + 12.0f * enabled) * alpha * settingsProgress));
            Render2D.rect(x, y + cardHeight, cardWidth, settingsVisibleHeight,
                    0.0f, 0.0f, radius, radius, settingsFill);
        }


        String title = module.getDisplayName();
        int titleColor = ColorUtil.rgba(
                Math.round(232.0f + 20.0f * enabled),
                Math.round(234.0f + 18.0f * enabled),
                Math.round(244.0f + 11.0f * enabled),
                Math.round(255.0f * alpha)
        );

        float badgeHeight = 13.0f * scale;
        float badgeY = y + (cardHeight - badgeHeight) * 0.5f;
        String bindText = displayBind();
        boolean bindUnbound = !binding && (bindSetting == null || bindSetting.getValue() == null || !bindSetting.getValue().isBound());
        float bindBadgeWidth = ModuleCardRenderer.bindBadge(graphics, bindText, bindUnbound,
                x + 6.5f * scale, badgeY, scale, alpha, bind);

        float switchWidth = SmoothSwitchRenderer.WIDTH * scale;
        float switchHeight = SmoothSwitchRenderer.HEIGHT * scale;
        float switchX = x + cardWidth - switchWidth - 6.5f * scale;
        float switchY = y + (cardHeight - switchHeight) * 0.5f;

        float textSize = 7.7f * scale;
        float textX = x + 6.5f * scale + bindBadgeWidth + 6.5f * scale;
        float availableTextWidth = Math.max(1.0f, switchX - 7.0f * scale - textX);
        float textY = y + (cardHeight - textSize) * 0.5f - 0.80f * scale;
        ModuleCardRenderer.scrollingTitle(graphics, title, textX, textY, textSize, titleColor,
                availableTextWidth, y, cardHeight);
        ModuleCardRenderer.toggle(switchX, switchY, scale, alpha, enabled);

        renderSettings(graphics, x, y + cardHeight, width, scale, alpha, clipX, clipY, clipWidth, clipHeight);
    }

    private String displayBind() {
        return ModuleBindLabel.format(binding, bindSetting);
    }

    private void renderSettings(GuiGraphics graphics, float x, float settingsY, float width, float scale, float alpha,
                                float clipX, float clipY, float clipWidth, float clipHeight) {
        float progress = expandedAnimation.get();
        if (!hasSettings() || progress <= 0.01f) {
            return;
        }
        ensureSettings();

        float rawHeight = computeSettingsHeight();
        float fullHeight = rawHeight * scale;
        float visibleHeight = fullHeight * progress;
        if (visibleHeight <= 0.5f) {
            return;
        }

        float panelX = x;
        float panelY = settingsY;
        float panelWidth = width * scale;

        Render2D.pushScissor(graphics, panelX, panelY, panelWidth, visibleHeight);
        try {
            float optionScale = scale * OPTION_SCALE;
            float optionY = panelY + SETTINGS_PADDING_Y * scale;
            for (ClickGuiOption option : settings) {
                float visibility = option.visibilityProgress();
                if (visibility <= 0.01f) {
                    continue;
                }
                float slotHeight = (option.getHeight() * OPTION_SCALE + OPTION_GAP) * scale;
                option.setFadeMultiplier(progress * visibility);
                float optionWidth = Math.max(1.0f, (panelWidth - SETTINGS_PADDING_X * 2.0f * scale) / Math.max(0.0001f, optionScale));
                option.render(graphics,
                        panelX + SETTINGS_PADDING_X * scale,
                        optionY - (1.0f - visibility) * 5.0f * scale,
                        optionWidth,
                        optionScale,
                        alpha,
                        clipX,
                        clipY,
                        clipWidth,
                        clipHeight);
                if (option.isVisibleForInteraction()) {
                    interactiveSettings.add(option);
                }
                optionY += slotHeight * visibility;
            }
        } finally {
            Render2D.popScissor(graphics);
        }
    }

    public Module module() {
        return module;
    }

    public String bindLabel() {
        return displayBind();
    }

    public float enabledProgress() {
        return Math.max(0.0f, Math.min(1.0f, enabledControl.progress()));
    }


    public void beginExternalBinding() {
        startBinding();
    }

    public boolean isBinding() {
        return binding;
    }

    void cancelBindingFromCoordinator() {
        if (!binding) {
            return;
        }
        binding = false;
        bindAnimation.run(0.0, 0.18, Easings.CUBIC_OUT);
    }


    public boolean captureExternalMouseBind(MouseButtonEvent event) {
        if (!binding || event == null) return false;
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            setBind(KeyBind.mouse(event.button()));
        }
        return true;
    }


    public float detachedSettingsHeight(float optionScaleFactor) {
        if (!hasSettings()) return 0.0f;
        detachedLayout.prepare(settings, Math.max(0.2f, optionScaleFactor));
        return detachedLayout.scrollHeight();
    }

    public void renderDetachedSettings(
            GuiGraphics graphics,
            float x, float y, float width, float height,
            float scale, float alpha, float scroll
    ) {
        beginInteractionFrame();
        if (!hasSettings()) {
            return;
        }
        ensureSettings();
        float reveal = Math.max(0.0F, Math.min(1.0F, expandedAnimation.get()));
        DetachedSettingsRenderer.render(
                graphics, settings, detachedLayout, interactiveSettings, reveal,
                x, y, width, height, scale, alpha, scroll
        );
    }

    public void renderDetachedOverlay(GuiGraphics graphics) {
        if (!hasSettings()) {
            return;
        }
        ensureSettings();
        DetachedSettingsRenderer.renderOverlay(graphics, settings);
    }

    public void renderOverlay(GuiGraphics graphics) {
        if (!hasSettings() || expandedAnimation.get() <= 0.01f) {
            return;
        }
        ensureSettings();
        for (ClickGuiOption option : settings) {
            if (option.isVisibleForRender()) {
                option.renderOverlay(graphics);
            }
        }
    }

    public void renderRow(float panelX, float rowY, float panelWidth, float rowHeight, float scale, float alpha) {
        beginInteractionFrame();
        cardX = panelX + 5.0f * scale;
        cardY = rowY;
        cardWidth = Math.max(0.0f, panelWidth - 10.0f * scale);
        cardHeight = rowHeight * scale;
        cardScale = scale;
        cardInteractive = alpha > 0.01f;

        float enabled = enabledControl.progress();
        float switchWidth = SmoothSwitchRenderer.WIDTH * scale;
        float switchX = cardX + cardWidth - switchWidth - 2.0f * scale;
        float switchY = cardY + (cardHeight - SmoothSwitchRenderer.HEIGHT * scale) * 0.5f;

        Render2D.text(FontType.BOLD, module.getDisplayName(), cardX + 4.0f * scale, cardY + 2.5f * scale, 4.0f * scale,
                ColorUtil.rgba(230, 230, 230, Math.round(245.0f * alpha)));
        renderToggle(switchX, switchY, scale, alpha, enabled);
    }

    private void renderToggle(float x, float y, float scale, float alpha, float enabled) {
        SmoothSwitchRenderer.render(x, y, scale, alpha, enabled);
    }

    public float renderSettingsBackground(float panelX, float settingsTop, float width, float scale, float alpha, float clipBottom) {
        float progress = expandedAnimation.get();
        if (!hasSettings() || progress <= 0.01f) {
            return 0.0f;
        }
        float height = computeSettingsHeight() * scale * progress;
        float clipped = Math.max(0.0f, Math.min(height, clipBottom - settingsTop));
        if (clipped > 0.0f) {
            float boxX = panelX + 2.5f * scale;
            float boxY = settingsTop;
            float boxWidth = Math.max(0.0f, width - 5.0f * scale);
            float radius = 6.0f * scale;
            int extensionFill = ColorUtil.rgba(7, 10, 21, Math.round((38.0f + 12.0f * enabledControl.progress()) * alpha * progress));
            Render2D.rect(boxX, boxY, boxWidth, clipped, 0.0f, 0.0f, radius, radius, extensionFill);
        }
        return height;
    }

    public void renderSettingsContent(GuiGraphics graphics, float panelX, float settingsTop, float width, float scale, float alpha, float clipBottom) {
        renderSettings(graphics, panelX + 2.5f * scale, settingsTop - 25.0f * scale, Math.max(1.0f, width / Math.max(0.0001f, scale) - 5.0f), scale, alpha,
                panelX, settingsTop, width, Math.max(0.0f, clipBottom - settingsTop));
    }

    public void beginInteractionFrame() {
        cardInteractive = false;
        for (ClickGuiOption option : interactiveSettings) {
            option.resetInteractionFrame();
        }
        interactiveSettings.clear();
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (mouseClickedPopup(event, doubled)) {
            return true;
        }
        if (mouseClickedSettings(event, doubled)) {
            return true;
        }
        if (binding) {
            if (event.button() != GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                setBind(KeyBind.mouse(event.button()));
            }
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && hovered(event.x(), event.y(), cardX, cardY, cardWidth, cardHeight)) {
            startBinding();
            return true;
        }
        if (event.button() == 0 && hovered(event.x(), event.y(), cardX, cardY, cardWidth, cardHeight)) {
            module.toggle();
            return true;
        }
        if (event.button() == 1 && hovered(event.x(), event.y(), cardX, cardY, cardWidth, cardHeight) && hasSettings()) {
            if (!expanded) {
                ensureSettings();
            }
            expanded = !expanded;
            expandedAnimation.run(expanded ? 1.0 : 0.0, 0.24, Easings.CUBIC_OUT);
            closePopupsExcept(null);
            return true;
        }
        return false;
    }

    public boolean mouseClickedSettings(MouseButtonEvent event, boolean doubled) {
        if (!hasSettings() || expandedAnimation.get() <= 0.85f) {
            return false;
        }
        ClickGuiOption handled = null;
        for (ClickGuiOption option : interactiveSettings) {
            if (!option.isVisibleForInteraction()) {
                continue;
            }
            if (option.wantsMousePriority(event) && option.mouseClicked(event, doubled)) {
                handled = option;
                break;
            }
        }
        if (handled == null) {
            for (int i = interactiveSettings.size() - 1; i >= 0; i--) {
                ClickGuiOption option = interactiveSettings.get(i);
                if (!option.isVisibleForInteraction()) {
                    continue;
                }
                if (option.mouseClicked(event, doubled)) {
                    handled = option;
                    break;
                }
            }
        }
        if (handled != null) {
            closePopupsExcept(handled);
            return true;
        }
        closePopupsExcept(null);
        return false;
    }

    public boolean mouseClickedPopup(MouseButtonEvent event, boolean doubled) {
        if (!hasSettings() || expandedAnimation.get() <= 0.01f) {
            return false;
        }
        boolean hasOpenPopup = false;
        ClickGuiOption handled = null;
        for (int i = interactiveSettings.size() - 1; i >= 0; i--) {
            ClickGuiOption option = interactiveSettings.get(i);
            if (!option.isVisibleForRender() || !option.hasOpenPopup()) {
                continue;
            }
            hasOpenPopup = true;
            if (option.wantsMousePriority(event) && option.mouseClicked(event, doubled)) {
                handled = option;
                break;
            }
        }
        if (handled != null) {
            closePopupsExcept(handled);
            return true;
        }
        if (hasOpenPopup) {
            closePopupsExcept(null);
            return true;
        }
        return false;
    }

    public boolean mouseClickedRow(MouseButtonEvent event, boolean doubled) {
        if (binding) {
            if (event.button() != GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                setBind(KeyBind.mouse(event.button()));
            }
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && hovered(event.x(), event.y(), cardX, cardY, cardWidth, cardHeight)) {
            startBinding();
            return true;
        }
        if (event.button() == 0 && hovered(event.x(), event.y(), cardX, cardY, cardWidth, cardHeight)) {
            module.toggle();
            return true;
        }
        if (event.button() == 1 && hovered(event.x(), event.y(), cardX, cardY, cardWidth, cardHeight) && hasSettings()) {
            if (!expanded) {
                ensureSettings();
            }
            expanded = !expanded;
            expandedAnimation.run(expanded ? 1.0 : 0.0, 0.24, Easings.CUBIC_OUT);
            closePopupsExcept(null);
            return true;
        }
        return false;
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        if (!hasSettings() || expandedAnimation.get() <= 0.01f) {
            return false;
        }
        for (int i = interactiveSettings.size() - 1; i >= 0; i--) {
            ClickGuiOption option = interactiveSettings.get(i);
            if (option.isVisibleForInteraction() && option.mouseReleased(event)) {
                return true;
            }
        }
        return false;
    }

    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (!hasSettings() || expandedAnimation.get() <= 0.01f) {
            return false;
        }
        for (int i = interactiveSettings.size() - 1; i >= 0; i--) {
            ClickGuiOption option = interactiveSettings.get(i);
            if (option.isVisibleForInteraction() && option.mouseDragged(event, dragX, dragY)) {
                return true;
            }
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!hasSettings() || expandedAnimation.get() <= 0.01f) {
            return false;
        }
        for (int i = interactiveSettings.size() - 1; i >= 0; i--) {
            ClickGuiOption option = interactiveSettings.get(i);
            if (!option.isVisibleForRender() || !option.hasOpenPopup()) {
                continue;
            }
            if (option.mouseScrolled(mouseX, mouseY, scrollY)) {
                closePopupsExcept(option);
                return true;
            }
        }
        for (int i = interactiveSettings.size() - 1; i >= 0; i--) {
            ClickGuiOption option = interactiveSettings.get(i);
            if (!option.isVisibleForInteraction() || option.hasOpenPopup()) {
                continue;
            }
            if (option.mouseScrolled(mouseX, mouseY, scrollY)) {
                closePopupsExcept(option);
                return true;
            }
        }
        return false;
    }

    public boolean keyPressed(KeyEvent event) {
        if (binding) {
            if (event.key() == GLFW.GLFW_KEY_DELETE || event.key() == GLFW.GLFW_KEY_BACKSPACE || event.key() == GLFW.GLFW_KEY_ESCAPE) {
                setBind(KeyBind.NONE);
            } else {
                setBind(KeyBind.keyboard(event.key()));
            }
            return true;
        }
        if (!hasSettings() || expandedAnimation.get() <= 0.01f) {
            return false;
        }
        for (int i = interactiveSettings.size() - 1; i >= 0; i--) {
            ClickGuiOption option = interactiveSettings.get(i);
            if (option.isVisibleForInteraction() && option.keyPressed(event)) {
                return true;
            }
        }
        return false;
    }

    public boolean charTyped(CharacterEvent event) {
        if (!hasSettings() || expandedAnimation.get() <= 0.01f) {
            return false;
        }
        for (int i = interactiveSettings.size() - 1; i >= 0; i--) {
            ClickGuiOption option = interactiveSettings.get(i);
            if (option.isVisibleForInteraction() && option.charTyped(event)) {
                return true;
            }
        }
        return false;
    }

    public float extraHeight() {
        if (!hasSettings()) {
            return 0.0f;
        }
        return Math.max(0.0f, computeSettingsHeight()) * expandedAnimation.get();
    }

    public float getAnimatedExtraHeight() {
        return extraHeight();
    }

    public float totalHeight() {
        return 27.0f + extraHeight();
    }

    public boolean hasSettings() {
        return hasSupportedSettings;
    }

    public void revealSettings() {
        if (!hasSettings() || expanded) {
            return;
        }
        ensureSettings();
        expanded = true;
        expandedAnimation.run(1.0, 0.18, Easings.CUBIC_OUT, false);
    }

    public void hideSettings() {
        if (!hasSettings()) {
            return;
        }
        ensureSettings();
        expanded = false;
        expandedAnimation.run(0.0, 0.18, Easings.CUBIC_OUT, false);
        closePopupsExcept(null);
        binding = false;
    }


    public void restartSettingsReveal() {
        if (!hasSettings()) {
            return;
        }
        ensureSettings();
        expanded = true;
        expandedAnimation.set(0.0);
        expandedAnimation.run(1.0, 0.22, Easings.CUBIC_OUT);
        closePopupsExcept(null);
    }

    public void warmupText() {
        Render2D.warmupText(FontType.BOLD, module.getDisplayName(), 7.7f);
        for (Setting<?> setting : module.getSettings()) {
            Render2D.warmupText(FontType.SEMIBOLD, setting.getDisplayName(), 9.0f);
            ModuleOptionTextSupport.warmupSettingValue(setting);
        }
    }

    private void ensureSettings() {
        if (settingsInitialized) {
            return;
        }
        settings = ModuleOptionFactory.createSettings(module);
        settingRefs = ModuleOptionFactory.supportedSettings(module);
        settingsInitialized = true;
    }

    private float computeSettingsHeight() {
        if (!settingsInitialized) {
            return 0.0f;
        }
        float height = SETTINGS_PADDING_Y * 2.0f;
        for (ClickGuiOption option : settings) {
            float visibility = option.visibilityProgress();
            if (visibility > 0.01f) {
                height += (option.getHeight() * OPTION_SCALE + OPTION_GAP) * visibility;
            }
        }
        return Math.max(0.0f, height);
    }

    private void startBinding() {
        bindingCoordinator.activate(this);
        binding = true;
        bindAnimation.run(1.0, 0.18, Easings.CUBIC_OUT);
        closePopupsExcept(null);
    }

    private void setBind(KeyBind bind) {
        bindSetting.setValue(bind);
        binding = false;
        bindingCoordinator.release(this);
        bindAnimation.run(0.0, 0.18, Easings.CUBIC_OUT);
    }

    private void closePopupsExcept(ClickGuiOption keepOpen) {
        for (ClickGuiOption option : settings) {
            if (option != keepOpen) {
                option.closePopup();
            }
        }
    }


    private boolean hovered(double mouseX, double mouseY, float x, float y, float width, float height) {
        return cardInteractive && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
    private boolean intersects(float x, float y, float width, float height, float clipX, float clipY, float clipWidth, float clipHeight) {
        return ModuleCardMotion.intersects(x, y, width, height, clipX, clipY, clipWidth, clipHeight);
    }

}
