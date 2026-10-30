package universalmod.lifecycle;

import universalmod.render.hud.LoadingVisualGuard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import universalmod.hud.ElementManager;
import universalmod.hud.editor.ScoreboardStyleMenu;
import universalmod.event.type.DrawEvent;
import universalmod.module.render.Hud;
import universalmod.module.render.Scoreboard;
import universalmod.lifecycle.Manager;
import universalmod.render.Render2D;
import universalmod.render.optimize.UiFrameContext;

public final class HudRenderLayer {
    private HudRenderLayer() {
    }

    public static boolean shouldRender(Minecraft client) {
        return client != null
                && client.player != null
                && client.level != null
                && client.getWindow() != null
                && !LoadingVisualGuard.shouldSuppressHud(client);
    }

    public static void renderGame(GuiGraphics graphics, float partialTick) {
        renderGame(Minecraft.getInstance(), graphics, partialTick);
    }

    public static void renderGame(Minecraft client, GuiGraphics graphics, float partialTick) {
        UiFrameContext.begin(graphics);
        try {
            renderComposite(client, graphics, partialTick, DrawEvent.Layer.GAME);
        } finally {
            UiFrameContext.end();
        }
    }

    public static void renderGameDrawEvents(GuiGraphics graphics, float partialTick) {
        UiFrameContext.begin(graphics);
        try {
            renderDrawEvent(graphics, partialTick, DrawEvent.Layer.GAME);
            Minecraft client = Minecraft.getInstance();
            if (!(client.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) {
                renderScoreboard(graphics, false);
            }
        } finally {
            UiFrameContext.end();
        }
    }

    public static void renderGameHud(Minecraft client, GuiGraphics graphics, float partialTick) {
        UiFrameContext.begin(graphics);
        try {
            renderHudOnly(client, graphics, partialTick, DrawEvent.Layer.GAME);
        } finally {
            UiFrameContext.end();
        }
    }

    public static void renderScreenBackground(Minecraft client, GuiGraphics graphics, float partialTick) {
        if (!shouldRender(client) || client.screen == null) {
            return;
        }
        UiFrameContext.begin(graphics);
        try {
            renderHudOnly(client, graphics, partialTick, DrawEvent.Layer.SCREEN_BACKGROUND);
        } finally {
            UiFrameContext.end();
        }
    }

    public static void renderChatOverlay(Minecraft client, GuiGraphics graphics, float partialTick) {
        UiFrameContext.begin(graphics);
        try {
            ElementManager.getInstance().updateActiveElementFromMouse();
            renderScoreboard(graphics, true);
            renderHudOnly(client, graphics, partialTick, DrawEvent.Layer.CHAT_OVERLAY);
            renderScoreboardStyleMenu(graphics);
        } finally {
            UiFrameContext.end();
        }
    }


    private static void renderScoreboardStyleMenu(GuiGraphics graphics) {
        ScoreboardStyleMenu menu = ScoreboardStyleMenu.getInstance();
        if (!menu.isOpen()) {
            return;
        }
        Render2D.beginFrame(graphics);
        try {
            menu.render(graphics);
        } finally {
            Render2D.flush();
        }
        graphics.nextStratum();
    }

    private static void renderScoreboard(GuiGraphics graphics, boolean editorLayer) {
        Scoreboard scoreboard = Scoreboard.getInstance();
        if (scoreboard == null || !scoreboard.isEnabled()) {
            return;
        }

        Render2D.beginFrame(graphics);
        try {
            scoreboard.render(graphics, editorLayer);
        } finally {
            Render2D.flush();
        }
        graphics.nextStratum();
    }

    private static void renderComposite(Minecraft client, GuiGraphics graphics, float partialTick, DrawEvent.Layer hudLayer) {
        if (!shouldRender(client)) {
            return;
        }

        renderDrawEvent(graphics, partialTick, DrawEvent.Layer.GAME);
        renderHudOnly(client, graphics, partialTick, hudLayer);
    }

    private static void renderHudOnly(Minecraft client, GuiGraphics graphics, float partialTick, DrawEvent.Layer layer) {
        if (!shouldRender(client)) {
            return;
        }

        Hud hud = Hud.getInstance();
        if (hud == null || !hud.isEnabled()) {
            return;
        }

        Render2D.beginFrame(graphics);
        hud.renderHudLayer(new DrawEvent(graphics, partialTick, layer));
        Render2D.flush();
        graphics.nextStratum();
    }

    private static void renderDrawEvent(GuiGraphics graphics, float partialTick, DrawEvent.Layer layer) {
        Render2D.beginFrame(graphics);
        Manager.postEvent(new DrawEvent(graphics, partialTick, layer));
        Render2D.flush();
        graphics.nextStratum();
    }
}
