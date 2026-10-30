package universalmod.hud.element;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import universalmod.feature.cooldown.CooldownStateStorage;
import universalmod.feature.cooldown.HolyWorldHealingCooldown;
import universalmod.hud.ElementScreen;
import universalmod.hud.editor.HudPanel;
import universalmod.module.render.Hud;
import universalmod.service.language.LanguageManager;
import universalmod.render.Render2D;
import universalmod.render.color.ColorUtil;
import universalmod.render.arc.UiArc;
import universalmod.render.arc.UiArcRenderer;
import universalmod.render.font.FontType;
import universalmod.render.item.RenderItem;
import universalmod.render.item.RenderItemOptions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Locale;


public final class Cooldowns extends HudPanel {
    private static final float MIN_WIDTH = 80.0F;
    private static final float MAX_WIDTH = 112.0F;
    private static final float HEADER_H = 14.0F;
    private static final float ROW_H = 11.0F;
    private static final float ROW_GAP = 1.0F;
    private static final float TEXT_SIZE = 4.55F;
    private static final float ITEM_SIZE = 6.4F;
    private static final float MODE_TWO_TILE = 31.0F;
    private static final float MODE_TWO_GAP = 4.0F;
    private static final float MODE_TWO_STEP = MODE_TWO_TILE + MODE_TWO_GAP;
    private static final float MODE_TWO_RING = 23.0F;
    private static final float MODE_TWO_RING_THICKNESS = 1.65F;
    private static final float MODE_TWO_ITEM = 15.5F;
    private static final float MODE_TWO_TEXT_SCALE = 1.5F;
    private static final String HEADER_ICON = "universalmod:textures/hud/header_cooldowns_clock.png";
    private static final String HOLYWORLD_HEALING_ROW_KEY = "__holyworld_healing_potion";

    private final Map<CooldownKey, CooldownInfo> infoByItem = new LinkedHashMap<>();
    private final List<CooldownInfo> activeCooldowns = new ArrayList<>();
    private final Set<CooldownKey> seenCooldowns = new HashSet<>();
    private static final ItemStack[] PREVIEW_ITEMS = {
            Items.ENDER_PEARL.getDefaultInstance(),
            Items.GOLDEN_APPLE.getDefaultInstance(),
            Items.TOTEM_OF_UNDYING.getDefaultInstance(),
            Items.FIRE_CHARGE.getDefaultInstance(),
            Items.CHORUS_FRUIT.getDefaultInstance()
    };

    private final CooldownInfo holyWorldHealingInfo = new CooldownInfo();
    private final CooldownInfo previewInfo = new CooldownInfo();
    private final CooldownInfo modeTwoPreviewInfo = new CooldownInfo();
    private final ListAnimator<Object, CooldownInfo> animator = new ListAnimator<>();
    private final ListAnimator<Object, CooldownInfo> modeTwoAnimator = new ListAnimator<>();
    private boolean previewWasActive;
    private boolean modeTwoPreviewWasActive;
    private ItemStack previewStack = Items.ENDER_PEARL.getDefaultInstance();
    private ItemStack modeTwoPreviewStack = Items.ENDER_PEARL.getDefaultInstance();
    private int previewTicks = 18 * 20;
    private int modeTwoPreviewTicks = 18 * 20;
    private boolean lastModeTwo;

    public Cooldowns() {
        super("cooldowns", "Cooldowns", defaultX(), defaultY(), MIN_WIDTH, HEADER_H + ROW_H + 3.0F);
    }

