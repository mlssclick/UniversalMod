package universalmod.module.render;

import universalmod.lifecycle.Manager;
import universalmod.event.SubscribeEvent;

import java.awt.Color;
import java.util.List;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import universalmod.event.type.DrawEvent;
import universalmod.render.animation.Easings;
import universalmod.render.animation.SmoothAnimation;
import universalmod.hud.registry.HudElementId;
import universalmod.hud.registry.HudElementDescriptor;
import universalmod.hud.registry.HudElementRegistry;
import universalmod.hud.registry.HudElementsSetting;
import universalmod.hud.ElementManager;
import universalmod.hud.ElementScreen;
import universalmod.hud.HudElement;
import universalmod.hud.HudMouseEvent;
import universalmod.hud.element.BossbarPanel;
import universalmod.hud.element.TargetHudPanel;
import universalmod.hud.element.NotificationsPanel;
import universalmod.hud.element.ArmorPanel;
import universalmod.hud.notification.HudNotificationManager;
import universalmod.event.type.AttackEntityEvent;
import universalmod.hud.element.Cooldowns;
import universalmod.hud.element.CurrentEventsPanel;
import universalmod.hud.editor.HudElementStyleMenu;
import universalmod.hud.editor.HudElementsPopup;
import universalmod.hud.editor.ScoreboardStyleMenu;
import universalmod.hud.editor.HudPanel;
import universalmod.hud.element.Inventory;
import universalmod.hud.element.Keystrokes;
import universalmod.hud.element.LyricsPanel;
import universalmod.hud.element.MusicPlayerPanel;
import universalmod.hud.element.Potions;
import universalmod.hud.element.TntTimerPanel;
import universalmod.render.item.RenderItem;
import universalmod.render.blur.BlurPass;
import universalmod.render.color.ColorUtil;
import universalmod.render.font.FontType;
import universalmod.render.Render2D;
import universalmod.render.Render2DCoordinateSpace;
import universalmod.render.optimize.UiFrameContext;
import universalmod.theme.ThemeColors;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;
import universalmod.setting.BooleanSetting;
import universalmod.setting.ColorSetting;
import universalmod.setting.ModeListSetting;
import universalmod.setting.ModeSetting;
import universalmod.setting.SliderSetting;

@ModuleInformation(
        moduleName = "HUD",
        moduleCategory = ModuleCategory.RENDER
)
public final class Hud extends Module {
    private static final String BOSSBAR_ELEMENT = "Bossbar";
    private static final String MUSIC_PLAYER_ELEMENT = "Music Player";

    private static Hud instance;

    private final HudElementsSetting elements = new HudElementsSetting("Elements");

    private final ModeSetting backgroundStyle = new ModeSetting("Background Style", "Dark", "Dark", "Blur");
    private final ModeSetting potionsView = new ModeSetting("Potions View", "1", "1", "2").setVisible(() -> false);
    private final ModeSetting cooldownsView = new ModeSetting("Cooldowns View", "1", "1", "2").setVisible(() -> false);
    private final ModeSetting armorMode = new ModeSetting("Armor Mode", "Panel", "Panel", "Vanilla")
            .setVisible(this::isArmorSelected);
    private final ModeSetting armorSide = new ModeSetting("Armor Side", "Right", "Left", "Right")
            .setVisible(() -> isArmorSelected() && armorMode.is("Vanilla"));

    private final BooleanSetting keystrokesKeys = new BooleanSetting("Keys", true)
            .setVisible(this::isKeystrokesSelected);
    private final BooleanSetting keystrokesMouseButtons = new BooleanSetting("Mouse Buttons", true)
            .setVisible(this::isKeystrokesSelected);
    private final BooleanSetting keystrokesSpace = new BooleanSetting("Space", true)
            .setVisible(this::isKeystrokesSelected);
    private final SliderSetting keystrokesOpacity = new SliderSetting("Keystrokes Opacity", 82.0, 5.0, 100.0, 1.0)
            .setVisible(this::isKeystrokesSelected);
    private final ColorSetting keystrokesNormalColor = new ColorSetting("Keystrokes Color", new Color(17, 18, 23, 220).getRGB())
            .setVisible(this::isKeystrokesSelected);
    private final ColorSetting keystrokesPressedColor = new ColorSetting("Keystrokes Pressed Color", new Color(127, 242, 255, 255).getRGB())
            .setVisible(this::isKeystrokesSelected);
    private final ColorSetting keystrokesLetterColor = new ColorSetting("Keystrokes Letter Color", Color.WHITE.getRGB())
            .setVisible(this::isKeystrokesSelected);
    private final SliderSetting keystrokesPressedAlpha = new SliderSetting("Keystrokes Pressed Alpha", 100.0, 0.0, 100.0, 1.0)
            .setVisible(this::isKeystrokesSelected);

