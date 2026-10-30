package universalmod.module.render;

import universalmod.hud.ElementManager;
import universalmod.hud.ElementScreen;
import universalmod.hud.HudElement;
import universalmod.hud.registry.HudElementDescriptor;
import universalmod.hud.registry.HudElementId;
import universalmod.hud.registry.HudElementRegistry;
import universalmod.hud.registry.HudElementsSetting;
import universalmod.hud.editor.HudPanel;
import universalmod.hud.editor.HudElementStyleMenu;
import universalmod.hud.editor.HudElementsPopup;
import universalmod.hud.editor.ScoreboardStyleMenu;
import universalmod.hud.element.BossbarPanel;
import universalmod.hud.element.ArmorPanel;
import universalmod.hud.element.NotificationsPanel;
import universalmod.hud.element.TargetHudPanel;
import universalmod.hud.element.Cooldowns;
import universalmod.hud.element.Potions;
import universalmod.hud.element.CurrentEventsPanel;
import universalmod.hud.element.Inventory;
import universalmod.hud.element.Keystrokes;
import universalmod.hud.element.MusicPlayerPanel;
import universalmod.hud.element.TntTimerPanel;
import universalmod.event.SubscribeEvent;
import universalmod.event.type.AttackEntityEvent;
import universalmod.event.type.DrawEvent;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.setting.BooleanSetting;
import universalmod.setting.ColorSetting;
import universalmod.setting.ModeSetting;
import universalmod.setting.NumberSetting;
import universalmod.render.hud.LoadingVisualGuard;
import universalmod.render.color.ColorUtil;
import universalmod.render.item.RenderItem;
import universalmod.theme.ThemeColors;
import universalmod.service.media.MusicTracker;
import universalmod.render.Render2D;
import universalmod.render.Render2DCoordinateSpace;
import universalmod.render.optimize.UiFrameContext;
import universalmod.render.blur.BlurPass;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.Color;
import java.util.EnumSet;

public class Hud extends Module {

    private static Hud instance;

    private final HudElementsSetting elements = register(new HudElementsSetting(
            "Elements", "HUD elements to render."));
    private final ModeSetting backgroundStyle = register(new ModeSetting(
            "Background Style", "HUD panel background style.", false, false, "Dark", "Dark", "Blur"));
    private final ModeSetting potionsView = register(new ModeSetting("Potions View", "", false, false, "1", "1", "2"));
    private final ModeSetting cooldownsView = register(new ModeSetting(
            "Cooldowns View", "Cooldowns display style.", false, false, "1", "1", "2"));
    private final ModeSetting armorMode = register(new ModeSetting(
            "Armor Mode", "Armor display style.", false, true, "Panel", "Panel", "Vanilla"));
    private final ModeSetting armorSide = register(new ModeSetting(
            "Armor Side", "Side of the hotbar for Vanilla armor mode.", false, true, "Right", "Left", "Right"));
    private final BooleanSetting keystrokesKeys = register(new BooleanSetting(
            "Keys", "Show W, A, S and D in the Keystrokes HUD.", true));
    private final BooleanSetting keystrokesMouseButtons = register(new BooleanSetting(
            "Mouse Buttons", "Show LMB and RMB in the Keystrokes HUD.", true));
    private final BooleanSetting keystrokesSpace = register(new BooleanSetting(
            "Space", "Show the Space bar in the Keystrokes HUD.", true));
    private final NumberSetting keystrokesOpacity = register(new NumberSetting(
            "Keystrokes Opacity", "Opacity of the Keystrokes HUD.", 82.0, 5.0, 100.0, 1.0));
    private final ColorSetting keystrokesNormalColor = register(new ColorSetting(
            "Keystrokes Color", "Normal key background color.", new Color(17, 18, 23, 220)));
    private final ColorSetting keystrokesPressedColor = register(new ColorSetting(
            "Keystrokes Pressed Color", "Color used while a key is pressed.", new Color(127, 242, 255, 255)));
    private final ColorSetting keystrokesLetterColor = register(new ColorSetting(
            "Keystrokes Letter Color", "Color of the letters in the Keystrokes HUD.", new Color(255, 255, 255, 255)));
    private final NumberSetting keystrokesPressedAlpha = register(new NumberSetting(
            "Keystrokes Pressed Alpha", "Opacity of the pressed key color.", 100.0, 0.0, 100.0, 1.0));
    private final BooleanSetting useCustomBossbarSettings = register(new BooleanSetting(
            "Use Custom Bossbar Settings",
            "Uses the selected bossbar and text colors instead of the server or vanilla style.",
            false
    ));
    private final ColorSetting bossbarColor = register(new ColorSetting(
            "Bossbar Color",
            "Color of the custom bossbar progress and its dark background.",
            new Color(255, 85, 255, 255)
    ));
    private final ColorSetting bossbarTextColor = register(new ColorSetting(
            "Bossbar Text Color",
            "Color of bossbar titles while custom bossbar settings are enabled.",
            new Color(255, 255, 255, 255)
    ));

