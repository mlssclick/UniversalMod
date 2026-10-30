package universalmod.hud.element;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import universalmod.hud.ElementScreen;
import universalmod.hud.editor.HudPanel;
import universalmod.gui.clickgui.ClickGuiMsdfIcons;
import universalmod.service.language.LanguageManager;
import universalmod.render.font.FontType;
import universalmod.render.item.RenderItem;
import universalmod.render.item.RenderItemOptions;


public final class Inventory extends HudPanel {
    private static final float WIDTH = 116.0F;
    private static final float HEADER_H = 14.0F;
    private static final float ROW_H = 12.0F;
    private static final float ROW_GAP = 2.0F;
    private static final float HEIGHT = HEADER_H + ROW_H * 3.0F + ROW_GAP * 2.0F + 4.0F;
    private static final String HEADER_ICON_GLYPH = "^";
    private static final ItemStack[] PREVIEW = {
            Items.ENDER_PEARL.getDefaultInstance(), Items.GOLDEN_APPLE.getDefaultInstance(), Items.TOTEM_OF_UNDYING.getDefaultInstance(),
            Items.SUGAR.getDefaultInstance(), Items.ENDER_PEARL.getDefaultInstance(), Items.GOLDEN_APPLE.getDefaultInstance(),
            Items.TOTEM_OF_UNDYING.getDefaultInstance(), Items.SUGAR.getDefaultInstance(), Items.ENDER_PEARL.getDefaultInstance()
    };
    private final ItemStack[] inventoryStacks = new ItemStack[27];

    public Inventory() {
        super("inventory", "Inventory", defaultX(), defaultY(), WIDTH, HEIGHT);
        for (int i = 0; i < inventoryStacks.length; i++) {
            inventoryStacks[i] = ItemStack.EMPTY;
        }
        sizeImmediate(WIDTH, HEIGHT);
    }

    @Override
    public void render() {
        boolean hasItems = inventoryItems();
        boolean preview = !hasItems && editPreview();
        float alpha = contentAlpha(hasItems || preview);
        if (alpha <= 0.01F) {
            return;
        }

        sizeImmediate(WIDTH, HEIGHT);
        float x = drag.x();
        float y = drag.y();
        HudVisualStyle.panel(x, y, WIDTH, HEIGHT, 4.4F, alpha);
        HudVisualStyle.title(LanguageManager.settingOption("HUD", "Elements", "Inventory"), x + 5.0F, y + 4.0F, alpha);
        ClickGuiMsdfIcons.drawCentered(FontType.DELTA_ICONS, HEADER_ICON_GLYPH,
                x + WIDTH - 10.5F, y + 3.4F, 6.8F, 6.8F, 0.76F, HudVisualStyle.accent(alpha));

        for (int row = 0; row < 3; row++) {
            float rowY = y + HEADER_H + row * (ROW_H + ROW_GAP);
            HudVisualStyle.inset(x + 4.0F, rowY, WIDTH - 8.0F, ROW_H, 2.8F, alpha);
        }

        RenderItem.beginItemLayer();
        final float itemSize = 9.0F;
        final float step = (WIDTH - 12.0F) / 9.0F;
        for (int row = 0; row < 3; row++) {
            float rowY = y + HEADER_H + row * (ROW_H + ROW_GAP);
            for (int col = 0; col < 9; col++) {
                int index = row * 9 + col;
                ItemStack stack = preview
                        ? (index < PREVIEW.length ? PREVIEW[index] : ItemStack.EMPTY)
                        : inventoryStacks[index];
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                float itemX = x + 6.0F + col * step + (step - itemSize) * 0.5F;
                float itemY = rowY + 1.5F;
                RenderItem.item(stack, itemX, itemY, itemSize, RenderItemOptions.countNoDurability(alpha));
            }
        }
    }

    private boolean inventoryItems() {
        if (mc.player == null) {
            clearInventoryCache();
            return false;
        }
        boolean hasItems = false;
        for (int slot = 9; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            ItemStack safe = stack == null ? ItemStack.EMPTY : stack;
            inventoryStacks[slot - 9] = safe;
            hasItems |= !safe.isEmpty();
        }
        return hasItems;
    }

    private void clearInventoryCache() {
        for (int i = 0; i < inventoryStacks.length; i++) {
            inventoryStacks[i] = ItemStack.EMPTY;
        }
    }

    private static float defaultX() {
        ElementScreen screen = ElementScreen.current();
        return Math.max(4.0F, screen.width() - WIDTH - 32.0F);
    }

    private static float defaultY() {
        ElementScreen screen = ElementScreen.current();
        return Math.max(4.0F, screen.height() * 0.67F);
    }
}
