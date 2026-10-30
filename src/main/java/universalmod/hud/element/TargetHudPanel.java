package universalmod.hud.element;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import universalmod.hud.ElementScreen;
import universalmod.hud.editor.HudPanel;
import universalmod.render.Render2D;
import universalmod.render.animation.Easings;
import universalmod.render.animation.SmoothAnimation;
import universalmod.render.color.ColorUtil;
import universalmod.render.font.FontType;
import universalmod.render.item.RenderItem;
import universalmod.render.item.RenderItemOptions;
public final class TargetHudPanel extends HudPanel {

    private static final float WIDTH = 87.5F;
    private static final float MAIN_HEIGHT = 27.0F;
    private static final float HEIGHT = 39.0F;
    private static final float ARMOR_Y = 28.7F;
    private static final float ARMOR_SIZE = 8.97F;
    private static final float ARMOR_GAP = 1.9F;
    private static final long HOLD_MS = 3200L;
    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private LivingEntity target;
    private long touchedAt;

    private final SmoothAnimation healthFillAnimation = new SmoothAnimation();
    private final SmoothAnimation healthTrailAnimation = new SmoothAnimation();
    private final SmoothAnimation absorptionFillAnimation = new SmoothAnimation();
    private final SmoothAnimation absorptionTrailAnimation = new SmoothAnimation();

    public TargetHudPanel() {
        super("target_hud", "Target HUD", defaultX(), defaultY(), WIDTH, HEIGHT);
        sizeImmediate(WIDTH, HEIGHT);
    }

    public void setTarget(LivingEntity target) {
        if (target == null) {
            return;
        }
        this.target = target;
        this.touchedAt = System.currentTimeMillis();
    }

    @Override
    public void render() {
        LivingEntity shown = resolveShownTarget();
        boolean visible = isShownTargetVisible(shown);
        if (!(shown instanceof AbstractClientPlayerEntity player)) {
            return;
        }
        float alpha = contentAlpha(visible);
        if (alpha <= 0.01F) {
            return;
        }

        sizeImmediate(WIDTH, HEIGHT);
        float x = drag.x();
        float y = drag.y();
        HudVisualStyle.blackPanel(x, y, WIDTH, MAIN_HEIGHT, 5.2F, alpha);
        renderPortrait(player, x, y, alpha);
        float textX = x + 28.2F;
        renderIdentityAndHealth(shown, x, y, textX, alpha);
        renderArmor(shown, textX, y, alpha);
    }

    private LivingEntity resolveShownTarget() {
        return editPreview() && mc.player != null ? mc.player : target;
    }

    private boolean isShownTargetVisible(LivingEntity shown) {
        if (editPreview() && mc.player != null) {
            return true;
        }
        return shown != null && shown.isAlive() && shown != mc.player && System.currentTimeMillis() - touchedAt <= HOLD_MS;
    }

    private void renderPortrait(AbstractClientPlayerEntity player, float x, float y, float alpha) {
        float portrait = 22.5F;
        float px = x + 2.25F;
        float py = y + 2.25F;
        String skin = player.getSkinTextures().texture().toString();
        int tint = ColorUtil.rgba(255, 255, 255, Math.round(255.0F * alpha));
        Render2D.imageUvNearest(skin, px, py, portrait, portrait, 4.6F,
                1.0F, 0.125F, 0.125F, 0.25F, 0.25F, tint);
        Render2D.imageUvNearest(skin, px, py, portrait, portrait, 4.6F,
                1.0F, 0.625F, 0.125F, 0.75F, 0.25F, tint);
    }