    private final MusicPlayerPanel musicPlayerPanel;
    private final TargetHudPanel targetHudPanel = new TargetHudPanel();
    private final Potions potionsPanel = new Potions();
    private final Inventory inventoryPanel = new Inventory();
    private final NotificationsPanel notificationsPanel = new NotificationsPanel();
    private final ArmorPanel armorPanel = new ArmorPanel();
    private final Cooldowns cooldownsPanel = new Cooldowns();
    private final Keystrokes keystrokesPanel = new Keystrokes(this);
    private final TntTimerPanel tntTimerPanel = new TntTimerPanel();
    private final CurrentEventsPanel currentEventsPanel = CurrentEventsPanel.getInstance();
    private final BossbarPanel bossbarPanel = new BossbarPanel(this);
    private final HudElementRegistry elementRegistry;
    private final HudHoverHint hoverHint = new HudHoverHint();
    private final HudPointProjector hudPointProjector = new HudPointProjector();
    private final EnumSet<HudElementId> elementSelectedFrame = EnumSet.noneOf(HudElementId.class);
    private long elementSelectionFrame = Long.MIN_VALUE;

    public Hud(MusicTracker musicTracker) {
        super("hud", "HUD", ModuleCategory.RENDER);
        this.musicPlayerPanel = new MusicPlayerPanel(java.util.Objects.requireNonNull(musicTracker, "musicTracker"));
        this.elementRegistry = HudElementRegistry.builder()
                .add(HudElementId.MUSIC_PLAYER, musicPlayerPanel)
                .add(HudElementId.TARGET_HUD, targetHudPanel)
                .add(HudElementId.POTIONS, potionsPanel)
                .add(HudElementId.INVENTORY, inventoryPanel)
                .add(HudElementId.NOTIFICATIONS, notificationsPanel)
                .add(HudElementId.ARMOR, armorPanel)
                .add(HudElementId.COOLDOWNS, cooldownsPanel)
                .add(HudElementId.KEYSTROKES, keystrokesPanel)
                .add(HudElementId.TNT_TIMER, tntTimerPanel)
                .add(HudElementId.EVENT, currentEventsPanel)
                .add(HudElementId.BOSSBAR, bossbarPanel)
                .build();
        instance = this;
        potionsView.visibleWhen(() -> false);
        cooldownsView.visibleWhen(() -> false);
        armorMode.visibleWhen(this::isArmorSelected);
        armorSide.visibleWhen(() -> isArmorSelected() && "Vanilla".equalsIgnoreCase(armorMode.getValue()));
        keystrokesKeys.visibleWhen(this::isKeystrokesSelected);
        keystrokesMouseButtons.visibleWhen(this::isKeystrokesSelected);
        keystrokesSpace.visibleWhen(this::isKeystrokesSelected);
        keystrokesOpacity.visibleWhen(this::isKeystrokesSelected);
        keystrokesNormalColor.visibleWhen(this::isKeystrokesSelected);
        keystrokesPressedColor.visibleWhen(this::isKeystrokesSelected);
        keystrokesLetterColor.visibleWhen(this::isKeystrokesSelected);
        keystrokesPressedAlpha.visibleWhen(this::isKeystrokesSelected);
        useCustomBossbarSettings.visibleWhen(this::isBossbarSelected);
        bossbarColor.visibleWhen(() -> isBossbarSelected() && useCustomBossbarSettings.getValue());
        bossbarTextColor.visibleWhen(() -> isBossbarSelected() && useCustomBossbarSettings.getValue());
        setEnabled(true);
    }

    public static Hud getInstance() {
        return instance;
    }

    @Override
    protected void onDisable() {
        ElementManager.getInstance().cancelActiveElement();
        HudElementStyleMenu.getInstance().close();
        HudElementsPopup.getInstance().close();
    }


    public boolean isElementSelected(String persistentOrLegacyName) {
        return HudElementId.fromPersistentOrLegacy(persistentOrLegacyName)
                .map(elements::isSelected)
                .orElse(false);
    }

