package universalmod.render.item;


import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import universalmod.render.Render2DCoordinateSpace;

public final class CustomItemRenderer {
    private static volatile CustomItemRenderer instance;

    private GuiGraphics activeGraphics;

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

    public void beginFrame(GuiGraphics graphics) {
        activeGraphics = graphics;
    }

    public void enqueue(ItemRenderCommand item) {
        if (item == null) {
            return;
        }
        submit(activeGraphics, item.stack(), item.x(), item.y(), item.size(), item.seed());
    }

    public void enqueue(ItemStack stack, float x, float y, float size, int seed) {
        submit(activeGraphics, stack, x, y, size, seed);
    }

    public void flush() {
        activeGraphics = null;
    }

    private void submit(GuiGraphics graphics, ItemStack stack, float x, float y, float size, int seed) {
        if (graphics == null || stack == null || stack.isEmpty() || size <= 0.0f) {
            return;
        }

        submitVanillaItem(graphics, stack, x, y, size, seed);
    }

    public void beginItemLayer() {
        GuiGraphics graphics = activeGraphics;
        if (graphics != null) {
            graphics.nextStratum();
        }
    }

    private void submitVanillaItem(
            GuiGraphics graphics,
            ItemStack stack,
            float x,
            float y,
            float size,
            int seed
    ) {
        float scale = size / 16.0f;
        if (scale <= 0.0f) {
            return;
        }

        graphics.pose().pushMatrix();
        try {
            Render2DCoordinateSpace.applyGuiScaleIndependence(graphics.pose());
            graphics.pose().translate(x, y);
            graphics.pose().scale(scale);
            graphics.renderItem(stack, 0, 0, seed);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    public void close() {
        activeGraphics = null;
    }
}