    private final BooleanSetting useCustomBossbarSettings = new BooleanSetting("Use Custom Bossbar Settings", false)
            .setVisible(this::isBossbarSelected);
    private final ColorSetting bossbarColor = new ColorSetting("Bossbar Color", new Color(255, 85, 255, 255).getRGB())
            .setVisible(() -> isBossbarSelected() && useCustomBossbarSettings.getValue());
    private final ColorSetting bossbarTextColor = new ColorSetting("Bossbar Text Color", Color.WHITE.getRGB())
            .setVisible(() -> isBossbarSelected() && useCustomBossbarSettings.getValue());

    private final MusicPlayerPanel musicPlayerPanel = new MusicPlayerPanel();
    private final TargetHudPanel targetHudPanel = new TargetHudPanel();
    private final NotificationsPanel notificationsPanel = new NotificationsPanel();
    private final ArmorPanel armorPanel = new ArmorPanel();
    private final BossbarPanel bossbarPanel = new BossbarPanel(this);
    private final HudHoverHint hoverHint = new HudHoverHint();

    private final HudElementRegistry elementRegistry = HudElementRegistry.builder()
            .add(HudElementId.MUSIC_PLAYER, musicPlayerPanel)
            .add(HudElementId.TARGET_HUD, targetHudPanel)
            .add(HudElementId.POTIONS, new Potions())
            .add(HudElementId.INVENTORY, new Inventory())
            .add(HudElementId.NOTIFICATIONS, notificationsPanel)
            .add(HudElementId.ARMOR, armorPanel)
            .add(HudElementId.COOLDOWNS, new Cooldowns())
            .add(HudElementId.KEYSTROKES, new Keystrokes(this))
            .add(HudElementId.TNT_TIMER, new TntTimerPanel())
            .add(HudElementId.EVENT, CurrentEventsPanel.getInstance())
            .add(HudElementId.BOSSBAR, bossbarPanel)
            .build();
    private final List<HudElement> elementsList = elementRegistry.ordered().stream()
            .map(HudElementDescriptor::element).toList();
    private final String[] elementNames = elementRegistry.ordered().stream()
            .map(HudElementDescriptor::optionName).toArray(String[]::new);
    private final boolean[] elementSelectedFrame = new boolean[elementsList.size()];
    private long elementSelectionFrame = Long.MIN_VALUE;

    private long lastClickAt;
    private int lastClickButton = -1;
    private double lastClickX;
    private double lastClickY;

    public Hud() {
        instance = this;
        ElementManager.getInstance().load();

        setEnabled(true);
        HudNotificationManager.getInstance().setReady(true);
    }

    public static Hud getInstance() {
        return instance;
    }

    @Override
    public void onDisable() {

        closeEditorState();
    }

    public static boolean isBlurStyle() {
        Hud active = instance;
        return active != null && active.backgroundStyle.is("Blur");
    }

    public HudElementRegistry hudElementRegistry() {
        return elementRegistry;
    }

    public boolean isElementSelected(HudElementId id) {
        return elements.isSelected(id);
    }

    public void setElementSelected(HudElementId id, boolean selected) {
        elements.setSelected(id, selected);
        elementSelectionFrame = Long.MIN_VALUE;
    }

    public boolean isElementSelected(String persistentOrLegacyName) {
        return HudElementId.fromPersistentOrLegacy(persistentOrLegacyName)
                .map(elements::isSelected).orElse(false);
    }

    public void setElementSelected(String persistentOrLegacyName, boolean selected) {
        HudElementId.fromPersistentOrLegacy(persistentOrLegacyName)
                .ifPresent(id -> setElementSelected(id, selected));
    }

    public String[] hudElementNames() {
        return HudElementId.optionNames();
    }

    public boolean isBossbarSelected() {
        return elements.isEnabled(BOSSBAR_ELEMENT);
    }

    public boolean isMusicPlayerSelected() {
        return elements.isEnabled(MUSIC_PLAYER_ELEMENT);
    }

    public boolean isArmorSelected() {
        return elements.isEnabled("Armor");
    }

    public String armorMode() { return armorMode.getValue(); }
    public boolean armorVanillaMode() { return armorMode.is("Vanilla"); }
    public String armorSide() { return armorSide.getValue(); }
    public boolean armorOnLeft() { return armorSide.is("Left"); }

    public boolean isKeystrokesSelected() {
        return elements.isEnabled("Keystrokes");
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
        return Math.max(0.05F, Math.min(1.0F, keystrokesOpacity.getFloatValue() / 100.0F));
    }

