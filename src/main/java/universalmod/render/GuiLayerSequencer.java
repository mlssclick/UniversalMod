package universalmod.render;

import net.minecraft.client.gui.DrawContext;
import universalmod.render.blur.KawaseBlurRenderer;
import universalmod.render.font.msdf.MsdfTextBatch;
import universalmod.render.smooth.SmoothRoundedRenderer;

 











public final class GuiLayerSequencer {
    public enum Layer {
        GAME_EVENTS,
        HUD,
        CLICK_GUI_OVERLAY
    }

    private static long serial;
    private static Layer lastLayer;

    private GuiLayerSequencer() {}

     



    private static void flushCustomBatches() {
        MsdfTextBatch.flushPending();
        SmoothRoundedRenderer.getInstance().flush();
    }

    public static void render(DrawContext context, Layer layer, Runnable draw) {
        if (context == null || draw == null) return;
        
        flushCustomBatches();
        context.draw();
        
        KawaseBlurRenderer.invalidateScene();
        lastLayer = layer;
        serial++;
        try {
            draw.run();
        } finally {
            
            
            flushCustomBatches();
            context.draw();
        }
    }

    public static long currentSerial() {
        return serial;
    }

    public static Layer currentLayer() {
        return lastLayer;
    }
}