    private void renderIdentityAndHealth(LivingEntity shown, float x, float y, float textX, float alpha) {
        float maxHealth = Math.max(1.0F, shown.getMaxHealth());
        float health = Math.max(0.0F, Math.min(shown.getHealth(), maxHealth));
        String hp = oneDecimal(health);
        float hpSize = 5.2F;
        float hpW = Render2D.textWidth(FontType.BOLD, hp, hpSize);
        String rawName = shown.getName().getString();
        String name = trimToWidth(rawName, FontType.BOLD, 6.15F,
                Math.max(12.0F, WIDTH - (textX - x) - hpW - 6.0F));
        Render2D.text(FontType.BOLD, name, textX, y + 5.15F, 6.15F,
                HudVisualStyle.alpha(HudVisualStyle.TEXT, alpha));
        Render2D.text(FontType.BOLD, hp, x + WIDTH - hpW - 3.4F, y + 5.45F, hpSize,
                HudVisualStyle.accent(alpha));

        float barX = textX;
        float barY = y + 18.15F;
        float barW = WIDTH - (textX - x) - 3.6F;
        float barH = 1.6F;
        float healthTarget = clamp01(health / maxHealth);
        float absorptionTarget = clamp01(shown.getAbsorptionAmount() / maxHealth);
        updateHealthAnimations(healthTarget, absorptionTarget);
        renderHealthBars(barX, barY, barW, barH, alpha);
    }

    private void updateHealthAnimations(float healthTarget, float absorptionTarget) {
        healthFillAnimation.update();
        healthFillAnimation.run(healthTarget, 0.5D, Easings.EXPO_OUT, true);
        healthTrailAnimation.update();
        healthTrailAnimation.run(healthTarget, 2.5D, Easings.EXPO_OUT, true);
        if (absorptionTarget > 0.01F) {
            absorptionFillAnimation.update();
            absorptionFillAnimation.run(absorptionTarget, 0.5D, Easings.EXPO_OUT, true);
            absorptionTrailAnimation.update();
            absorptionTrailAnimation.run(absorptionTarget, 2.5D, Easings.EXPO_OUT, true);
        } else {
            absorptionFillAnimation.set(0.0D);
            absorptionTrailAnimation.set(0.0D);
        }
    }

    private void renderHealthBars(float barX, float barY, float barW, float barH, float alpha) {
        float healthFill = clamp01((float) healthFillAnimation.get());
        float healthTrail = clamp01((float) healthTrailAnimation.get());
        float absorptionFill = clamp01((float) absorptionFillAnimation.get());
        float absorptionTrail = clamp01((float) absorptionTrailAnimation.get());
        int accent = HudVisualStyle.accent(alpha);

        float trailWidth = barW * healthTrail;
        if (trailWidth > 0.5F) {
            Render2D.rect(barX, barY, trailWidth, barH, barH * 0.5F, ColorUtil.multAlpha(accent, 0.35F));
        }
        Render2D.rect(barX, barY, barW, barH, barH * 0.5F, ColorUtil.multAlpha(accent, 0.15F));
        float fastWidth = barW * healthFill;
        if (fastWidth > 0.5F) {
            Render2D.rect(barX, barY, fastWidth, barH, barH * 0.5F, accent);
        }

        float absorptionTrailWidth = barW * absorptionTrail;
        if (absorptionTrailWidth > 0.5F) {
            Render2D.rect(barX + barW - absorptionTrailWidth, barY, absorptionTrailWidth, barH, barH * 0.5F,
                    ColorUtil.rgba(130, 79, 4, Math.round(178.5F * alpha)));
        }
        float absorptionFastWidth = barW * absorptionFill;
        if (absorptionFastWidth > 0.5F) {
            Render2D.rect(barX + barW - absorptionFastWidth, barY, absorptionFastWidth, barH, barH * 0.5F,
                    ColorUtil.rgba(235, 161, 52, Math.round(229.5F * alpha)));
        }
    }

    private void renderArmor(LivingEntity shown, float textX, float y, float alpha) {
        int rendered = 0;
        boolean itemLayerStarted = false;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = shown.getEquippedStack(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (!itemLayerStarted) {

                itemLayerStarted = true;
            }
            RenderItem.item(stack,
                    textX + rendered * (ARMOR_SIZE + ARMOR_GAP),
                    y + ARMOR_Y,
                    ARMOR_SIZE,
                    RenderItemOptions.durabilityOnly(alpha));
            rendered++;
        }
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static String oneDecimal(float value) {
        int tenths = Math.max(0, Math.round(value * 10.0F));
        return (tenths / 10) + "." + (tenths % 10);
    }

    private static float defaultX() {
        ElementScreen s = ElementScreen.current();
        return Math.max(4.0F, s.width() * 0.5F - WIDTH * 0.5F);
    }

    private static float defaultY() {
        ElementScreen s = ElementScreen.current();
        return Math.max(4.0F, s.height() * 0.52F);
    }
}
