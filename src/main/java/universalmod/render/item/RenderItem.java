package universalmod.render.item;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import universalmod.render.color.ColorUtil;
import universalmod.render.font.FontType;
import universalmod.render.Render2D;
import universalmod.render.Render2DCoordinateSpace;

public final class RenderItem {
    private static DrawContext graphics;

    private RenderItem() {
    }

    public static void beginFrame(DrawContext context) {
        graphics = context;
    }

    public static void beginItemLayer() {
        
    }

    public static void flush() {
        graphics = null;
    }

    public static void item(ItemStack stack, float x, float y, float size) {
        item(stack, x, y, size, RenderItemOptions.defaults());
    }

    public static void item(ItemStack stack, float x, float y, float size, float alpha) {
        item(stack, x, y, size, RenderItemOptions.decorated(alpha));
    }

    public static void item(ItemStack stack, float x, float y, float size, RenderItemOptions options) {
        if (graphics == null || stack == null || stack.isEmpty() || size <= 0 || options == null || options.alpha() <= 0)
            return;
        float scale = size / 16f;
        graphics.getMatrices().push();
        try {
            float fixed = Render2DCoordinateSpace.guiIndependentScale();
            graphics.getMatrices().scale(fixed, fixed, 1f);
            graphics.getMatrices().translate(x, y, 0);
            graphics.getMatrices().scale(scale, scale, 1f);
            graphics.drawItem(stack, 0, 0);
        } finally {
            graphics.getMatrices().pop();
        }
        if (options.showDurability() && stack.isItemBarVisible()) drawDurability(stack, x, y, size, options.alpha());
        if (options.showCount() && stack.getCount() > 1) drawCount(stack.getCount(), x, y, size, options.alpha());
    }

    private static void drawDurability(ItemStack stack, float x, float y, float size, float alpha) {
        float bw = Math.max(8f, size * .78f), bh = Math.max(1.5f, size * .07f), bx = x + (size - bw) * .5f, by = y + size - bh - Math.max(1f, size * .08f);
        float fill = Math.max(0f, Math.min(1f, stack.getItemBarStep() / 13f));
        int c = stack.getItemBarColor();
        Render2D.rect(bx, by, bw, bh, bh * .5f, ColorUtil.rgba(0, 0, 0, Math.round(150 * alpha)));
        Render2D.rect(bx, by, Math.max(1f, bw * fill), bh, bh * .5f, ColorUtil.rgba((c >>> 16) & 255, (c >>> 8) & 255, c & 255, Math.round(240 * alpha)));
    }

    private static void drawCount(int count, float x, float y, float size, float alpha) {
        String text = Integer.toString(count);
        float ts = Math.max(4f, Math.min(6f, size * .5f));
        float tw = Render2D.textWidth(FontType.SEMIBOLD, text, ts);
        float tx = x + size - tw + size * .08f, ty = y + size - ts + size * .04f, sh = Math.max(.35f, Math.min(.75f, size * .07f));
        Render2D.text(FontType.SEMIBOLD, text, tx + sh, ty + sh, ts, ColorUtil.rgba(0, 0, 0, Math.round(170 * alpha)));
        Render2D.text(FontType.SEMIBOLD, text, tx, ty, ts, ColorUtil.rgba(255, 255, 255, Math.round(255 * alpha)));
    }
}
