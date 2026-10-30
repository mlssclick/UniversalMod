package universalmod.hud.element;

import net.minecraft.client.gui.DrawContext;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import universalmod.hud.ElementScreen;
import universalmod.hud.editor.HudPanel;
import universalmod.module.render.Hud;
import universalmod.service.language.LanguageManager;
import universalmod.render.Render2D;
import universalmod.render.font.FontType;
import universalmod.theme.ThemeColors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class Potions extends HudPanel {
    private static final float MIN_WIDTH = 80.0F;
    private static final float MAX_WIDTH = 106.0F;
    private static final float HEADER_H = 14.0F;
    private static final float ROW_H = 11.0F;
    private static final float ROW_GAP = 1.0F;
    private static final float TEXT_SIZE = 4.55F;
    private static final float EFFECT_ICON_SIZE = 6.2F;
    private static final float CARD_WIDTH = 82.0F;
    private static final float CARD_HEIGHT = 14.0F;
    private static final float CARD_GAP = 3.0F;
    private static final float CARD_TEXT_SIZE = 4.45F;
    private static final float CARD_ICON_SIZE = 6.8F;
    private static final String HEADER_ICON = "universalmod:textures/hud/header_potions.png";
    private static final Comparator<Row> ROW_COMPARATOR = Comparator.comparing(Row::name, String.CASE_INSENSITIVE_ORDER);
    private static final List<StatusEffect> PREVIEW_EFFECTS = List.of(
            StatusEffects.BLINDNESS,
            StatusEffects.DARKNESS,
            StatusEffects.MINING_FATIGUE,
            StatusEffects.WITHER
    );

    private final List<Row> rows = new ArrayList<>();
    private final List<Row> rowPool = new ArrayList<>();
    private final ListAnimator<StatusEffect, Row> animator = new ListAnimator<>();
    private final Map<StatusEffect, Integer> maxDurations = new HashMap<>();
    private boolean previewWasActive;
    private StatusEffect previewEffect = StatusEffects.BLINDNESS;
    private int previewDurationTicks = 84 * 20;

    public Potions() {
        super("potions", "Potions", defaultX(), defaultY(), MIN_WIDTH, HEADER_H + ROW_H + 3.0F);
    }

    @Override
    public void render() {
        collectRows();
        boolean modeTwo = Hud.getInstance() != null && "2".equals(Hud.getInstance().potionsViewMode());
        if (modeTwo) {
            renderCards();
        } else {
            renderPanel();
        }
    }

    private void collectRows() {
        rows.clear();
        if (mc.player != null) {
            Collection<StatusEffectInstance> active = mc.player.getStatusEffects();
            int poolIndex = 0;
            for (StatusEffectInstance instance : active) {
                StatusEffect effect = instance.getEffectType();
                Row row = obtainRow(poolIndex++);
                int duration = instance.getDuration();
                int max = Math.max(duration, maxDurations.getOrDefault(effect, duration));
                maxDurations.put(effect, max);
                row.set(effect, net.minecraft.text.Text.translatable(effect.getTranslationKey()).getString(), instance.getAmplifier() + 1, duration, max);
                rows.add(row);
            }
            rows.sort(ROW_COMPARATOR);
            maxDurations.keySet().retainAll(rows.stream().map(Row::effect).toList());
        }

        boolean preview = rows.isEmpty() && editPreview();
        if (preview) {
            if (!previewWasActive) {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                previewEffect = PREVIEW_EFFECTS.get(random.nextInt(PREVIEW_EFFECTS.size()));
                previewDurationTicks = random.nextInt(45, 180) * 20;
            }
            Row row = obtainRow(0);
            row.set(previewEffect, net.minecraft.text.Text.translatable(previewEffect.getTranslationKey()).getString(), 1, previewDurationTicks, 180 * 20);
            rows.add(row);
        }
        previewWasActive = preview;
    }

    private void renderPanel() {
        Collection<ListAnimator.Row<StatusEffect, Row>> animated = animator.sync(rows, Row::effect, ROW_H, ROW_GAP);
        float alpha = contentAlpha(!rows.isEmpty() || animator.hasRows());
        if (alpha <= 0.01F || animated.isEmpty()) {
            return;
        }

        float width = MIN_WIDTH;
        for (ListAnimator.Row<StatusEffect, Row> animatedRow : animated) {
            Row row = animatedRow.value();
            String label = effectLabel(row.name, row.level);
            String timer = duration(row.durationTicks);
            width = Math.max(width, 20.0F + Render2D.textWidth(FontType.BOLD, label, TEXT_SIZE)
                    + Render2D.textWidth(FontType.BOLD, timer, TEXT_SIZE) + 12.0F);
        }
        width = Math.min(MAX_WIDTH, width);
        float height = HEADER_H + animator.extent() + 3.0F;
        size(width, height);

        float x = drag.x();
        float y = drag.y();
        HudVisualStyle.panel(x, y, width, height, 4.2F, alpha);
        HudVisualStyle.title(LanguageManager.settingOption("HUD", "Elements", "Potions"), x + 5.0F, y + 4.0F, alpha);
        Render2D.image(HEADER_ICON, x + width - 10.0F, y + 3.6F, 6.7F, 6.7F, 0.0F,
                HudVisualStyle.accent(alpha));

        DrawContext graphics = Render2D.currentGraphics();
        for (ListAnimator.Row<StatusEffect, Row> animatedRow : animated) {
            if (animatedRow.progress() <= 0.001F) {
                continue;
            }
            Row row = animatedRow.value();
            float rowY = y + HEADER_H + animatedRow.offset();
            float rowAlpha = alpha * animatedRow.progress();
            float finalWidth = width;
            drawAnimatedRow(graphics, x, rowY, finalWidth, ROW_H, animatedRow.scale(), () ->
                    drawRow(row, x, rowY, finalWidth, rowAlpha));
        }
    }

    private void renderCards() {
        Collection<ListAnimator.Row<StatusEffect, Row>> animated = animator.sync(rows, Row::effect, CARD_HEIGHT, CARD_GAP);
        float alpha = contentAlpha(!rows.isEmpty() || animator.hasRows());
        if (alpha <= 0.01F || animated.isEmpty()) {
            return;
        }

        float height = Math.max(CARD_HEIGHT, animator.extent());
        size(CARD_WIDTH, height);
        float x = drag.x();
        float y = drag.y();

        for (ListAnimator.Row<StatusEffect, Row> animatedRow : animated) {
            if (animatedRow.progress() <= 0.001F) {
                continue;
            }
            Row row = animatedRow.value();
            float p = animatedRow.progress();
            float rowY = y + animatedRow.offset();
            float drawX = x - (1.0F - p) * 6.0F;
            float rowAlpha = alpha * p;
            drawCard(row, drawX, rowY, rowAlpha);
        }
    }

    private void drawCard(Row row, float x, float y, float alpha) {
        HudVisualStyle.panel(x, y, CARD_WIDTH, CARD_HEIGHT, 4.0F, alpha);
        String timer = duration(row.durationTicks);
        String label = effectLabel(row.name, row.level);

        float timerW = Render2D.textWidth(FontType.BOLD, timer, CARD_TEXT_SIZE);
        float centerY = y + CARD_HEIGHT * 0.5F;
        float labelY = visuallyCenteredTextY(label, CARD_TEXT_SIZE, centerY);
        float timerY = visuallyCenteredTextY(timer, CARD_TEXT_SIZE, centerY);
        float iconY = centerY - CARD_ICON_SIZE * 0.5F;

        float iconX = x + 4.0F;
        float timerX = x + CARD_WIDTH - 4.0F - timerW;
        float labelX = x + 14.0F;
        float availableLabel = Math.max(8.0F, timerX - labelX - 4.0F);
        String shown = trimToWidth(label, FontType.BOLD, CARD_TEXT_SIZE, availableLabel);

        Render2D.effectIcon(row.effect, iconX, iconY, CARD_ICON_SIZE,
                HudVisualStyle.alpha(HudVisualStyle.TEXT, alpha));
        Render2D.text(FontType.BOLD, shown, labelX, labelY, CARD_TEXT_SIZE, effectNameColor(row, alpha));
        Render2D.text(FontType.BOLD, timer, timerX, timerY, CARD_TEXT_SIZE, timerColor(row, alpha));

        float progress = row.maxDurationTicks <= 0 ? 0.0F : Math.max(0.0F, Math.min(1.0F,
                row.durationTicks / (float) row.maxDurationTicks));
        float barMargin = 4.0F;
        float barW = CARD_WIDTH - barMargin * 2.0F;
        float barH = 1.2F;
        float barY = y + CARD_HEIGHT - 2.0F;
        Render2D.rect(x + barMargin, barY, barW, barH, 0.6F,
                HudVisualStyle.alpha(0x46FFFFFF, alpha));
        if (progress > 0.0F) {
            Render2D.rect(x + barMargin, barY, Math.max(barH, barW * progress), barH, 0.6F,
                    ThemeColors.clickGuiSliderFillColor(alpha));
        }
    }

    private void drawRow(Row row, float x, float rowY, float width, float alpha) {
        String timer = duration(row.durationTicks);
        String fullLabel = effectLabel(row.name, row.level);
        float textH = Math.max(Render2D.textHeight(FontType.BOLD, fullLabel, TEXT_SIZE),
                Render2D.textHeight(FontType.BOLD, timer, TEXT_SIZE));
        float contentY = rowY + (ROW_H - textH) * 0.5F;
        float iconY = rowY + (ROW_H - EFFECT_ICON_SIZE) * 0.5F;
        Render2D.effectIcon(row.effect, x + 5.0F, iconY, EFFECT_ICON_SIZE,
                HudVisualStyle.alpha(HudVisualStyle.TEXT, alpha));

        float timerW = Render2D.textWidth(FontType.BOLD, timer, TEXT_SIZE);
        float timerX = x + width - 5.0F - timerW;
        String label = trimToWidth(fullLabel, FontType.BOLD, TEXT_SIZE,
                Math.max(12.0F, timerX - (x + 15.0F) - 4.0F));
        Render2D.text(FontType.BOLD, label, x + 15.0F, contentY, TEXT_SIZE, effectNameColor(row, alpha));
        Render2D.text(FontType.BOLD, timer, timerX, contentY, TEXT_SIZE, timerColor(row, alpha));
    }


    private static int effectNameColor(Row row, float alpha) {
        EffectPresentationPolicy.NameState state = EffectPresentationPolicy.nameState(isHarmful(row));
        int base = state == EffectPresentationPolicy.NameState.DANGER
                ? HudVisualStyle.RED : HudVisualStyle.TEXT;
        return HudVisualStyle.alpha(base, alpha);
    }

    private static int timerColor(Row row, float alpha) {
        int ticks = row == null ? -1 : row.durationTicks;
        return switch (EffectPresentationPolicy.timerState(isHarmful(row), ticks)) {
            case DANGER -> HudVisualStyle.alpha(HudVisualStyle.RED, alpha);
            case SUCCESS -> HudVisualStyle.alpha(HudVisualStyle.GREEN, alpha);
            case NORMAL -> HudVisualStyle.alpha(HudVisualStyle.TEXT, alpha);
        };
    }

    private static boolean isHarmful(Row row) {
        return row != null && row.effect != null && !row.effect.isBeneficial();
    }

    private static void drawAnimatedRow(DrawContext graphics, float x, float y, float width, float height, float scale, Runnable draw) {
        if (graphics == null || scale >= 0.999F) {
            draw.run();
            return;
        }
        float cx = x + width * 0.5F;
        float cy = y + height * 0.5F;
        graphics.getMatrices().push();
        try {
            graphics.getMatrices().translate(cx, cy, 0.0F);
            graphics.getMatrices().scale(scale, scale, 1.0F);
            graphics.getMatrices().translate(-cx, -cy, 0.0F);
            draw.run();
        } finally {
            graphics.getMatrices().pop();
        }
    }

    private Row obtainRow(int index) {
        while (rowPool.size() <= index) {
            rowPool.add(new Row());
        }
        return rowPool.get(index);
    }


    private static float visuallyCenteredTextY(String text, float size, float centerY) {
        Render2D.TextVisualBounds bounds = Render2D.textVisualBounds(FontType.BOLD, text, size);
        if (bounds == null || bounds.empty()) {
            return centerY - size * 0.5F;
        }
        return centerY - bounds.centerY();
    }

    private static String effectLabel(String name, int level) {
        return level <= 1 ? name : name + " " + level;
    }

    private static String duration(int ticks) {
        if (ticks < 0 || ticks >= 999_999_999) {
            return "∞";
        }
        int totalSeconds = Math.max(0, ticks / 20);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return minutes + ":" + twoDigits(seconds);
    }

    private static float defaultX() {
        ElementScreen screen = ElementScreen.current();
        return Math.max(4.0F, screen.width() * 0.73F);
    }

    private static float defaultY() {
        ElementScreen screen = ElementScreen.current();
        return Math.max(4.0F, screen.height() * 0.45F);
    }

    private static final class Row {
        private StatusEffect effect;
        private String name = "";
        private int level;
        private int durationTicks;
        private int maxDurationTicks;

        private void set(StatusEffect effect, String name, int level, int durationTicks, int maxDurationTicks) {
            this.effect = effect;
            this.name = name == null ? "" : name;
            this.level = level;
            this.durationTicks = durationTicks;
            this.maxDurationTicks = Math.max(durationTicks, maxDurationTicks);
        }

        private String name() {
            return name;
        }

        private StatusEffect effect() {
            return effect;
        }
    }
}
