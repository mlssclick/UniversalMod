package universalmod.render.item;

import universalmod.render.Render2D;
import universalmod.render.font.FontType;
import universalmod.render.color.ColorUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

public final class RenderItem {
    private RenderItem() {
    }

    public static void beginFrame(GuiGraphics graphics) {
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
        ItemStack safeStack = stack == null ? ItemStack.EMPTY : stack;
        RenderItemOptions safeOptions = options == null ? RenderItemOptions.defaults() : options;
        if (safeStack.isEmpty()
                || size <= 0.0f
                || safeOptions.alpha() <= 0.0f
                || (safeOptions.color() >>> 24) == 0) {
            return;
        }

        renderer().enqueue(safeStack, x, y, size, 0);
        drawDecorations(safeStack, x, y, size, safeOptions);
    }

    private static void drawDecorations(ItemStack stack, float x, float y, float size, RenderItemOptions options) {
        if (options.showDurability() && stack.isBarVisible()) {
            drawDurabilityBar(stack, x, y, size, options.alpha(), options.vanillaDurabilityStyle());
        }
        if (options.showCount() && stack.getCount() > 1) {
            drawCount(stack.getCount(), x, y, size, options.alpha());
        }
    }

    private static void drawDurabilityBar(
            ItemStack stack,
            float x,
            float y,
            float size,
            float alpha,
            boolean vanillaStyle
    ) {
        float fill = Math.max(0.0f, Math.min(1.0f, stack.getBarWidth() / 13.0f));
        int barColor = stack.getBarColor();

        if (vanillaStyle) {


            float pixel = size / 16.0F;
            float barWidth = 13.0F * pixel;
            float backgroundHeight = Math.max(0.72F, 2.0F * pixel);
            float fillHeight = Math.max(0.42F, 1.0F * pixel);
            float barX = x + 2.0F * pixel;
            float barY = y + 13.0F * pixel;
            Render2D.rect(barX, barY, barWidth, backgroundHeight, 0.0F,
                    ColorUtil.rgba(0, 0, 0, Math.round(255.0F * alpha)));
            if (fill > 0.0F) {
                Render2D.rect(
                        barX,
                        barY,
                        Math.max(pixel, barWidth * fill),
                        fillHeight,
                        0.0F,
                        ColorUtil.rgba(
                                (barColor >>> 16) & 0xFF,
                                (barColor >>> 8) & 0xFF,
                                barColor & 0xFF,
                                Math.round(255.0F * alpha)
                        )
                );
            }
            return;
        }

        float barWidth = Math.max(8.0f, size * 0.78f);
        float barHeight = Math.max(1.5f, size * 0.07f);
        float barX = x + (size - barWidth) * 0.5f;
        float barY = y + size - barHeight - Math.max(1.0f, size * 0.08f);
        Render2D.rect(barX, barY, barWidth, barHeight, barHeight * 0.5f,
                ColorUtil.rgba(0, 0, 0, Math.round(150.0f * alpha)));
        Render2D.rect(
                barX,
                barY,
                Math.max(1.0f, barWidth * fill),
                barHeight,
                barHeight * 0.5f,
                ColorUtil.rgba((barColor >>> 16) & 0xFF, (barColor >>> 8) & 0xFF, barColor & 0xFF, Math.round(240.0f * alpha))
        );
    }

    private static void drawCount(int count, float x, float y, float size, float alpha) {
        String text = Integer.toString(count);
        float textSize = Math.max(4.0f, Math.min(6.0f, size * 0.5f));
        float textWidth = Render2D.textWidth(FontType.BOLD, text, textSize);
        float textX = x + size - textWidth + size * 0.08f;
        float textY = y + size - textSize + size * 0.04f;
        float shadowOffset = Math.max(0.35f, Math.min(0.75f, size * 0.07f));
        int shadowAlpha = Math.round(170.0f * alpha);
        int textAlpha = Math.round(255.0f * alpha);

        Render2D.text(FontType.BOLD, text, textX + shadowOffset, textY + shadowOffset, textSize, ColorUtil.rgba(0, 0, 0, shadowAlpha));
        Render2D.text(FontType.BOLD, text, textX, textY, textSize, ColorUtil.rgba(255, 255, 255, textAlpha));
    }

    private static CustomItemRenderer renderer() {
        return CustomItemRenderer.getInstance();
    }
}