    public boolean isElementSelected(HudElementId id) {
        return elements.isSelected(id);
    }

    public void setElementSelected(String persistentOrLegacyName, boolean selected) {
        HudElementId.fromPersistentOrLegacy(persistentOrLegacyName)
                .ifPresent(id -> setElementSelected(id, selected));
    }

    public void setElementSelected(HudElementId id, boolean selected) {
        if (id == null) {
            return;
        }
        elements.setSelected(id, selected);
        elementSelectionFrame = Long.MIN_VALUE;
    }

    public String[] hudElementNames() {
        return HudElementId.optionNames();
    }

    public HudElementRegistry hudElementRegistry() {
        return elementRegistry;
    }

    public String backgroundStyle() {
        return backgroundStyle.getValue();
    }

    public boolean blurStyle() {
        return "Blur".equalsIgnoreCase(backgroundStyle.getValue());
    }

    public static boolean isBlurStyle() {
        Hud hud = instance;
        return hud != null && hud.blurStyle();
    }

    public String potionsViewMode() {
        return potionsView.getValue();
    }

    public void setPotionsViewMode(String mode) {
        potionsView.setValue(mode);
    }

    public String cooldownsViewMode() {
        return cooldownsView.getValue();
    }

    public void setCooldownsViewMode(String mode) {
        cooldownsView.setValue(mode);
    }

    public boolean cooldownsSecondMode() {
        return "2".equals(cooldownsView.getValue());
    }

    public boolean isCooldownsSelected() {
        return elements.isSelected(HudElementId.COOLDOWNS);
    }

    public boolean isBossbarSelected() {
        return elements.isSelected(HudElementId.BOSSBAR);
    }

    public boolean isMusicPlayerSelected() {
        return elements.isSelected(HudElementId.MUSIC_PLAYER);
    }

    public boolean isKeystrokesSelected() {
        return elements.isSelected(HudElementId.KEYSTROKES);
    }

    public boolean isArmorSelected() {
        return elements.isSelected(HudElementId.ARMOR);
    }

    public String armorMode() {
        return armorMode.getValue();
    }

    public boolean armorVanillaMode() {
        return "Vanilla".equalsIgnoreCase(armorMode.getValue());
    }

    public String armorSide() {
        return armorSide.getValue();
    }

    public boolean armorOnLeft() {
        return "Left".equalsIgnoreCase(armorSide.getValue());
    }

    public boolean keystrokesShowKeys() {
        return keystrokesKeys.getValue();
    }

    public boolean keystrokesShowMouseButtons() {
        return keystrokesMouseButtons.getValue();
    }

    public boolean keystrokesShowSpace() {
        return keystrokesSpace.getValue();
    }

    public float keystrokesOpacity() {
        return Math.max(0.05F, Math.min(1.0F, keystrokesOpacity.getFloat() / 100.0F));
    }

    public Color keystrokesNormalColor() {
        return keystrokesNormalColor.getValue();
    }

    public Color keystrokesPressedColor() {
        return keystrokesPressedColor.getValue();
    }

    public Color keystrokesLetterColor() {
        return keystrokesLetterColor.getValue();
    }

    public ColorSetting keystrokesNormalColorSetting() {
        return keystrokesNormalColor;
    }

    public ColorSetting keystrokesPressedColorSetting() {
        return keystrokesPressedColor;
    }

    public ColorSetting keystrokesLetterColorSetting() {
        return keystrokesLetterColor;
    }

    public float keystrokesPressedAlpha() {
        return Math.max(0.0F, Math.min(1.0F, keystrokesPressedAlpha.getFloat() / 100.0F));
    }

    public boolean useCustomBossbarSettings() {
        return useCustomBossbarSettings.getValue();
    }

    public Color bossbarColor() {
        return bossbarColor.getValue();
    }

    public Color bossbarTextColor() {
        return bossbarTextColor.getValue();
    }

    public static boolean shouldReplaceVanillaBossbar() {
        Hud hud = instance;
        return hud != null && hud.isEnabled() && hud.bossbarPanel.shouldReplaceVanilla();
    }

    @SubscribeEvent
    private void onAttack(AttackEntityEvent event) {
        if (event != null && event.target() instanceof LivingEntity living && living != mc.player) {
            targetHudPanel.setTarget(living);
        }
    }

    public static boolean isBlurEnabled() {
        Hud hud = instance;
        return hud != null && hud.isEnabled() && hud.blurStyle();
    }