    @Override
    public void render() {
        List<CooldownInfo> active = collectActive();
        boolean modeTwo = Hud.getInstance() != null && Hud.getInstance().cooldownsSecondMode();
        if (modeTwo != lastModeTwo) {
            if (modeTwo) {
                modeTwoAnimator.clear();
                modeTwoPreviewWasActive = false;
            } else {
                animator.clear();
                previewWasActive = false;
            }
            lastModeTwo = modeTwo;
        }
        if (modeTwo) {
            renderModeTwo(active);
            return;
        }

        hitExpansion(0.0F, 0.0F, 0.0F, 0.0F);
        boolean preview = active.isEmpty() && editPreview();
        if (preview) {
            if (!previewWasActive) {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                previewStack = PREVIEW_ITEMS[random.nextInt(PREVIEW_ITEMS.length)].copy();
                previewTicks = random.nextInt(8, 45) * 20;
            }
            previewInfo.setFixed("__preview", previewStack, previewTicks, Math.max(previewTicks, 45 * 20));
            active.add(previewInfo);
        }
        previewWasActive = preview;

        Collection<ListAnimator.Row<Object, CooldownInfo>> animated = animator.sync(active, CooldownInfo::animationKey, ROW_H, ROW_GAP);
        float alpha = contentAlpha(!active.isEmpty() || animator.hasRows());
        if (alpha <= 0.01F || animated.isEmpty()) {
            return;
        }

        float width = MIN_WIDTH;
        for (ListAnimator.Row<Object, CooldownInfo> animatedRow : animated) {
            CooldownInfo info = animatedRow.value();
            String name = info.displayName();
            String timer = info.remainingText();
            width = Math.max(width, 20.0F
                    + Render2D.textWidth(FontType.BOLD, name, TEXT_SIZE)
                    + Render2D.textWidth(FontType.BOLD, timer, TEXT_SIZE)
                    + 12.0F);
        }
        width = Math.min(MAX_WIDTH, width);
        float height = HEADER_H + animator.extent() + 3.0F;
        size(width, height);

        float x = drag.x();
        float y = drag.y();
        HudVisualStyle.panel(x, y, width, height, 4.2F, alpha);
        HudVisualStyle.title(LanguageManager.settingOption("HUD", "Elements", "Cooldowns"), x + 5.0F, y + 4.0F, alpha);
        Render2D.image(HEADER_ICON, x + width - 10.0F, y + 3.6F, 6.7F, 6.7F, 0.0F,
                HudVisualStyle.accent(alpha));

        GuiGraphics graphics = Render2D.currentGraphics();
        RenderItem.beginItemLayer();
        for (ListAnimator.Row<Object, CooldownInfo> animatedRow : animated) {
            if (animatedRow.progress() <= 0.001F) {
                continue;
            }
            CooldownInfo info = animatedRow.value();
            float rowY = y + HEADER_H + animatedRow.offset();
            float rowAlpha = alpha * animatedRow.progress();
            float finalWidth = width;
            drawAnimatedRow(graphics, x, rowY, finalWidth, ROW_H, animatedRow.scale(), () ->
                    drawCooldownRow(info, x, rowY, finalWidth, rowAlpha));
        }
    }

