package universalmod.render.item;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import universalmod.render.Render2DCoordinateSpace;
import universalmod.render.Render2D;

public final class CustomItemRenderer implements AutoCloseable {
    private static volatile CustomItemRenderer instance;
    private DrawContext activeGraphics;

    private CustomItemRenderer() {
    }

    public static CustomItemRenderer getInstance() {
        CustomItemRenderer local = instance;
        if (local == null) {
            synchronized (CustomItemRenderer.class) {
                local = instance;
                if (local == null) {
                    local = new CustomItemRenderer();
                    instance = local;
                }
            }
        }
        return local;
    }

    public static void closeInstance() {
        CustomItemRenderer local = instance;
        if (local != null) {
            local.close();
            instance = null;
        }
    }

    public void beginFrame(DrawContext graphics) {
        activeGraphics = graphics;
    }

    public void beginItemLayer() {
        DrawContext graphics = activeGraphics;
        if (graphics != null) {

            Render2D.renderBarrier();
            graphics.draw();
        }
    }

    public void enqueue(BuiltRenderItem item) {
        submit(activeGraphics, item);
    }

    public void flush() {
        activeGraphics = null;
    }

    private void submit(DrawContext graphics, BuiltRenderItem item) {
        if (graphics == null || item == null || !item.visible()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getItemRenderer() == null) {
            return;
        }
        try {
            float scale = item.size() / 16.0f;
            if (scale <= 0.0f) {
                return;
            }
            graphics.getMatrices().push();
            try {
                float fixed = Render2DCoordinateSpace.guiIndependentScale();
                graphics.getMatrices().scale(fixed, fixed, 1.0f);
                graphics.getMatrices().translate(item.x(), item.y(), 0.0f);
                graphics.getMatrices().scale(scale, scale, 1.0f);
                graphics.drawItem(item.stack(), 0, 0, item.seed());
            } finally {
                graphics.getMatrices().pop();
            }
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void close() {
        activeGraphics = null;
    }
}