    public static boolean isGlowEnabled() {
        return true;
    }

    public static void renderHudBackground(float x, float y, float width, float height, float radius, float blurRadius, float smoothness, int color) {
        float alpha = ((color >>> 24) & 0xFF) / 255.0f;
        if (alpha <= 0.0001f || width <= 0.0f || height <= 0.0f) {
            return;
        }
        if (isBlurStyle()) {
            
            Render2D.blur(x, y, width, height, radius, 4.5F, Math.max(0.50F, smoothness * 0.55F),
                    ColorUtil.rgba(255, 255, 255, Math.round(255.0F * alpha)));
            Render2D.rect(x, y, width, height, radius,
                    ThemeColors.hudBlurColor(ColorUtil.rgba(9, 11, 15, Math.round(47.0F * alpha))));
            return;
        }

        
        if (isBlurEnabled()) {
            Render2D.blur(x, y, width, height, radius, Math.max(4.5f, blurRadius * 1.15f),
                    Math.max(0.70f, smoothness), ThemeColors.hudBlurColor(ColorUtil.rgba(5, 7, 11, Math.round(226.0f * alpha))));
        } else {
            Render2D.rect(x, y, width, height, radius,
                    ThemeColors.hudBlurColor(ColorUtil.rgba(5, 7, 11, Math.round(246.0f * alpha))));
        }
        Render2D.hudSurface(x, y, width, height, radius, 0.97f * alpha, 0.86f, 0.93f);
    }

    public static void renderHudHeader(float x, float y, float width, float height, float radius, float blurRadius, float smoothness, int color) {
        renderHudBackground(x, y, width, height, radius, blurRadius, smoothness, color);
    }

    public static void renderHudBackground(BlurPass blur) {
        if (blur == null) {
            return;
        }
        float alpha = ((blur.color() >>> 24) & 0xFF) / 255.0f;
        if (alpha <= 0.0001f) {
            return;
        }
        if (isBlurStyle()) {
            BlurPass styleBlur = new BlurPass(
                    blur.x(), blur.y(), blur.width(), blur.height(),
                    blur.radiusTopLeft(), blur.radiusTopRight(), blur.radiusBottomRight(), blur.radiusBottomLeft(),
                    Math.max(0.50F, blur.smoothness() * 0.55F), 4.5F,
                    ColorUtil.rgba(255, 255, 255, Math.round(255.0F * alpha))
            );
            Render2D.blur(styleBlur);
            Render2D.rect(blur.x(), blur.y(), blur.width(), blur.height(),
                    blur.radiusTopLeft(), blur.radiusTopRight(), blur.radiusBottomRight(), blur.radiusBottomLeft(),
                    ThemeColors.hudBlurColor(ColorUtil.rgba(9, 11, 15, Math.round(47.0F * alpha))));
            return;
        }

        BlurPass darkBlur = new BlurPass(
                blur.x(), blur.y(), blur.width(), blur.height(),
                blur.radiusTopLeft(), blur.radiusTopRight(), blur.radiusBottomRight(), blur.radiusBottomLeft(),
                Math.max(0.70f, blur.smoothness()), Math.max(4.5f, blur.blurRadius() * 1.15f),
                ThemeColors.hudBlurColor(ColorUtil.rgba(5, 7, 11, Math.round(226.0f * alpha)))
        );
        if (isBlurEnabled()) {
            Render2D.blur(darkBlur);
        } else {
            Render2D.rect(blur.x(), blur.y(), blur.width(), blur.height(), blur.radiusTopLeft(), blur.radiusTopRight(),
                    blur.radiusBottomRight(), blur.radiusBottomLeft(),
                    ThemeColors.hudBlurColor(ColorUtil.rgba(5, 7, 11, Math.round(246.0f * alpha))));
        }
        Render2D.hudSurface(blur.x(), blur.y(), blur.width(), blur.height(), blur.radiusTopLeft(), 0.97f * alpha, 0.86f, 0.93f);
    }

    public static void renderHudGlow(String texture, float x, float y, float width, float height, float radius, int color) {
        if (isGlowEnabled()) {
            Render2D.image(texture, x, y, width, height, radius, color);
        }
    }