    private void renderModeTwo(List<CooldownInfo> active) {
        boolean preview = active.isEmpty() && editPreview();
        if (preview) {
            if (!modeTwoPreviewWasActive) {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                modeTwoPreviewStack = PREVIEW_ITEMS[random.nextInt(PREVIEW_ITEMS.length)].copy();
                modeTwoPreviewTicks = random.nextInt(8, 45) * 20;
            }
            modeTwoPreviewInfo.setFixed("__preview_mode2", modeTwoPreviewStack, modeTwoPreviewTicks,
                    Math.max(modeTwoPreviewTicks, 45 * 20));
            active.add(modeTwoPreviewInfo);
        }
        modeTwoPreviewWasActive = preview;

        Collection<ListAnimator.Row<Object, CooldownInfo>> animated = modeTwoAnimator.sync(
                active, CooldownInfo::animationKey, 0.0F, 0.0F);
        float alpha = contentAlpha(!active.isEmpty() || modeTwoAnimator.hasRows());
        if (alpha <= 0.01F || animated.isEmpty()) {
            return;
        }

        int count = animated.size();
        int maxDistance = count / 2;
        float sideExpansion = maxDistance * MODE_TWO_STEP;

        sizeImmediate(MODE_TWO_TILE, MODE_TWO_TILE);
        hitExpansion(sideExpansion, 0.0F, sideExpansion, 0.0F);

        float centerX = drag.x();
        float y = drag.y();
        GuiGraphics graphics = Render2D.currentGraphics();
        UiArcRenderer arcs = UiArcRenderer.getInstance();
        arcs.beginFrame(graphics);

        int index = 0;
        for (ListAnimator.Row<Object, CooldownInfo> animatedRow : animated) {
            if (animatedRow.progress() <= 0.001F) {
                index++;
                continue;
            }
            CooldownInfo info = animatedRow.value();
            int slot = alternatingSlot(index++);
            float tileX = centerX + slot * MODE_TWO_STEP;
            float rowAlpha = alpha * animatedRow.progress();
            drawModeTwoTileBackground(graphics, arcs, info, tileX, y, rowAlpha, animatedRow.scale());
        }
        arcs.flush();

        RenderItem.beginItemLayer();
        index = 0;
        for (ListAnimator.Row<Object, CooldownInfo> animatedRow : animated) {
            if (animatedRow.progress() <= 0.001F) {
                index++;
                continue;
            }
            CooldownInfo info = animatedRow.value();
            int slot = alternatingSlot(index++);
            float tileX = centerX + slot * MODE_TWO_STEP;
            float rowAlpha = alpha * animatedRow.progress();
            drawModeTwoTileContent(graphics, info, tileX, y, rowAlpha, animatedRow.scale());
        }
    }

    private void drawModeTwoTileBackground(
            GuiGraphics graphics, UiArcRenderer arcs, CooldownInfo info,
            float x, float y, float alpha, float scale
    ) {
        Runnable draw = () -> {
            HudVisualStyle.panel(x, y, MODE_TWO_TILE, MODE_TWO_TILE, 5.3F, alpha);

            float ringX = x + (MODE_TWO_TILE - MODE_TWO_RING) * 0.5F;
            float ringY = y + (MODE_TWO_TILE - MODE_TWO_RING) * 0.5F;
            int track = ColorUtil.rgba(255, 255, 255, Math.round(34.0F * HudVisualStyle.clamp01(alpha)));
            arcs.enqueue(new UiArc(ringX, ringY, MODE_TWO_RING, MODE_TWO_RING_THICKNESS,
                    360.0F, -90.0F, 0.0F, track));
            float progress = info.remainingProgress();
            if (progress > 0.001F) {
                float degree = 360.0F * progress;
                arcs.enqueue(new UiArc(ringX, ringY, MODE_TWO_RING, MODE_TWO_RING_THICKNESS,
                        degree, -90.0F + degree * 0.5F, 0.0F, HudVisualStyle.accent(alpha)));
            }
        };
        drawAnimatedRow(graphics, x, y, MODE_TWO_TILE, MODE_TWO_TILE, scale, draw);
    }

    private void drawModeTwoTileContent(
            GuiGraphics graphics, CooldownInfo info,
            float x, float y, float alpha, float scale
    ) {
        Runnable draw = () -> {
            if (info.stack != null && !info.stack.isEmpty()) {
                float itemX = x + (MODE_TWO_TILE - MODE_TWO_ITEM) * 0.5F;
                float itemY = y + (MODE_TWO_TILE - MODE_TWO_ITEM) * 0.5F;
                RenderItem.item(info.stack, itemX, itemY, MODE_TWO_ITEM, RenderItemOptions.noDecorations(alpha));
            }

            String time = info.compactRemainingText();
            float textSize = (time.length() >= 4 ? 4.35F : 4.8F) * MODE_TWO_TEXT_SCALE;
            float tw = Render2D.textWidth(FontType.BOLD, time, textSize);
            float th = Render2D.textHeight(FontType.BOLD, time, textSize);
            float tx = x + (MODE_TWO_TILE - tw) * 0.5F;
            float ty = y + (MODE_TWO_TILE - th) * 0.5F;
            float shadow = 0.45F;
            Render2D.text(FontType.BOLD, time, tx + shadow, ty + shadow, textSize,
                    ColorUtil.rgba(0, 0, 0, Math.round(220.0F * HudVisualStyle.clamp01(alpha))));
            Render2D.text(FontType.BOLD, time, tx, ty, textSize, HudVisualStyle.alpha(HudVisualStyle.TEXT, alpha));
        };
        drawAnimatedRow(graphics, x, y, MODE_TWO_TILE, MODE_TWO_TILE, scale, draw);
    }