    public Color keystrokesNormalColor() {
        return new Color(keystrokesNormalColor.getValue(), true);
    }

    public Color keystrokesPressedColor() {
        return new Color(keystrokesPressedColor.getValue(), true);
    }

    public Color keystrokesLetterColor() {
        return new Color(keystrokesLetterColor.getValue(), true);
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
        return Math.max(0.0F, Math.min(1.0F, keystrokesPressedAlpha.getFloatValue() / 100.0F));
    }

    public boolean useCustomBossbarSettings() {
        return useCustomBossbarSettings.getValue();
    }

    public Color bossbarColor() {
        return new Color(bossbarColor.getValue(), true);
    }

    public Color bossbarTextColor() {
        return new Color(bossbarTextColor.getValue(), true);
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
        return hud != null && hud.isEnabled() && hud.backgroundStyle.is("Blur");
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

    public void drawHeaderBackground(float x, float y, float w, float h, float radius, int alpha) {
        renderHudHeader(x, y, w, h, radius, 4.0F, 0.55F, ColorUtil.rgba(0, 0, 0, alpha));
    }

    public void drawBackground(float x, float y, float w, float h, float radius, int alpha) {
        renderHudBackground(x, y, w, h, radius, 4.0F, 0.55F, ColorUtil.rgba(0, 0, 0, alpha));
    }

    public void drawBackground(float x, float y, float w, float h,
                               float topLeft, float topRight, float bottomLeft, float bottomRight, int alpha) {
        BlurPass blur = new BlurPass(x, y, w, h, topLeft, topRight, bottomRight, bottomLeft,
                0.55F, 4.0F, ColorUtil.rgba(0, 0, 0, alpha));
        renderHudBackground(blur);
    }

    public float getBackgroundIntensity() {
        return 1.0F;
    }

    @SubscribeEvent
    public void onDrawEvent(DrawEvent event) {

        if (!isEnabled() || event == null || event.getLayer() != DrawEvent.Layer.GAME
                || mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen
                || mc.player == null || mc.world == null || mc.options.hudHidden) {
            return;
        }
        DrawContext graphics = event.getGraphics();
        if (graphics == null) {
            return;
        }

        renderHudElements(graphics);
    }

    private void renderHudElements(DrawContext graphics) {
        ElementScreen screen = ElementScreen.current();
        ElementManager manager = ElementManager.getInstance();
        manager.frame(screen);
        refreshElementSelection();

        Render2D.beginFrame(graphics);
        RenderItem.beginFrame(graphics);
        try {
            for (int i = 0; i < elementsList.size(); i++) {
                HudElement element = elementsList.get(i);
                boolean selected = elementSelectedFrame[i];
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
                if (!selected) {
                    continue;
                }
                if (element instanceof HudPanel panel) {
                    renderHudPanel(panel, true);
                } else {
                    element.render();
                }
            }
        } finally {
            RenderItem.flush();
            Render2D.flush();
        }
    }

    public void renderEditorLayer(DrawContext graphics, int mouseX, int mouseY) {
        if (graphics == null || mc.player == null || mc.world == null) {
            return;
        }
        boolean hudEnabled = isEnabled();
        UiFrameContext.begin(graphics);
        try {
            ElementManager manager = ElementManager.getInstance();
            ElementScreen screen = ElementScreen.current();
            if (hudEnabled) {
                manager.frame(screen);
                manager.updateActiveElementFromMouse();
                HudElementStyleMenu.getInstance().updateBeforeHudRender();
            }

            Scoreboard scoreboard = Scoreboard.getInstance();
            if (scoreboard != null && scoreboard.isEnabled()) {
                Render2D.beginFrame(graphics);
                try {
                    scoreboard.render(graphics, true);
                } finally {
                    Render2D.flush();
                }
            }
            if (hudEnabled) {
                renderHudElements(graphics);
            }

            Render2D.beginFrame(graphics);
            try {
                if (hudEnabled) {
                    manager.renderEditorOverlay(graphics, screen);
                    HudElementStyleMenu.getInstance().render(graphics);
                    HudElementsPopup.getInstance().render(graphics);
                }
                ScoreboardStyleMenu.getInstance().render(graphics);
                hoverHint.render(graphics, mouseX, mouseY, hudEnabled, elementRegistry, elements);
            } finally {
                Render2D.flush();
            }
        } finally {
            UiFrameContext.end();
        }
    }

    public boolean handleMouseClicked(double guiMouseX, double guiMouseY, int button) {
        float mouseX = Render2D.guiToFixed((float) guiMouseX);
        float mouseY = Render2D.guiToFixed((float) guiMouseY);
        HudMouseEvent event = new HudMouseEvent(mouseX, mouseY, button);
        boolean doubled = isDoubleClick(event);

        if (!isEnabled()) {
            if (ScoreboardStyleMenu.getInstance().mouseClicked(event)) {
                return true;
            }
            Scoreboard scoreboard = Scoreboard.getInstance();
            if (button == 1 && scoreboard != null && scoreboard.editorHit(mouseX, mouseY)) {
                ScoreboardStyleMenu.getInstance().open(scoreboard, mouseX);
                return true;
            }
            return false;
        }

        if (HudElementsPopup.getInstance().mouseClicked(event)) {
            return true;
        }
        if (ScoreboardStyleMenu.getInstance().mouseClicked(event)) {
            return true;
        }
        if (HudElementStyleMenu.getInstance().mouseClicked(event)) {
            return true;
        }
        if (button == 1) {
            Scoreboard scoreboard = Scoreboard.getInstance();
            if (scoreboard != null && scoreboard.editorHit(mouseX, mouseY)) {
                HudElementStyleMenu.getInstance().close();
                HudElementsPopup.getInstance().close();
                ScoreboardStyleMenu.getInstance().open(scoreboard, mouseX);
                return true;
            }
            for (int i = elementsList.size() - 1; i >= 0; i--) {
                HudElement element = elementsList.get(i);
                if (!elements.isEnabled(elementNames[i]) || !(element instanceof HudPanel panel)) {
                    continue;
                }
                if (panel.hit(mouseX, mouseY)) {
                    ScoreboardStyleMenu.getInstance().close();
                    HudElementsPopup.getInstance().close();
                    HudElementStyleMenu.getInstance().open(panel, mouseX, mouseY);
                    return true;
                }
            }
            HudElementStyleMenu.getInstance().close();
            ScoreboardStyleMenu.getInstance().close();
            HudElementsPopup.getInstance().open(mouseX, mouseY);
            return true;
        }
        if (button != 0) {
            return false;
        }
        if (ElementManager.getInstance().handleResizeMouseClicked(event)) {
            return true;
        }
        for (int i = elementsList.size() - 1; i >= 0; i--) {
            HudElement element = elementsList.get(i);
            if (!elements.isEnabled(elementNames[i])) {
                continue;
            }
            if (element.mouseClicked(event, doubled)) {
                return true;
            }
        }
        return ElementManager.getInstance().handleMouseClicked(event);
    }

    public boolean handleMouseDragged(double guiMouseX, double guiMouseY, int button) {
        if (!isEnabled() || button != 0) {
            return false;
        }
        return ElementManager.getInstance().handleMouseDragged(
                Render2D.guiToFixed(guiMouseX), Render2D.guiToFixed(guiMouseY));
    }

    public boolean handleMouseReleased(double guiMouseX, double guiMouseY, int button) {
        if (!isEnabled()) {
            return false;
        }
        HudMouseEvent event = new HudMouseEvent(Render2D.guiToFixed(guiMouseX),
                Render2D.guiToFixed(guiMouseY), button);
        if (ElementManager.getInstance().resizing()) {
            return ElementManager.getInstance().handleMouseReleased(event);
        }
        for (int i = elementsList.size() - 1; i >= 0; i--) {
            if (elements.isEnabled(elementNames[i]) && elementsList.get(i).mouseReleased(event)) {
                return true;
            }
        }
        return ElementManager.getInstance().handleMouseReleased(event);
    }

    public boolean handleMouseScrolled(double guiMouseX, double guiMouseY, double scrollY) {
        double mouseX = Render2D.guiToFixed(guiMouseX);
        double mouseY = Render2D.guiToFixed(guiMouseY);
        if (!isEnabled()) {
            return ScoreboardStyleMenu.getInstance().mouseScrolled(mouseX, mouseY, scrollY);
        }
        if (HudElementsPopup.getInstance().mouseScrolled(mouseX, mouseY, scrollY)
                || ScoreboardStyleMenu.getInstance().mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        for (int i = elementsList.size() - 1; i >= 0; i--) {
            HudElement element = elementsList.get(i);
            if (elements.isEnabled(elementNames[i]) && element.mouseScrolled(mouseX, mouseY, scrollY)) {
                return true;
            }
        }
        return false;
    }

    public void closeEditorState() {
        ElementManager.getInstance().cancelActiveElement();
        HudElementStyleMenu.getInstance().close();
        HudElementsPopup.getInstance().close();
        ScoreboardStyleMenu.getInstance().close();
    }

    private boolean isDoubleClick(HudMouseEvent event) {
        long now = UiFrameContext.nowMillis();
        double dx = event.x() - lastClickX;
        double dy = event.y() - lastClickY;
        boolean doubled = event.button() == lastClickButton
                && now - lastClickAt <= 250L
                && dx * dx + dy * dy <= 9.0;
        lastClickAt = now;
        lastClickButton = event.button();
        lastClickX = event.x();
        lastClickY = event.y();
        return doubled;
    }

    private void renderHudPanel(HudPanel panel, boolean useConfiguredScale) {
        if (panel == null) {
            return;
        }
        panel.prepareFrame();
        float scale = useConfiguredScale ? panel.configuredHudScale() : 1.0F;
        panel.prepareHudScale(scale);

        DrawContext graphics = Render2D.currentGraphics();
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
            graphics.getMatrices().push();
            try {

                float guiCenterX = Render2DCoordinateSpace.toGui(centerX);
                float guiCenterY = Render2DCoordinateSpace.toGui(centerY);
                float guiAnchorX = Render2DCoordinateSpace.toGui(anchorX);
                float guiAnchorY = Render2DCoordinateSpace.toGui(anchorY);
                graphics.getMatrices().translate(guiCenterX, guiCenterY, 0.0F);
                graphics.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(tiltDegrees));
                graphics.getMatrices().scale(dragScale, dragScale, 1.0F);
                graphics.getMatrices().translate(-guiCenterX, -guiCenterY, 0.0F);
                graphics.getMatrices().translate(guiAnchorX, guiAnchorY, 0.0F);
                graphics.getMatrices().scale(appliedScale, appliedScale, 1.0F);
                graphics.getMatrices().translate(-guiAnchorX, -guiAnchorY, 0.0F);
                Matrix4f transformedMatrix = new Matrix4f(graphics.getMatrices().peek().getPositionMatrix());
                float guiIndependentScale = Render2DCoordinateSpace.guiIndependentScale();
                if (Math.abs(guiIndependentScale - 1.0F) > 0.0001F) {
                    transformedMatrix.scale(guiIndependentScale, guiIndependentScale, 1.0F);
                }
                Render2D.withProjection(
                        transformedMatrix,
                        (px, py) -> projectHudPoint(px, py, anchorX, anchorY, centerX, centerY, appliedScale, dragScale, tiltDegrees),
                        panel::render
                );
            } finally {
                graphics.getMatrices().pop();
            }
        } finally {
            panel.finishHudScale();
        }
    }