    public void renderHudLayer(DrawEvent event) {
        if (mc.player == null || mc.level == null || mc.getWindow() == null || LoadingVisualGuard.shouldSuppressHud(mc)) {
            return;
        }

        ElementScreen screen = ElementScreen.current();
        ElementManager elementManager = ElementManager.getInstance();
        elementManager.frame(screen);
        if (event.getLayer() == DrawEvent.Layer.CHAT_OVERLAY) {
            elementManager.updateActiveElementFromMouse();
            HudElementStyleMenu.getInstance().updateBeforeHudRender();
        }
        refreshElementSelection();
        RenderItem.beginFrame(event.getGraphics());
        try {
            for (HudElementDescriptor descriptor : elementRegistry.ordered()) {
                HudElement element = descriptor.element();
                boolean selected = elementSelectedFrame.contains(descriptor.id());
                if (element instanceof BossbarPanel panel) {
                    panel.setHudSelected(selected);
                    boolean render = selected || panel.shouldRenderDynamicFallback();
                    panel.setHudVisible(render);
                    if (render) {

                        renderHudPanel(panel, selected);
                    }
                    continue;
                }

                setElementVisible(element, selected);
                if (selected) {
                    if (element instanceof HudPanel panel) {
                        renderHudPanel(panel, true);
                    } else {
                        element.render();
                    }
                }
            }
        } finally {
            RenderItem.flush();
        }

        boolean editLayer = event.getLayer() == DrawEvent.Layer.CHAT_OVERLAY && elementManager.canEditCurrentScreen();
        if (editLayer) {
            elementManager.renderEditorOverlay(event.getGraphics(), screen);
            HudElementStyleMenu.getInstance().render(event.getGraphics());
            HudElementsPopup.getInstance().render(event.getGraphics());
        }
    }

    public boolean handleMouseClicked(MouseButtonEvent event, boolean doubled) {
        if (!isEnabled() || event == null) {
            return false;
        }
        if (HudElementsPopup.getInstance().mouseClicked(event)) {
            return true;
        }
        if (HudElementStyleMenu.getInstance().mouseClicked(event)) {
            return true;
        }
        if (event.button() == 1) {
            var descriptors = elementRegistry.ordered();
            for (int i = descriptors.size() - 1; i >= 0; i--) {
                HudElementDescriptor descriptor = descriptors.get(i);
                HudElement element = descriptor.element();
                if (!elements.isSelected(descriptor.id()) || !(element instanceof HudPanel panel)) {
                    continue;
                }
                if (panel.hit((float) event.x(), (float) event.y())) {
                    ScoreboardStyleMenu.getInstance().close();
                    HudElementsPopup.getInstance().close();
                    HudElementStyleMenu.getInstance().open(panel, (float) event.x(), (float) event.y());
                    return true;
                }
            }
            HudElementStyleMenu.getInstance().close();
            ScoreboardStyleMenu.getInstance().close();
            HudElementsPopup.getInstance().open((float) event.x(), (float) event.y());
            return true;
        }
        if (event.button() != 0) {
            return false;
        }
        var descriptors = elementRegistry.ordered();
        for (int i = descriptors.size() - 1; i >= 0; i--) {
            HudElementDescriptor descriptor = descriptors.get(i);
            HudElement element = descriptor.element();
            if (!elements.isSelected(descriptor.id())) {
                continue;
            }
            if (element instanceof HudPanel panel) {
                if (element.mouseClicked(event, doubled)) {
                    return true;
                }
            } else if (element.mouseClicked(event, doubled)) {
                return true;
            }
        }
        return false;
    }

    public boolean handleMouseReleased(MouseButtonEvent event) {
        if (!isEnabled() || event == null) {
            return false;
        }
        var descriptors = elementRegistry.ordered();
        for (int i = descriptors.size() - 1; i >= 0; i--) {
            HudElementDescriptor descriptor = descriptors.get(i);
            HudElement element = descriptor.element();
            if (!elements.isSelected(descriptor.id())) {
                continue;
            }
            if (element.mouseReleased(event)) {
                return true;
            }
        }
        return false;
    }