    private static int alternatingSlot(int index) {
        if (index <= 0) {
            return 0;
        }
        int distance = (index + 1) / 2;
        return (index & 1) == 1 ? -distance : distance;
    }

    private void drawCooldownRow(CooldownInfo info, float x, float rowY, float width, float alpha) {
        String timer = info.remainingText();
        float textH = Math.max(
                Render2D.textHeight(FontType.BOLD, info.displayName(), TEXT_SIZE),
                Render2D.textHeight(FontType.BOLD, timer, TEXT_SIZE)
        );
        float contentY = rowY + (ROW_H - textH) * 0.5F;
        float itemY = rowY + (ROW_H - ITEM_SIZE) * 0.5F;
        if (info.stack != null && !info.stack.isEmpty()) {
            RenderItem.item(info.stack, x + 5.0F, itemY, ITEM_SIZE,
                    RenderItemOptions.noDecorations(alpha));
        }

        float timerW = Render2D.textWidth(FontType.BOLD, timer, TEXT_SIZE);
        float timerX = x + width - 5.0F - timerW;
        String name = trimToWidth(info.displayName(), FontType.BOLD, TEXT_SIZE,
                Math.max(12.0F, timerX - (x + 15.0F) - 4.0F));
        HudVisualStyle.text(name, x + 15.0F, contentY, TEXT_SIZE, alpha);
        Render2D.text(FontType.BOLD, timer, timerX, contentY, TEXT_SIZE,
                HudVisualStyle.alpha(HudVisualStyle.TEXT, alpha));
    }

    private static void drawAnimatedRow(GuiGraphics graphics, float x, float y, float width, float height, float scale, Runnable draw) {
        if (graphics == null || scale >= 0.999F) {
            draw.run();
            return;
        }
        float cx = x + width * 0.5F;
        float cy = y + height * 0.5F;
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(cx, cy);
            graphics.pose().scale(scale);
            graphics.pose().translate(-cx, -cy);
            draw.run();
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private List<CooldownInfo> collectActive() {
        activeCooldowns.clear();
        if (mc.player == null) {
            infoByItem.clear();
            return activeCooldowns;
        }

        HolyWorldHealingCooldown.Snapshot healing = HolyWorldHealingCooldown.snapshot();
        seenCooldowns.clear();
        for (int slot = 0; slot < mc.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack == null || stack.isEmpty() || !mc.player.getCooldowns().isOnCooldown(stack)) {
                continue;
            }
            if (healing != null && HolyWorldHealingCooldown.isHealingPotion(stack)) {
                continue;
            }

            CooldownKey key = CooldownKey.of(stack);
            if (!seenCooldowns.add(key)) {
                continue;
            }

            Identifier group = mc.player.getCooldowns().getCooldownGroup(stack);
            int totalTicks = CooldownStateStorage.getDuration(group);
            float progress = clamp(mc.player.getCooldowns().getCooldownPercent(stack, 0.0F), 0.0F, 1.0F);
            CooldownInfo info = infoByItem.computeIfAbsent(key, ignored -> new CooldownInfo());
            info.key = key;
            info.stack = stack;
            info.update(progress, mc.player.tickCount, totalTicks);
            activeCooldowns.add(info);
        }

        Iterator<Map.Entry<CooldownKey, CooldownInfo>> infoIterator = infoByItem.entrySet().iterator();
        while (infoIterator.hasNext()) {
            if (!seenCooldowns.contains(infoIterator.next().getKey())) {
                infoIterator.remove();
            }
        }

        if (healing != null) {
            holyWorldHealingInfo.setFixed(
                    HOLYWORLD_HEALING_ROW_KEY,
                    healing.stack(),
                    healing.remainingTicks(),
                    HolyWorldHealingCooldown.DURATION_SECONDS * 20
            );
            activeCooldowns.add(holyWorldHealingInfo);
        }
        return activeCooldowns;
    }

