package universalmod.module.utility;

import universalmod.event.SubscribeEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.entry.RegistryEntry;
import universalmod.event.type.DrawEvent;
import universalmod.render.Render2D;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;

@ModuleInformation(
        moduleName = "Effect Notifier",
        moduleCategory = ModuleCategory.UTILS
)
public final class EffectNotifier extends Module {
    private static final int WARNING_TICKS = 100;
    private static final int ENDED_NOTICE_TICKS = 40;
    private static final float ICON_SIZE = 18.0F;
    private static final float ICON_SPACING = 2.0F;
    private static final float ICON_Y_OFFSET = 12.0F;

    private final Set<RegistryEntry<StatusEffect>> previousBeneficialEffects = new HashSet<>();
    private final List<EndedNotice> endedNotices = new ArrayList<>();
    private final List<WarningNotice> warningNotices = new ArrayList<>();

    @Override
    public void onTick(net.minecraft.client.MinecraftClient tickClient) {
        if (mc.player == null || mc.world == null) {
            reset();
            return;
        }

        Set<RegistryEntry<StatusEffect>> currentBeneficialEffects = new HashSet<>();
        List<WarningNotice> currentWarnings = new ArrayList<>();

        for (StatusEffectInstance instance : mc.player.getStatusEffects()) {
            RegistryEntry<StatusEffect> effect = instance.getEffectType();
            if (effect == null || !effect.value().isBeneficial()) {
                continue;
            }

            currentBeneficialEffects.add(effect);
            int duration = instance.getDuration();
            if (duration >= 0 && duration <= WARNING_TICKS) {
                currentWarnings.add(new WarningNotice(effect, duration));
            }
        }

        for (RegistryEntry<StatusEffect> previousEffect : previousBeneficialEffects) {
            if (!currentBeneficialEffects.contains(previousEffect) && !hasEndedNotice(previousEffect)) {
                endedNotices.add(new EndedNotice(previousEffect, ENDED_NOTICE_TICKS));
            }
        }

        Iterator<EndedNotice> iterator = endedNotices.iterator();
        while (iterator.hasNext()) {
            EndedNotice notice = iterator.next();
            notice.remainingTicks--;
            if (notice.remainingTicks <= 0) {
                iterator.remove();
            }
        }

        previousBeneficialEffects.clear();
        previousBeneficialEffects.addAll(currentBeneficialEffects);
        currentWarnings.sort(Comparator.comparingInt(WarningNotice::remainingTicks));
        warningNotices.clear();
        warningNotices.addAll(currentWarnings);
    }

    @SubscribeEvent
    private void onHud(DrawEvent event) {
        if (event.getLayer() != DrawEvent.Layer.GAME || mc.player == null || mc.options.hudHidden) {
            return;
        }

        List<RegistryEntry<StatusEffect>> iconEffects = new ArrayList<>();
        Set<RegistryEntry<StatusEffect>> usedEffects = new HashSet<>();

        for (EndedNotice notice : endedNotices) {
            if (usedEffects.add(notice.effect)) {
                iconEffects.add(notice.effect);
            }
        }
        for (WarningNotice notice : warningNotices) {
            if (usedEffects.add(notice.effect)) {
                iconEffects.add(notice.effect);
            }
        }

        if (iconEffects.isEmpty()) {
            return;
        }

        float centerX = mc.getWindow().getScaledWidth() / 2.0F;
        float centerY = mc.getWindow().getScaledHeight() / 2.0F;
        float totalWidth = iconEffects.size() * ICON_SIZE + (iconEffects.size() - 1) * ICON_SPACING;
        float startX = centerX - totalWidth / 2.0F;
        float iconY = centerY + ICON_Y_OFFSET;

        for (int index = 0; index < iconEffects.size(); index++) {
            Render2D.effectIcon(
                    iconEffects.get(index),
                    startX + index * (ICON_SIZE + ICON_SPACING),
                    iconY,
                    ICON_SIZE,
                    0xFFFFFFFF
            );
        }
    }

    @Override
    public void onDisable() {
        reset();
        super.onDisable();
    }

    private void reset() {
        previousBeneficialEffects.clear();
        endedNotices.clear();
        warningNotices.clear();
    }

    private boolean hasEndedNotice(RegistryEntry<StatusEffect> effect) {
        for (EndedNotice notice : endedNotices) {
            if (notice.effect.equals(effect)) {
                return true;
            }
        }
        return false;
    }

    private static final class EndedNotice {
        private final RegistryEntry<StatusEffect> effect;
        private int remainingTicks;

        private EndedNotice(RegistryEntry<StatusEffect> effect, int remainingTicks) {
            this.effect = effect;
            this.remainingTicks = remainingTicks;
        }
    }

    private record WarningNotice(RegistryEntry<StatusEffect> effect, int remainingTicks) {
    }
}