    public boolean handleMouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!isEnabled()) {
            return false;
        }
        if (HudElementsPopup.getInstance().mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        var descriptors = elementRegistry.ordered();
        for (int i = descriptors.size() - 1; i >= 0; i--) {
            HudElementDescriptor descriptor = descriptors.get(i);
            HudElement element = descriptor.element();
            if (!elements.isSelected(descriptor.id())) {
                continue;
            }
            if (element instanceof HudPanel panel) {
                if (element.mouseScrolled(mouseX, mouseY, scrollY)) {
                    return true;
                }
            } else if (element.mouseScrolled(mouseX, mouseY, scrollY)) {
                return true;
            }
        }
        return false;
    }

    public void renderHudHoverHint(GuiGraphics graphics, int mouseX, int mouseY) {
        hoverHint.render(graphics, mouseX, mouseY, isEnabled(), elementRegistry, elements);
    }

    private void renderHudPanel(HudPanel panel, boolean useConfiguredScale) {
        if (panel == null) {
            return;
        }

        panel.prepareFrame();
        float scale = useConfiguredScale ? panel.configuredHudScale() : 1.0F;
        panel.prepareHudScale(scale);

        GuiGraphics graphics = Render2D.currentGraphics();
        float appliedScale = panel.hudScale();
        float dragScale = panel.dragScale();
        float tiltDegrees = panel.dragTiltDegrees();
        float anchorX = panel.x();
        float anchorY = panel.y();
        float centerX = anchorX + panel.width() * 0.5F;
        float centerY = anchorY + panel.height() * 0.5F;
        boolean transform = graphics != null
                && (Math.abs(appliedScale - 1.0F) > 0.0001F
                || Math.abs(dragScale - 1.0F) > 0.0001F
                || Math.abs(tiltDegrees) > 0.0001F);

        try {
            if (!transform) {
                panel.render();
                return;
            }

            float guiAnchorX = Render2DCoordinateSpace.toGui(anchorX);
            float guiAnchorY = Render2DCoordinateSpace.toGui(anchorY);
            float guiCenterX = Render2DCoordinateSpace.toGui(centerX);
            float guiCenterY = Render2DCoordinateSpace.toGui(centerY);
            graphics.pose().pushMatrix();
            try {

                graphics.pose().translate(guiCenterX, guiCenterY);
                graphics.pose().rotate((float) Math.toRadians(tiltDegrees));
                graphics.pose().scale(dragScale);
                graphics.pose().translate(-guiCenterX, -guiCenterY);

                graphics.pose().translate(guiAnchorX, guiAnchorY);
                graphics.pose().scale(appliedScale);
                graphics.pose().translate(-guiAnchorX, -guiAnchorY);

                if (panel.usesProjectedScissor()) {
                    hudPointProjector.configure(
                            anchorX, anchorY, centerX, centerY, appliedScale, dragScale, tiltDegrees
                    );
                    Render2D.pushProjection(hudPointProjector);
                    try {
                        panel.render();
                    } finally {
                        Render2D.popProjection();
                    }
                } else {
                    panel.render();
                }
            } finally {
                graphics.pose().popMatrix();
            }
        } finally {
            panel.finishHudScale();
        }
    }

    private static final class HudPointProjector implements Render2D.PointProjector {
        private float anchorX;
        private float anchorY;
        private float centerX;
        private float centerY;
        private float appliedScale;
        private float dragScale;
        private float sin;
        private float cos;

        private void configure(
                float anchorX,
                float anchorY,
                float centerX,
                float centerY,
                float appliedScale,
                float dragScale,
                float tiltDegrees
        ) {
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.centerX = centerX;
            this.centerY = centerY;
            this.appliedScale = appliedScale;
            this.dragScale = dragScale;
            float radians = (float) Math.toRadians(tiltDegrees);
            this.sin = (float) Math.sin(radians);
            this.cos = (float) Math.cos(radians);
        }

        @Override
        public Render2D.ProjectedPoint project(float px, float py) {
            float scaledX = anchorX + (px - anchorX) * appliedScale;
            float scaledY = anchorY + (py - anchorY) * appliedScale;
            float dx = (scaledX - centerX) * dragScale;
            float dy = (scaledY - centerY) * dragScale;
            return new Render2D.ProjectedPoint(
                    centerX + dx * cos - dy * sin,
                    centerY + dx * sin + dy * cos
            );
        }
    }

    private void refreshElementSelection() {
        long frame = UiFrameContext.active() ? UiFrameContext.frameId() : Long.MIN_VALUE;
        if (frame != Long.MIN_VALUE && elementSelectionFrame == frame) {
            return;
        }
        elementSelectedFrame.clear();
        for (HudElementDescriptor descriptor : elementRegistry.ordered()) {
            if (elements.isSelected(descriptor.id())) {
                elementSelectedFrame.add(descriptor.id());
            }
        }
        elementSelectionFrame = frame;
    }

    private void setElementVisible(HudElement element, boolean visible) {
        if (element instanceof HudPanel panel) {
            panel.setHudVisible(visible);
        }
    }


}
