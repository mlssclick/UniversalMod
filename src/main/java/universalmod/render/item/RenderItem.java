package universalmod.render.item;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import universalmod.render.color.ColorUtil;
import universalmod.render.Render2D;
import universalmod.render.font.FontType;

public final class RenderItem {
    private RenderItem() {
    }

    public static void beginFrame(DrawContext graphics) {
        renderer().beginFrame(graphics);
    }

    public static void flush() {
        renderer().flush();
    }

    public static void beginItemLayer() {
        renderer().beginItemLayer();
    }

    public static void item(ItemStack stack, float x, float y, float size) {
        item(stack, x, y, size, RenderItemOptions.defaults());
    }

    public static void item(ItemStack stack, float x, float y, float size, float alpha) {
        item(stack, x, y, size, RenderItemOptions.decorated(alpha));
    }

    public static void item(ItemStack stack, float x, float y, float size, RenderItemOptions options) {
        BuiltRenderItem item = new BuiltRenderItem(stack, x, y, size, options, 0);
        renderer().enqueue(item);
        drawDecorations(item);
    }

    private static void drawDecorations(BuiltRenderItem item) {
        if (item == null || !item.visible()) {
            return;
        }
        ItemStack stack = item.stack();
        RenderItemOptions options = item.options();
        if (options.showDurability() && stack.isItemBarVisible()) {
            drawDurabilityBar(stack, item.x(), item.y(), item.size(), options.alpha(), options.vanillaDurabilityStyle());
        }
        if (options.showCount() && stack.getCount() > 1) {
            drawCount(stack.getCount(), item.x(), item.y(), item.size(), options.alpha());
        }
    }

    private static void drawDurabilityBar(ItemStack stack, float x, float y, float size, float alpha,
                                          boolean vanillaStyle) {
        if (vanillaStyle) {
            float pixel = size / 16.0F;
            float barWidth = 13.0F * pixel;
            float backgroundHeight = Math.max(0.72F, 2.0F * pixel);
            float fillHeight = Math.max(0.42F, pixel);
            float barX = x + 2.0F * pixel;
            float barY = y + 13.0F * pixel;
            float fill = Math.max(0.0F, Math.min(1.0F, stack.getItemBarStep() / 13.0F));
            int barColor = stack.getItemBarColor();
            Render2D.rect(barX, barY, barWidth, backgroundHeight, 0.0F,
                    ColorUtil.rgba(0, 0, 0, Math.round(255.0F * alpha)));
            if (fill > 0.0F) {
                Render2D.rect(barX, barY, Math.max(pixel, barWidth * fill), fillHeight, 0.0F,
                        ColorUtil.rgba((barColor >>> 16) & 0xFF, (barColor >>> 8) & 0xFF,
                                barColor & 0xFF, Math.round(255.0F * alpha)));
            }
            return;
        }
        float barWidth = Math.max(8.0f, size * 0.78f);
        float barHeight = Math.max(1.5f, size * 0.07f);
        float barX = x + (size - barWidth) * 0.5f;
        float barY = y + size - barHeight - Math.max(1.0f, size * 0.08f);
        float fill = Math.max(0.0f, Math.min(1.0f, stack.getItemBarStep() / 13.0f));
        int barColor = stack.getItemBarColor();
        Render2D.rect(barX, barY, barWidth, barHeight, barHeight * 0.5f,
                ColorUtil.rgba(0, 0, 0, Math.round(150.0f * alpha)));
        Render2D.rect(barX, barY, Math.max(1.0f, barWidth * fill), barHeight, barHeight * 0.5f,
                ColorUtil.rgba((barColor >>> 16) & 0xFF, (barColor >>> 8) & 0xFF, barColor & 0xFF,
                        Math.round(240.0f * alpha)));
    }

    private static void drawCount(int count, float x, float y, float size, float alpha) {
        String text = Integer.toString(count);
        float textSize = Math.max(4.0f, Math.min(6.0f, size * 0.5f));
        float textWidth = Render2D.textWidth(FontType.BOLD, text, textSize);
        float textX = x + size - textWidth + size * 0.08f;
        float textY = y + size - textSize + size * 0.04f;
        float shadowOffset = Math.max(0.35f, Math.min(0.75f, size * 0.07f));
        Render2D.text(FontType.BOLD, text, textX + shadowOffset, textY + shadowOffset, textSize,
                ColorUtil.rgba(0, 0, 0, Math.round(170.0f * alpha)));
        Render2D.text(FontType.BOLD, text, textX, textY, textSize,
                ColorUtil.rgba(255, 255, 255, Math.round(255.0f * alpha)));
    }

    private static CustomItemRenderer renderer() {
        return CustomItemRenderer.getInstance();
    }
}
