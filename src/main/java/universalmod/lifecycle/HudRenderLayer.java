package universalmod.lifecycle;


import universalmod.render.hud.LoadingVisualGuard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import universalmod.event.type.DrawEvent;
import universalmod.lifecycle.Manager;
import universalmod.render.optimize.UiFrameContext;






public final class HudRenderLayer {
    private HudRenderLayer() {
    }

    public static boolean shouldRender(MinecraftClient client) {
        return client != null
                && client.player != null
                && client.world != null
                && client.options != null
                && !client.options.hudHidden
                && !LoadingVisualGuard.shouldSuppressHud(client);
    }

    public static void renderGame(DrawContext graphics, float partialTick) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!shouldRender(client) || graphics == null) {
            return;
        }
        UiFrameContext.begin(graphics);
        try {
            Manager.postEvent(new DrawEvent(graphics, partialTick, DrawEvent.Layer.GAME));
        } finally {
            UiFrameContext.end();
        }
    }

    public static void renderScreenBackground(DrawContext graphics, float partialTick) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!shouldRender(client) || client.currentScreen == null || graphics == null) {
            return;
        }
        UiFrameContext.begin(graphics);
        try {
            Manager.postEvent(new DrawEvent(graphics, partialTick, DrawEvent.Layer.SCREEN_BACKGROUND));
        } finally {
            UiFrameContext.end();
        }
    }

    public static void renderChatOverlay(DrawContext graphics, float partialTick) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!shouldRender(client) || graphics == null) {
            return;
        }
        UiFrameContext.begin(graphics);
        try {
            Manager.postEvent(new DrawEvent(graphics, partialTick, DrawEvent.Layer.CHAT_OVERLAY));
        } finally {
            UiFrameContext.end();
        }
    }
}
