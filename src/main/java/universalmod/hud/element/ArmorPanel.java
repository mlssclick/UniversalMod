package universalmod.hud.element;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import universalmod.hud.ElementScreen;
import universalmod.hud.HudStyleOverrides;
import universalmod.hud.editor.HudPanel;
import universalmod.module.render.Hud;
import universalmod.render.Render2D;
import universalmod.render.color.ColorUtil;
import universalmod.render.item.RenderItem;
import universalmod.render.item.RenderItemOptions;
public final class ArmorPanel extends HudPanel {
    private static final float WIDTH = 18.0F;
    private static final float PAD_Y = 2.4F;
    private static final float ROW = 9.4F;
    private static final float GAP = 1.25F;
    private static final float HEIGHT = PAD_Y * 2.0F + ROW * 4.0F + GAP * 3.0F;
    private static final float TRACK_X = 2.0F;
    private static final float TRACK_W = 1.45F;
    private static final float TRACK_H = 6.5F;
    private static final float ITEM_X = 6.0F;
    private static final float ITEM_SIZE = 7.9F;

    private static final Identifier VANILLA_HOTBAR = new Identifier("minecraft", "textures/gui/widgets.png");

    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    public ArmorPanel() {
        super("armor", "Armor", 16.0F, defaultY(), WIDTH, HEIGHT);
        sizeImmediate(WIDTH, HEIGHT);
    }

    @Override
    public void prepareFrame() {
        Hud hud = Hud.getInstance();
        if (hud != null && hud.armorVanillaMode()) {
            int count = armorCount();
            float width = ArmorVanillaLayout.width(Math.max(1, count));
            ElementScreen screen = ElementScreen.current();
            ArmorVanillaLayout.Side side = hud.armorOnLeft()
                    ? ArmorVanillaLayout.Side.LEFT : ArmorVanillaLayout.Side.RIGHT;
            float targetX = ArmorVanillaLayout.x(Math.round(screen.width()), Math.max(1, count), side);
            float targetY = ArmorVanillaLayout.y(Math.round(screen.height()));
            drag.visualOffset(targetX - drag.baseX(), targetY - drag.baseY());
            drag.locked(true);
            sizeImmediate(width, ArmorVanillaLayout.HEIGHT);
        } else {
            drag.clearVisualOffset();
            drag.locked(false);
            sizeImmediate(WIDTH, HEIGHT);
        }
    }

    @Override
    protected float visualBaseScale() {
        Hud hud = Hud.getInstance();
        return hud != null && hud.armorVanillaMode() ? 1.0F : HudStyleOverrides.VISUAL_BASE_SCALE * 1.30F;
    }

    @Override
    public boolean supportsHudScale() {
        return true;
    }

    @Override
    public void render() {
        Hud hud = Hud.getInstance();
        if (hud != null && hud.armorVanillaMode()) {
            renderVanilla(hud);
        } else {
            renderPanel();
        }
    }

    private void renderPanel() {
        boolean hasArmor = armorCount() > 0;
        float alpha = contentAlpha(hasArmor);
        if (alpha <= 0.01F || mc.player == null) {
            return;
        }

        float x = drag.x();
        float y = drag.y();
        sizeImmediate(WIDTH, HEIGHT);
        HudVisualStyle.blackPanel(x, y, WIDTH, HEIGHT, 5.0F, alpha);

        boolean hasRenderableItem = false;
        for (int i = 0; i < SLOTS.length; i++) {
            ItemStack stack = mc.player.getEquippedStack(SLOTS[i]);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            hasRenderableItem = true;

            float rowY = y + PAD_Y + i * (ROW + GAP);
            float durability = durability(stack);
            float trackY = rowY + (ROW - TRACK_H) * 0.5F;

            Render2D.rect(x + TRACK_X, trackY, TRACK_W, TRACK_H, TRACK_W * 0.5F,
                    HudVisualStyle.alpha(0xFF34323A, alpha));
            float fillH = TRACK_H * durability;
            if (fillH > 0.05F) {
                Render2D.rect(x + TRACK_X, trackY + (TRACK_H - fillH), TRACK_W, fillH,
                        TRACK_W * 0.5F, HudVisualStyle.accent(alpha));
            }
        }

        if (!hasRenderableItem) {
            return;
        }

        for (int i = 0; i < SLOTS.length; i++) {
            ItemStack stack = mc.player.getEquippedStack(SLOTS[i]);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            float rowY = y + PAD_Y + i * (ROW + GAP);
            RenderItem.item(stack, x + ITEM_X, rowY + (ROW - ITEM_SIZE) * 0.5F, ITEM_SIZE,
                    RenderItemOptions.noDecorations(alpha));
        }
    }

    private void renderVanilla(Hud hud) {
        int count = armorCount();
        float alpha = contentAlpha(count > 0);
        if (alpha <= 0.01F || mc.player == null || count <= 0) {
            return;
        }

        int width = ArmorVanillaLayout.width(count);
        ElementScreen screen = ElementScreen.current();
        ArmorVanillaLayout.Side side = hud.armorOnLeft()
                ? ArmorVanillaLayout.Side.LEFT : ArmorVanillaLayout.Side.RIGHT;
        int x = ArmorVanillaLayout.x(Math.round(screen.width()), count, side);
        int y = ArmorVanillaLayout.y(Math.round(screen.height()));
        drag.visualOffset(x - drag.baseX(), y - drag.baseY());
        sizeImmediate(width, ArmorVanillaLayout.HEIGHT);

        DrawContext graphics = Render2D.currentGraphics();
        if (graphics == null) {
            return;
        }

        int tint = ColorUtil.rgba(255, 255, 255, Math.round(255.0F * alpha));
        int bodyWidth = Math.max(0, width - 3);
        graphics.drawTexture(VANILLA_HOTBAR, x, y, 0, 0, bodyWidth, ArmorVanillaLayout.HEIGHT, 256, 256);
        graphics.drawTexture(VANILLA_HOTBAR, x + bodyWidth, y, 179, 0, 3, ArmorVanillaLayout.HEIGHT, 256, 256);

        int slot = 0;
        for (EquipmentSlot equipmentSlot : SLOTS) {
            ItemStack stack = mc.player.getEquippedStack(equipmentSlot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            float itemX = ArmorVanillaLayout.itemX(x, slot);
            float itemY = y + 3.0F;
            RenderItem.item(stack, itemX, itemY, 16.0F, RenderItemOptions.durabilityOnly(alpha));
            slot++;
        }
    }

    private int armorCount() {
        if (mc.player == null) {
            return 0;
        }
        int count = 0;
        for (EquipmentSlot slot : SLOTS) {
            if (!mc.player.getEquippedStack(slot).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static float durability(ItemStack stack) {
        if (!stack.isDamageable() || stack.getMaxDamage() <= 0) {
            return 1.0F;
        }
        return Math.max(0.0F, Math.min(1.0F,
                1.0F - stack.getDamage() / (float) stack.getMaxDamage()));
    }

    private static float defaultY() {
        ElementScreen screen = ElementScreen.current();
        return Math.max(4.0F, screen.height() - HEIGHT - 18.0F);
    }
}