    private static String formatDurationTicks(int ticks) {
        if (ticks < 0) {
            return "0:00";
        }
        int totalSeconds = Math.max(0, (int) Math.ceil(ticks / 20.0D));
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return minutes + ":" + twoDigits(seconds);
    }

    private static float defaultX() {
        ElementScreen screen = ElementScreen.current();
        return Math.max(4.0F, screen.width() * 0.5F - MIN_WIDTH * 0.5F);
    }

    private static float defaultY() {
        ElementScreen screen = ElementScreen.current();
        return Math.max(4.0F, screen.height() - 94.0F);
    }

    private final class CooldownInfo {
        private CooldownKey key;
        private String customRowKey;
        private ItemStack stack = ItemStack.EMPTY;
        private float lastProgress = -1.0F;
        private long lastTick = -1L;
        private int totalTicks = -1;
        private int remainingTicks;

        private void update(float nextProgress, long tick, int knownTotalTicks) {
            customRowKey = null;
            float progress = clamp(nextProgress, 0.0F, 1.0F);

            if (knownTotalTicks > 0) {
                totalTicks = knownTotalTicks;
            } else if (lastTick >= 0L && tick > lastTick && lastProgress > progress) {
                float diff = lastProgress - progress;
                if (diff > 0.00001F) {
                    int estimate = Math.round((tick - lastTick) / diff);
                    if (estimate > 0 && estimate < 12000) {
                        totalTicks = totalTicks <= 0
                                ? estimate
                                : Math.round(totalTicks * 0.75F + estimate * 0.25F);
                    }
                }
            }

            lastProgress = progress;
            lastTick = tick;
            remainingTicks = totalTicks <= 0
                    ? Math.max(1, Math.round(progress * 20.0F))
                    : Math.max(1, Math.round(progress * totalTicks));
        }

        private void setFixed(String rowKey, ItemStack stack, int remainingTicks, int totalTicks) {
            this.key = null;
            this.customRowKey = rowKey;
            this.stack = stack == null ? ItemStack.EMPTY : stack;
            this.remainingTicks = Math.max(1, remainingTicks);
            this.totalTicks = Math.max(this.remainingTicks, totalTicks);
            this.lastProgress = this.totalTicks <= 0 ? 0.0F : clamp(this.remainingTicks / (float) this.totalTicks, 0.0F, 1.0F);
            this.lastTick = -1L;
        }

        private float remainingProgress() {
            if (totalTicks > 0) {
                return clamp(remainingTicks / (float) totalTicks, 0.0F, 1.0F);
            }
            return lastProgress < 0.0F ? 0.0F : clamp(lastProgress, 0.0F, 1.0F);
        }

        private String compactRemainingText() {
            float seconds = Math.max(0.0F, remainingTicks / 20.0F);
            if (seconds >= 10.0F) {
                return Integer.toString((int) Math.ceil(seconds));
            }
            return String.format(Locale.ROOT, "%.1f", seconds);
        }

        private String displayName() {
            if (stack == null || stack.isEmpty()) {
                return customRowKey == null ? "Cooldown" : "Healing";
            }
            return stack.getHoverName().getString();
        }

        private String remainingText() {
            return formatDurationTicks(remainingTicks);
        }

        private Object animationKey() {
            return key != null ? key : customRowKey;
        }
    }

    private record CooldownKey(Item item, DataComponentMap components, String name) {
        private static CooldownKey of(ItemStack stack) {
            return new CooldownKey(stack.getItem(), stack.immutableComponents(), stack.getHoverName().getString());
        }

        @SuppressWarnings("unused")
        private String rowKey() {
            return BuiltInRegistries.ITEM.getKey(item) + "|" + name + "|" + components.hashCode();
        }
    }
}