    private Render2D.ProjectedPoint projectHudPoint(
            float px,
            float py,
            float anchorX,
            float anchorY,
            float centerX,
            float centerY,
            float appliedScale,
            float dragScale,
            float tiltDegrees
    ) {
        float scaledX = anchorX + (px - anchorX) * appliedScale;
        float scaledY = anchorY + (py - anchorY) * appliedScale;
        float dx = (scaledX - centerX) * dragScale;
        float dy = (scaledY - centerY) * dragScale;
        float radians = (float) Math.toRadians(tiltDegrees);
        float sin = (float) Math.sin(radians);
        float cos = (float) Math.cos(radians);
        return new Render2D.ProjectedPoint(
                centerX + dx * cos - dy * sin,
                centerY + dx * sin + dy * cos
        );
    }

    private void refreshElementSelection() {
        long frame = UiFrameContext.active() ? UiFrameContext.frameId() : Long.MIN_VALUE;
        if (frame != Long.MIN_VALUE && elementSelectionFrame == frame) {
            return;
        }
        for (int i = 0; i < elementNames.length; i++) {
            elementSelectedFrame[i] = elements.isEnabled(elementNames[i]);
        }
        elementSelectionFrame = frame;
    }

    private static String resolveElementName(HudElement element) {
        return element instanceof HudPanel panel ? panel.elementName() : element.getClass().getSimpleName();
    }

    private void setElementVisible(HudElement element, boolean visible) {
        if (element instanceof HudPanel panel) {
            panel.setHudVisible(visible);
        }
    }

    private static float clamp(float value, float min, float max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    public String potionsViewMode() { return potionsView.getValue(); }
    public void setPotionsViewMode(String mode) { potionsView.setValue(mode); }
    public String cooldownsViewMode() { return cooldownsView.getValue(); }
    public void setCooldownsViewMode(String mode) { cooldownsView.setValue(mode); }
    public boolean cooldownsSecondMode() { return "2".equals(cooldownsView.getValue()); }
    public boolean isCooldownsSelected() { return elements.isSelected(HudElementId.COOLDOWNS); }
}
