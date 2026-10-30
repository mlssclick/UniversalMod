package universalmod.hud.element;


import universalmod.hud.editor.HudPanel;
import universalmod.lifecycle.Manager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import universalmod.hud.ElementManager;
import universalmod.hud.ElementScreen;
import universalmod.render.color.ColorUtil;
import universalmod.render.Render2D;
import universalmod.render.Render2DCoordinateSpace;
import universalmod.mixin.accessor.BossBarHudAccessor;
import universalmod.module.render.Hud;
import universalmod.module.render.NoRender;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BossbarPanel extends HudPanel {
    private static final int BAR_WIDTH = 182, BAR_HEIGHT = 5, VANILLA_BAR_Y = 12, VANILLA_TEXT_Y = 3, ENTRY_SPACING = 19, VANILLA_SNAP_DISTANCE_GUI = 12;
    private static final int VANILLA_TEXT_COLOR = 0xFFFFFFFF, PREVIEW_PROGRESS_COLOR = 0xFFFF55FF;
    private static final UUID PREVIEW_ID = UUID.fromString("32e7247d-5f69-4e06-bdfa-bebd3d8e9490");
    private final Hud hud;
    private final List<ClientBossBar> visibleEventsScratch = new ArrayList<>(8);
    private final ClientBossBar previewEvent = new ClientBossBar(PREVIEW_ID, Text.literal("Bossbar"), .72F, BossBar.Color.PINK, BossBar.Style.PROGRESS, false, false, false);
    private boolean hudSelected, wasMoving, snapToVanillaOnRelease;

    public BossbarPanel(Hud hud) {
        super("bossbar", "Bossbar", defaultX(), defaultY(), BAR_WIDTH, 14F);
        this.hud = hud;
        drag.minimumSize(BAR_WIDTH * .5F, 7F).screenMargins(0, 0, 0, 0).visible(false);
    }

    @Override
    protected float visualBaseScale() { return 1.0F; }

    @Override
    public float dragTiltDegrees() {
        return 0.0F;
    }

    @Override
    public float dragScale() {
        return 1.0F;
    }

    public void setHudSelected(boolean selected) {
        hudSelected = selected;
    }

    public boolean isHudSelected() {
        return hudSelected;
    }

    public boolean shouldReplaceVanilla() {
        return !NoRender.isActive("Bossbar") && hud != null && hud.isEnabled() && hud.isBossbarSelected();
    }

    public boolean shouldRenderDynamicFallback() {
        return false;
    }

    @Override
    public void render() {
        hudSelected = hud != null && hud.isBossbarSelected();
        if (NoRender.isActive("Bossbar")) {
            hide();
            return;
        }
        DrawContext graphics = Render2D.currentGraphics();
        if (graphics == null || mc == null || mc.getWindow() == null || mc.player == null) {
            hide();
            return;
        }
        List<ClientBossBar> events = visibleEvents(graphics.getScaledWindowHeight());
        boolean editorPreview = hudSelected && events.isEmpty() && mc.currentScreen instanceof ChatScreen;
        if (editorPreview) events = List.of(previewEvent);
        if (events.isEmpty()) {
            hide();
            return;
        }
        BossLayout layout = layout(events);
        float coordinateScale = Math.max(.0001F, Render2DCoordinateSpace.guiIndependentScale());
        float fixedWidth = layout.widthGui() / coordinateScale, fixedHeight = layout.heightGui() / coordinateScale;
        float physicalWidth = fixedWidth * hudScale(), physicalHeight = fixedHeight * hudScale();
        VanillaPlacement vanilla = vanillaPlacement(graphics, layout, coordinateScale);
        boolean movingNow = hudSelected && drag.moving();
        if (wasMoving && !movingNow && snapToVanillaOnRelease) {
            drag.resetPosition().position(vanilla.xFixed(), vanilla.yFixed());
            ElementManager.getInstance().save();
            snapToVanillaOnRelease = false;
        }
        float baseX, baseY;
        if (hudSelected) {
            drag.visible(true);
            sizeImmediate(fixedWidth, fixedHeight);
            if (!drag.positionCustomized()) drag.position(vanilla.xFixed(), vanilla.yFixed());
            drag.clamp(ElementScreen.current());
            baseX = drag.baseX();
            baseY = drag.baseY();
        } else {
            drag.visible(false).clearVisualOffset();
            baseX = vanilla.xFixed();
            baseY = vanilla.yFixed();
        }
        float renderX, renderY;
        if (hudSelected) {
            drag.clearVisualOffset();
            renderX = drag.x();
            renderY = drag.y();
        } else {
            renderX = baseX;
            renderY = baseY;
        }
        if (movingNow) {
            renderVanillaPlacementGhost(vanilla, fixedWidth, fixedHeight);
            snapToVanillaOnRelease = isNearVanillaPlacement(drag.x(), drag.y(), vanilla, physicalWidth, physicalHeight);
        } else if (!wasMoving) snapToVanillaOnRelease = false;
        wasMoving = movingNow;
        drawEvents(graphics, events, layout, renderX, renderY, coordinateScale);
    }

    private void hide() {
        drag.visible(false).clearVisualOffset();
        wasMoving = false;
        snapToVanillaOnRelease = false;
    }

    private List<ClientBossBar> visibleEvents(int guiHeight) {
        BossBarHud overlay = mc.inGameHud == null ? null : mc.inGameHud.getBossBarHud();
        BossBarHudAccessor accessor = accessor(overlay);
        if (accessor == null) return List.of();
        Map<UUID, ClientBossBar> map = accessor.universalmod$getBossBars();
        if (map == null || map.isEmpty()) return List.of();
        visibleEventsScratch.clear();
        int barY = VANILLA_BAR_Y;
        for (ClientBossBar event : map.values()) {
            if (event == null) continue;
            visibleEventsScratch.add(event);
            barY += ENTRY_SPACING;
            if (barY >= guiHeight / 3) break;
        }
        return visibleEventsScratch;
    }

    private BossLayout layout(List<ClientBossBar> events) {
        int width = BAR_WIDTH;
        for (ClientBossBar e : events) width = Math.max(width, mc.textRenderer.getWidth(e.getName()));
        return new BossLayout(width, 14 + Math.max(0, events.size() - 1) * ENTRY_SPACING, (width - BAR_WIDTH) / 2);
    }

    private VanillaPlacement vanillaPlacement(DrawContext g, BossLayout l, float scale) {
        float x = (g.getScaledWindowWidth() - l.widthGui()) * .5F;
        return new VanillaPlacement(x / scale, VANILLA_TEXT_Y / scale);
    }

    private void drawEvents(DrawContext graphics, List<ClientBossBar> events, BossLayout layout, float rx, float ry, float coordinateScale) {
        int leftGui = Math.round(rx * coordinateScale), topGui = Math.round(ry * coordinateScale), barLeft = leftGui + layout.barOffsetXGui(), centerX = leftGui + layout.widthGui() / 2;
        boolean customStyle = hud.isBossbarSelected() && hud.useCustomBossbarSettings();
        BossBarHud overlay = mc.inGameHud == null ? null : mc.inGameHud.getBossBarHud();
        BossBarHudAccessor accessor = accessor(overlay);
        for (int i = 0; i < events.size(); i++) {
            ClientBossBar event = events.get(i);
            int barY = topGui + (VANILLA_BAR_Y - VANILLA_TEXT_Y) + i * ENTRY_SPACING, textY = barY - 9;
            if (customStyle) {
                drawTintedVanillaBar(graphics, barLeft, barY, event, hud.bossbarColor());
                Text styledName = event.getName();
                int nw = mc.textRenderer.getWidth(styledName);
                graphics.drawText(mc.textRenderer, styledName, centerX - nw / 2, textY, hud.bossbarTextColor().getRGB(), true);
            } else {
                if (accessor != null) accessor.universalmod$renderBossBar(graphics, barLeft, barY, event);
                else drawTintedVanillaBar(graphics, barLeft, barY, event, new Color(PREVIEW_PROGRESS_COLOR, true));
                graphics.drawCenteredTextWithShadow(mc.textRenderer, event.getName(), centerX, textY, VANILLA_TEXT_COLOR);
            }
        }
    }

    private void drawTintedVanillaBar(DrawContext g, int x, int y, BossBar event, Color selected) {
        Color safe = selected == null ? new Color(PREVIEW_PROGRESS_COLOR, true) : selected;
        int progressTint = safe.getRGB();
        int backgroundTint = new Color(Math.max(0, Math.round(safe.getRed() * .35F)), Math.max(0, Math.round(safe.getGreen() * .35F)), Math.max(0, Math.round(safe.getBlue() * .35F)), safe.getAlpha()).getRGB();
        drawTintedSpritePair(g, x, y, event, BossBar.Color.WHITE.ordinal(), BossBarHudAccessor.universalmod$getBackgroundTextures(), BossBarHudAccessor.universalmod$getProgressTextures(), backgroundTint, progressTint);
        if (event.getStyle() != BossBar.Style.PROGRESS)
            drawTintedSpritePair(g, x, y, event, event.getStyle().ordinal() - 1, BossBarHudAccessor.universalmod$getNotchedBackgroundTextures(), BossBarHudAccessor.universalmod$getNotchedProgressTextures(), 0xFFFFFFFF, 0xFFFFFFFF);
    }

    private void drawTintedSpritePair(DrawContext g, int x, int y, BossBar event, int idx, Identifier[] bg, Identifier[] progress, int bgTint, int progressTint) {
        if (!validSpriteIndex(idx, bg, progress)) return;
        g.drawGuiTexture(RenderLayer::getGuiTextured, bg[idx], x, y, BAR_WIDTH, BAR_HEIGHT, bgTint);
        int pw = Math.round(clamp01(event.getPercent()) * BAR_WIDTH);
        if (pw <= 0) return;
        g.enableScissor(x, y, x + pw, y + BAR_HEIGHT);
        try {
            g.drawGuiTexture(RenderLayer::getGuiTextured, progress[idx], x, y, BAR_WIDTH, BAR_HEIGHT, progressTint);
        } finally {
            g.disableScissor();
        }
    }

    private static boolean validSpriteIndex(int i, Identifier[] b, Identifier[] p) {
        return i >= 0 && b != null && p != null && i < b.length && i < p.length && b[i] != null && p[i] != null;
    }

    private void renderVanillaPlacementGhost(VanillaPlacement v, float w, float h) {
        Render2D.rect(v.xFixed(), v.yFixed(), w, h, 0, ColorUtil.rgba(255, 255, 255, 72));
    }

    private boolean isNearVanillaPlacement(float x, float y, VanillaPlacement v, float w, float h) {
        float d = Math.max(1F, (float) Render2D.guiToFixed(VANILLA_SNAP_DISTANCE_GUI));
        return Math.abs(x + w * .5F - (v.xFixed() + w * .5F)) <= d && Math.abs(y + h * .5F - (v.yFixed() + h * .5F)) <= d;
    }

    private static BossBarHudAccessor accessor(BossBarHud overlay) {
        if (overlay == null) return null;
        try {
            return (BossBarHudAccessor) (Object) overlay;
        } catch (ClassCastException e) {
            return null;
        }
    }

    private static float clamp01(float v) {
        return Float.isFinite(v) ? Math.max(0, Math.min(1, v)) : 0;
    }

    private static float defaultX() {
        ElementScreen s = ElementScreen.current();
        return Math.max(0, (s.width() - BAR_WIDTH) * .5F);
    }

    private static float defaultY() {
        return Math.max(0, (float) Render2D.guiToFixed(VANILLA_TEXT_Y));
    }

    private record BossLayout(int widthGui, int heightGui, int barOffsetXGui) {
    }

    private record VanillaPlacement(float xFixed, float yFixed) {
    }
}
