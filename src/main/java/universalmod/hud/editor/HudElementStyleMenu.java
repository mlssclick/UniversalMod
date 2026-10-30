package universalmod.hud.editor;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import universalmod.hud.HudMouseEvent;
import org.lwjgl.glfw.GLFW;
import universalmod.hud.HudStyleOverrides;
import universalmod.hud.registry.HudElementId;
import universalmod.gui.ui.control.SmoothSwitchRenderer;
import universalmod.module.render.Hud;
import universalmod.render.Render2D;
import universalmod.render.Render2DCoordinateSpace;
import universalmod.render.animation.Easings;
import universalmod.render.animation.SmoothAnimation;
import universalmod.render.font.FontType;
import universalmod.service.language.LanguageManager;
import universalmod.setting.BooleanSetting;
import universalmod.setting.ColorSetting;
import universalmod.setting.NumberSetting;
import universalmod.setting.ModeSetting;
import universalmod.setting.Setting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HudElementStyleMenu {
    private static final HudElementStyleMenu INSTANCE = new HudElementStyleMenu();
    private static final float BOTTOM_PADDING = 16.0F;

    private final SmoothAnimation popupAnimation = new SmoothAnimation();
    private final SmoothAnimation modeAnimation = new SmoothAnimation();
    private final Map<Setting, SmoothAnimation> visibilityAnimations = new HashMap<>();
    private final SmoothAnimation sizeAnimation = new SmoothAnimation();
    private final HudElementSettingRenderer settingRenderer = new HudElementSettingRenderer();
    private final SmoothAnimation sizePressAnimation = new SmoothAnimation();

    private HudPanel anchor;
    private String elementId;
    private HudElementId hudElementId;
    private String elementTitle;
    private float x;
    private float y;
    private HudStyleMenuPlacement.Side side = HudStyleMenuPlacement.Side.RIGHT;
    private HudStyleMenuPlacement.Side requestedSide = HudStyleMenuPlacement.Side.RIGHT;
    private boolean closing;

    private final ToggleControl toggleControl = new ToggleControl();
    private long frameNanos = System.nanoTime();
    private float deltaSeconds = 1.0F / 60.0F;

    private boolean sizeDragging;
    private NumberSetting activeNumber;
    private boolean numberDragging;

    private boolean modeDragging;
    private boolean modeMoved;
    private float modePressX;

    private final HudColorPickerOverlay colorPicker = new HudColorPickerOverlay();

    private HudElementStyleMenu() {
        popupAnimation.set(0.0);
        modeAnimation.set(0.0);
    }

    public static HudElementStyleMenu getInstance() {
        return INSTANCE;
    }

    public void open(HudPanel panel, float mouseX, float mouseY) {
        Hud currentHudPolicy = Hud.getInstance();
        if (panel == null || currentHudPolicy == null || !currentHudPolicy.isEnabled()) {
            close();
            return;
        }
        if (isOpen() && panel.elementId().equalsIgnoreCase(elementId) && !closing) {
            close();
            return;
        }
        if (closing) {
            finishClose();
        }
        anchor = panel;
        elementId = panel.elementId();
        String legacyElementName = panel.elementName();
        Hud currentHud = Hud.getInstance();
        hudElementId = currentHud == null ? null : currentHud.hudElementRegistry().byElement(panel)
                .map(descriptor -> descriptor.id())
                .orElseGet(() -> HudElementId.fromPersistentOrLegacy(legacyElementName).orElse(null));
        elementTitle = panel.elementDisplayName();
        requestedSide = mouseX < panel.x() + panel.width() * 0.5F
                ? HudStyleMenuPlacement.Side.LEFT
                : HudStyleMenuPlacement.Side.RIGHT;
        side = requestedSide;
        closing = false;
        sizeDragging = false;
        numberDragging = false;
        activeNumber = null;
        modeDragging = false;
        modeMoved = false;
        colorPicker.reset();
        settingRenderer.reset();
        visibilityAnimations.clear();
        Hud hud = Hud.getInstance();
        toggleControl.reset(hud != null && hudElementId != null && hud.isElementSelected(hudElementId));
        sizeAnimation.set(sizeProgress());
        sizePressAnimation.set(0.0);
        modeAnimation.set(hud != null && "2".equals(currentViewMode(hud)) ? 1.0 : 0.0);
        for (Setting setting : relevantSettings()) {
            SmoothAnimation animation = new SmoothAnimation();
            animation.set(setting.isVisible() ? 1.0 : 0.0);
            visibilityAnimations.put(setting, animation);
        }
        place(true);
        popupAnimation.set(0.0);
        popupAnimation.run(1.0, 0.22, Easings.CUBIC_OUT, true);
    }

    public void close() {
        if (!isOpen() || closing) {
            return;
        }
        finishSizeDrag();
        numberDragging = false;
        activeNumber = null;
        modeDragging = false;
        colorPicker.close();
        closing = true;
        popupAnimation.run(0.0, 0.22, Easings.CUBIC_OUT, false);
    }

    public boolean isOpen() {
        return elementId != null && !elementId.isBlank();
    }

    public void updateBeforeHudRender() {
        updateSizeDrag();
        updateNumberDrag();
        updateModeDrag();
        updateColorDrag();
    }

    public void render(DrawContext graphics) {
        if (!isOpen()) {
            return;
        }
        Hud hudPolicy = Hud.getInstance();
        if (hudPolicy == null || !hudPolicy.isEnabled()) {
            finishClose();
            return;
        }
        updateAnimations();
        place(false);
        float appear = clamp01(popupAnimation.get());
        if (closing && appear <= 0.001F && !popupAnimation.isAlive()) {
            finishClose();
            return;
        }
        if (appear <= 0.001F) {
            return;
        }
        Layout layout = layout();
        float scale = 0.92F + 0.08F * appear;
        float shiftY = (1.0F - appear) * -u(6.0F);
        float centerX = layout.x + HudEditorControls.panelWidth() * 0.5F;
        float centerY = layout.y + layout.height * 0.5F;
        graphics.getMatrices().push();
        try {
            graphics.getMatrices().translate(centerX, centerY + shiftY, 0.0F);
            graphics.getMatrices().scale(scale, scale, 1.0F);
            graphics.getMatrices().translate(-centerX, -centerY, 0.0F);
            HudEditorControls.panel(layout.x, layout.y, layout.height, appear);
            Render2D.text(FontType.BOLD, elementTitle, layout.x + u(16.0F), layout.y + u(20.0F), u(16.0F),
                    alpha(0xFFF3F6FA, appear));
            renderToggle(layout, appear);
            HudEditorControls.divider(layout.x, layout.y + u(56.0F), appear);
            float rowY = layout.y + u(HudEditorControls.HEADER_HEIGHT);
            boolean hasPrevious = false;
            if (hasSizeRow()) {
                renderSize(layout, rowY, appear);
                rowY += HudStyleMenuMetrics.sizeRowHeight();
                hasPrevious = true;
            }
            if (hasViewMode()) {
                if (hasPrevious) {
                    rowY += u(HudEditorControls.ROW_GAP);
                }
                renderMode(layout, rowY, appear);
                rowY += HudStyleMenuMetrics.modeRowHeight();
                hasPrevious = true;
            }
            for (Setting setting : relevantSettings()) {
                float visibility = visibility(setting);
                if (visibility <= 0.001F) {
                    continue;
                }
                if (hasPrevious) {
                    rowY += u(HudEditorControls.ROW_GAP) * visibility;
                }
                settingRenderer.render(layout.x, rowY, setting, appear * visibility, numberDragging, activeNumber);
                rowY += HudStyleMenuMetrics.settingHeight(setting) * visibility;
                hasPrevious = true;
            }
            colorPicker.render(graphics, layout.x, layout.y, appear);
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
        if (!isOpen() || event == null) {
            return false;
        }
        if (closing) {
            return true;
        }
        updateAnimations();
        place(false);
        Layout layout = layout();
        Pointer pointer = localPointer(event, layout);
        float mx = pointer.x();
        float my = pointer.y();
        if (colorPicker.mouseClicked(mx, my, event.button(), layout.x, layout.y)) {
            return true;
        }
        if (!inside(mx, my, layout.x, layout.y, HudEditorControls.panelWidth(), layout.height)) {
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
        if (handleToggleClick(mx, my, layout)) {
            return true;
        }
        return handleSettingRowsClick(mx, my, layout);
    }

    private Pointer localPointer(HudMouseEvent event, Layout layout) {
        float mx = (float) event.x();
        float my = (float) event.y();
        float appear = clamp01(popupAnimation.get());
        float scale = 0.92F + 0.08F * appear;
        float shiftY = (1.0F - appear) * -u(6.0F);
        float cx = layout.x + HudEditorControls.panelWidth() * 0.5F;
        float cy = layout.y + layout.height * 0.5F;
        if (scale > 0.001F) {
            mx = cx + (mx - cx) / scale;
            my = cy + (my - cy - shiftY) / scale;
        }
        return new Pointer(mx, my);
    }

    private boolean handleToggleClick(float mx, float my, Layout layout) {
        float toggleX = layout.x + HudEditorControls.panelWidth() - u(44.0F);
        float toggleY = layout.y + u(20.0F);
        if (!inside(mx, my, toggleX, toggleY, SmoothSwitchRenderer.WIDTH, SmoothSwitchRenderer.HEIGHT)) {
            return false;
        }
        Hud hud = Hud.getInstance();
        if (hud != null && hudElementId != null) {
            toggleControl.sync(hud.isElementSelected(hudElementId));
            hud.setElementSelected(hudElementId, toggleControl.toggle());
        }
        return true;
    }

    private boolean handleSettingRowsClick(float mx, float my, Layout layout) {
        float rowY = layout.y + u(HudEditorControls.HEADER_HEIGHT);
        boolean hasPrevious = false;
        if (hasSizeRow()) {
            HudEditorControls.SliderBounds bounds = HudStyleMenuMetrics.sizeBounds(layout.x, rowY);
            if (inside(mx, my, bounds.hitX(), bounds.hitY(), bounds.hitWidth(), bounds.hitHeight())) {
                sizeDragging = true;
                updateSizeFromMouse(mx, layout, rowY);
                colorPicker.close();
                return true;
            }
            rowY += HudStyleMenuMetrics.sizeRowHeight();
            hasPrevious = true;
        }
        if (hasViewMode()) {
            if (hasPrevious) {
                rowY += u(HudEditorControls.ROW_GAP);
            }
            HudEditorControls.SegmentBounds bounds = HudStyleMenuMetrics.modeBounds(layout.x, rowY);
            if (inside(mx, my, bounds.x(), bounds.y(), bounds.width(), bounds.height())) {
                modeDragging = true;
                modeMoved = false;
                modePressX = mx;
                colorPicker.close();
                return true;
            }
            rowY += HudStyleMenuMetrics.modeRowHeight();
            hasPrevious = true;
        }
        return handleCustomSettingClick(mx, my, layout, rowY, hasPrevious);
    }

    private boolean handleCustomSettingClick(float mx, float my, Layout layout, float rowY, boolean hasPrevious) {
        for (Setting setting : relevantSettings()) {
            float visibility = visibility(setting);
            if (visibility <= 0.5F) {
                continue;
            }
            if (hasPrevious) {
                rowY += u(HudEditorControls.ROW_GAP) * visibility;
            }
            float height = HudStyleMenuMetrics.settingHeight(setting) * visibility;
            if (inside(mx, my, layout.x + u(16.0F), rowY,
                    HudEditorControls.panelWidth() - u(32.0F), height)) {
                handleSettingClick(setting, mx, my, layout, rowY);
                return true;
            }
            rowY += height;
            hasPrevious = true;
        }
        colorPicker.close();
        return true;
    }

    private void handleSettingClick(Setting setting, float mx, float my, Layout layout, float rowY) {
        if (setting instanceof BooleanSetting booleanSetting) {
            booleanSetting.setValue(!booleanSetting.getValue());
            colorPicker.close();
            return;
        }
        if (setting instanceof NumberSetting numberSetting) {
            HudEditorControls.SliderBounds bounds = HudStyleMenuMetrics.numberBounds(numberSetting, layout.x, rowY);
            if (inside(mx, my, bounds.hitX(), bounds.hitY(), bounds.hitWidth(), bounds.hitHeight())) {
                activeNumber = numberSetting;
                numberDragging = true;
                updateNumberFromMouse(numberSetting, mx, layout, rowY);
            }
            colorPicker.close();
            return;
        }
        if (setting instanceof ColorSetting colorSetting) {
            colorPicker.toggle(colorSetting);
            return;
        }
        if (setting instanceof ModeSetting modeSetting) {
            List<String> modes = modeSetting.getModes();
            if (modes.size() >= 2) {
                HudEditorControls.SegmentBounds bounds = HudStyleMenuMetrics.genericModeBounds(layout.x, rowY, modeSetting.getDisplayName());
                modeSetting.setValue(mx < bounds.x() + bounds.width() * 0.5F ? modes.get(0) : modes.get(1));
            }
            colorPicker.close();
        }
    }

    private void renderToggle(Layout layout, float alpha) {
        Hud hud = Hud.getInstance();
        boolean enabled = hud != null && hudElementId != null && hud.isElementSelected(hudElementId);
        toggleControl.sync(enabled);
        toggleControl.update(deltaSeconds);
        toggleControl.render(layout.x + HudEditorControls.panelWidth() - u(44.0F), layout.y + u(20.0F), alpha);
    }

    private void renderSize(Layout layout, float rowY, float alpha) {
        float target = sizeProgress();
        sizeAnimation.update();
        if (sizeDragging) {
            sizeAnimation.set(target);
        } else {
            sizeAnimation.run(target, 0.25, Easings.EXPO_OUT, true);
        }
        sizePressAnimation.update();
        sizePressAnimation.run(sizeDragging ? 1.0 : 0.0, 0.15, Easings.QUAD_OUT, true);
        float x = layout.x + u(16.0F);
        float width = HudEditorControls.panelWidth() - u(32.0F);
        HudEditorControls.slider(LanguageManager.ui("hud_editor.size"), x, rowY, width, alpha,
                clamp01(sizeAnimation.get()), clamp01(sizePressAnimation.get()));
    }

    private void renderMode(Layout layout, float rowY, float alpha) {
        Hud hud = Hud.getInstance();
        boolean second = hud != null && "2".equals(currentViewMode(hud));
        if (!modeDragging) {
            modeAnimation.run(second ? 1.0 : 0.0, 0.22, Easings.CUBIC_IN_OUT, true);
        }
        modeAnimation.update();
        HudEditorControls.segmented(LanguageManager.ui("hud_editor.mode"), "1", "2",
                layout.x + u(16.0F), rowY, HudEditorControls.panelWidth() - u(32.0F), alpha,
                clamp01(modeAnimation.get()), second);
    }

    private List<Setting> relevantSettings() {
        return HudElementSettingCatalog.resolve(Hud.getInstance(), hudElementId);
    }

    private void updateAnimations() {
        long now = System.nanoTime();
        deltaSeconds = Math.max(0.0F, Math.min(0.05F, (float) ((now - frameNanos) / 1_000_000_000.0)));
        frameNanos = now;
        popupAnimation.update();
        colorPicker.updateAnimation();
        for (Setting setting : relevantSettings()) {
            SmoothAnimation animation = visibilityAnimations.computeIfAbsent(setting, key -> {
                SmoothAnimation created = new SmoothAnimation();
                created.set(setting.isVisible() ? 1.0 : 0.0);
                return created;
            });
            animation.run(setting.isVisible() ? 1.0 : 0.0, 0.22, Easings.CUBIC_OUT, true);
            animation.update();
        }
    }

    private float visibility(Setting setting) {
        SmoothAnimation animation = visibilityAnimations.get(setting);
        return animation == null ? (setting.isVisible() ? 1.0F : 0.0F) : clamp01(animation.get());
    }

    private void updateSizeDrag() {
        if (!sizeDragging || !isOpen()) {
            return;
        }
        MouseState mouse = mouseState();
        if (mouse == null || !mouse.pressed) {
            finishSizeDrag();
            return;
        }
        Layout layout = layout();
        updateSizeFromMouse(mouse.x, layout, layout.y + u(HudEditorControls.HEADER_HEIGHT));
    }

    private void updateNumberDrag() {
        if (!numberDragging || activeNumber == null || !isOpen()) {
            return;
        }
        MouseState mouse = mouseState();
        if (mouse == null || !mouse.pressed) {
            numberDragging = false;
            activeNumber = null;
            return;
        }
        Layout layout = layout();
        float rowY = settingY(activeNumber, layout);
        updateNumberFromMouse(activeNumber, mouse.x, layout, rowY);
    }

    private void updateModeDrag() {
        if (!modeDragging || !isOpen() || !hasViewMode()) {
            return;
        }
        MouseState mouse = mouseState();
        if (mouse == null) {
            modeDragging = false;
            modeMoved = false;
            return;
        }
        Layout layout = layout();
        float rowY = layout.y + u(HudEditorControls.HEADER_HEIGHT) + HudStyleMenuMetrics.sizeRowHeight() + u(HudEditorControls.ROW_GAP);
        HudEditorControls.SegmentBounds bounds = HudStyleMenuMetrics.modeBounds(layout.x, rowY);
        if (mouse.pressed) {
            if (!modeMoved && Math.abs(mouse.x - modePressX) >= u(1.5F)) {
                modeMoved = true;
            }
            if (modeMoved) {
                float local = clamp01((mouse.x - bounds.x()) / Math.max(0.001F, bounds.width()));
                modeAnimation.set(clamp01(local * 2.0F - 0.5F));
            }
            return;
        }
        Hud hud = Hud.getInstance();
        if (hud != null) {
            String selected = mouse.x < bounds.x() + bounds.width() * 0.5F ? "1" : "2";
            setCurrentViewMode(hud, selected);
            modeAnimation.run("2".equals(selected) ? 1.0 : 0.0, 0.22, Easings.CUBIC_IN_OUT, true);
        }
        modeDragging = false;
        modeMoved = false;
    }

    private void updateColorDrag() {
        if (!colorPicker.dragging() || !isOpen()) {
            return;
        }
        MouseState mouse = mouseState();
        if (mouse == null) {
            colorPicker.cancelDrag();
            return;
        }
        colorPicker.updateDrag(mouse.x, mouse.y, mouse.pressed);
    }

    private MouseState mouseState() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.mouse == null || client.getWindow() == null) {
            return null;
        }
        float coordinateScale = Math.max(0.0001F, Render2DCoordinateSpace.guiIndependentScale());
        float mx = (float) (client.mouse.getX() / client.getWindow().getScaleFactor()) / coordinateScale;
        float my = (float) (client.mouse.getY() / client.getWindow().getScaleFactor()) / coordinateScale;
        boolean pressed = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        return new MouseState(mx, my, pressed);
    }

    private void updateSizeFromMouse(float mouseX, Layout layout, float rowY) {
        HudEditorControls.SliderBounds bounds = HudStyleMenuMetrics.sizeBounds(layout.x, rowY);
        float progress = HudEditorControls.sliderProgressFromMouse(mouseX, bounds);
        float value = HudStyleOverrides.SIZE_MIN_PERCENT + progress * (HudStyleOverrides.SIZE_MAX_PERCENT - HudStyleOverrides.SIZE_MIN_PERCENT);
        HudStyleOverrides.getInstance().previewSizePercent(elementId, Math.round(value));
    }

    private void finishSizeDrag() {
        if (!sizeDragging) {
            return;
        }
        sizeDragging = false;
        if (elementId != null) {
            HudStyleOverrides.getInstance().commitSizePercent(elementId);
        }
    }

    private void updateNumberFromMouse(NumberSetting setting, float mouseX, Layout layout, float rowY) {
        HudEditorControls.SliderBounds bounds = HudStyleMenuMetrics.numberBounds(setting, layout.x, rowY);
        float progress = HudEditorControls.sliderProgressFromMouse(mouseX, bounds);
        setting.setValue(setting.getMin() + progress * (setting.getMax() - setting.getMin()));
    }

    private float settingY(Setting target, Layout layout) {
        float rowY = layout.y + u(HudEditorControls.HEADER_HEIGHT);
        boolean previous = false;
        if (hasSizeRow()) {
            rowY += HudStyleMenuMetrics.sizeRowHeight();
            previous = true;
        }
        if (hasViewMode()) {
            if (previous) {
                rowY += u(HudEditorControls.ROW_GAP);
            }
            rowY += HudStyleMenuMetrics.modeRowHeight();
            previous = true;
        }
        for (Setting setting : relevantSettings()) {
            float visibility = visibility(setting);
            if (visibility <= 0.001F) {
                continue;
            }
            if (previous) {
                rowY += u(HudEditorControls.ROW_GAP) * visibility;
            }
            if (setting == target) {
                return rowY;
            }
            rowY += HudStyleMenuMetrics.settingHeight(setting) * visibility;
            previous = true;
        }
        return rowY;
    }

    private float sizeProgress() {
        if (elementId == null) {
            return 0.5F;
        }
        float value = HudStyleOverrides.getInstance().getSizePercent(elementId);
        return clamp01((value - HudStyleOverrides.SIZE_MIN_PERCENT)
                / (HudStyleOverrides.SIZE_MAX_PERCENT - HudStyleOverrides.SIZE_MIN_PERCENT));
    }

    private void place(boolean resolveSide) {
        if (!isOpen() || anchor == null) {
            return;
        }

        if (!resolveSide && (sizeDragging || numberDragging || modeDragging || colorPicker.dragging())) {
            return;
        }
        HudStyleMenuPlacement.Result placement = HudStyleMenuPlacement.resolve(
                anchor, HudEditorControls.panelWidth(), layoutHeight(), requestedSide, side, resolveSide
        );
        x = placement.x();
        y = placement.y();
        side = placement.side();
    }

    private Layout layout() {
        return new Layout(x, y, layoutHeight());
    }

    private float layoutHeight() {
        float height = u(HudEditorControls.HEADER_HEIGHT);
        boolean previous = false;
        if (hasSizeRow()) {
            height += HudStyleMenuMetrics.sizeRowHeight();
            previous = true;
        }
        if (hasViewMode()) {
            if (previous) {
                height += u(HudEditorControls.ROW_GAP);
            }
            height += HudStyleMenuMetrics.modeRowHeight();
            previous = true;
        }
        for (Setting setting : relevantSettings()) {
            float visibility = visibility(setting);
            if (visibility <= 0.001F) {
                continue;
            }
            if (previous) {
                height += u(HudEditorControls.ROW_GAP) * visibility;
            }
            height += HudStyleMenuMetrics.settingHeight(setting) * visibility;
            previous = true;
        }
        return height + u(BOTTOM_PADDING);
    }

    private boolean hasSizeRow() {
        return true;
    }

    private boolean isPotions() {
        return hudElementId == HudElementId.POTIONS;
    }

    private boolean isCooldowns() {
        return hudElementId == HudElementId.COOLDOWNS;
    }

    private boolean hasViewMode() {
        return isPotions() || isCooldowns();
    }

    private String currentViewMode(Hud hud) {
        if (hud == null) {
            return "1";
        }
        return isCooldowns() ? hud.cooldownsViewMode() : hud.potionsViewMode();
    }

    private void setCurrentViewMode(Hud hud, String mode) {
        if (hud == null) {
            return;
        }
        if (isCooldowns()) {
            hud.setCooldownsViewMode(mode);
        } else {
            hud.setPotionsViewMode(mode);
        }
    }

    private void finishClose() {
        anchor = null;
        elementId = null;
        hudElementId = null;
        elementTitle = null;
        closing = false;
        popupAnimation.set(0.0);
        colorPicker.reset();
        sizeDragging = false;
        numberDragging = false;
        activeNumber = null;
        modeDragging = false;
    }

    private static float u(float value) {
        return HudEditorControls.u(value);
    }

    private static float clamp01(float value) {
        return HudEditorControls.clamp01(value);
    }

    private static boolean inside(float px, float py, float x, float y, float width, float height) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    private static int alpha(int color, float multiplier) {
        int a = Math.round(((color >>> 24) & 255) * clamp01(multiplier));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private record Layout(float x, float y, float height) {
    }

    private record Pointer(float x, float y) {
    }

    private record MouseState(float x, float y, boolean pressed) {
    }
}
